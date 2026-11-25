package me.nimd.etbridge.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.nimd.etbridge.BuildConfig
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    /** Not checked yet, or self-update is disabled in this build. */
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val manifest: UpdateManifest) : UpdateState
    /** "Install unknown apps" is off for ETBridge; the user must allow it in system settings. */
    data class NeedsPermission(val manifest: UpdateManifest) : UpdateState
    data class Downloading(val manifest: UpdateManifest, val bytes: Long, val total: Long) : UpdateState {
        val progress: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
    }
    data class Installing(val manifest: UpdateManifest) : UpdateState
    data class Failed(val manifest: UpdateManifest?, val message: String) : UpdateState
}

val UpdateState.manifest: UpdateManifest?
    get() = when (this) {
        is UpdateState.Available -> manifest
        is UpdateState.NeedsPermission -> manifest
        is UpdateState.Downloading -> manifest
        is UpdateState.Installing -> manifest
        is UpdateState.Failed -> manifest
        else -> null
    }

/**
 * Self-update for the GitHub (sideloaded) build: checks android-update.json in the background,
 * downloads the APK from the GitHub release, verifies its SHA-256, package and signing key, then
 * hands it to [PackageInstaller]. Android always enforces that the new APK is signed with the same
 * key, so a tampered manifest can at worst offer an APK that fails to install.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttp: OkHttpClient,
    private val prefs: UserPreferencesRepository,
) {
    companion object {
        const val MANIFEST_URL = "https://etbridge.nimd.me/android-update.json"
        const val RELEASES_URL = "https://github.com/ET-Bridge/android/releases/latest"
        private const val RELEASE_PACKAGE = "me.nimd.etbridge"
        private const val CHECK_INTERVAL_MS = 12 * 60 * 60 * 1000L
        private const val SNOOZE_MS = 24 * 60 * 60 * 1000L
        const val ACTION_INSTALL_STATUS = "me.nimd.etbridge.update.INSTALL_STATUS"
    }

    /**
     * Only release builds signed with the release key, under the real package name (benchmark
     * variants use ".bench"), can update themselves; others would fail the signature check anyway.
     */
    val enabled: Boolean = BuildConfig.SELF_UPDATE && context.packageName == RELEASE_PACKAGE

    /** No HTTP cache: the manifest must be fresh and a 5 MB APK shouldn't evict cached ETLab data. */
    private val http: OkHttpClient = okHttp.newBuilder().cache(null).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkMutex = Mutex()
    private var downloadJob: Job? = null
    private val currentVersion = BuildConfig.VERSION_CODE

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Whether the update dialog should be on screen. */
    private val _prompt = MutableStateFlow(false)
    val prompt: StateFlow<Boolean> = _prompt.asStateFlow()

    fun isMandatory(m: UpdateManifest): Boolean = currentVersion < m.minSupportedVersionCode

    private val busy: Boolean
        get() = _state.value.let { it is UpdateState.Downloading || it is UpdateState.Installing }

    /** Start-up / foreground check: instant from the cached manifest, network at most every 12h. Never throws. */
    suspend fun checkIfDue() {
        if (!enabled) return
        withContext(Dispatchers.IO) { checkIfDueLocked() }
    }

    private suspend fun checkIfDueLocked() {
        checkMutex.withLock {
            val saved = prefs.updatePrefs()
            evaluate(UpdateManifest.parse(saved.manifestJson), fromUser = false)
            val now = System.currentTimeMillis()
            val checkedAt = saved.checkedAt
            if (checkedAt != null && now - checkedAt in 0 until CHECK_INTERVAL_MS) return
            fetch()?.let { evaluate(it, fromUser = false) }
        }
    }

    /** "Check for updates" in Settings: always hits the network and shows the dialog if there is one. */
    fun checkNow() {
        if (!enabled || busy) return
        scope.launch {
            checkMutex.withLock {
                _state.value = UpdateState.Checking
                val m = fetch()
                if (m == null) {
                    _state.value = UpdateState.Failed(null, "Couldn't check for updates. Check your connection.")
                    return@withLock
                }
                evaluate(m, fromUser = true)
            }
        }
    }

    private suspend fun fetch(): UpdateManifest? = try {
        val req = Request.Builder().url(MANIFEST_URL).cacheControl(CacheControl.FORCE_NETWORK).build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null
            val body = res.body.string()
            UpdateManifest.parse(body)?.also { prefs.setUpdateManifest(body, System.currentTimeMillis()) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null // Offline or the site is down: try again next time.
    }

    private suspend fun evaluate(m: UpdateManifest?, fromUser: Boolean) {
        if (busy) return
        if (m == null || m.versionCode <= currentVersion) {
            if (fromUser || m != null) _state.value = UpdateState.UpToDate
            _prompt.value = false
            updatesDir().deleteRecursively() // Leftover APK from an update that's now installed.
            return
        }
        // Keep a pending "allow installs" / error state for this same version instead of resetting it.
        val current = _state.value
        val keep = (current is UpdateState.NeedsPermission || current is UpdateState.Failed) && current.manifest == m
        if (!keep) _state.value = UpdateState.Available(m)
        val saved = prefs.updatePrefs()
        val dismissed = saved.skippedVersionCode == m.versionCode ||
            System.currentTimeMillis() < (saved.snoozedUntil ?: 0L)
        if (fromUser || isMandatory(m) || !dismissed) _prompt.value = true
    }

    /** "Later": hide for a day. Not allowed for mandatory updates. */
    fun later() {
        val m = _state.value.manifest
        if (m != null && isMandatory(m)) return
        cancelDownload()
        _prompt.value = false
        scope.launch { prefs.snoozeUpdate(System.currentTimeMillis() + SNOOZE_MS) }
    }

    /** "Skip this version": don't prompt again until a newer one is published. */
    fun skip() {
        val m = _state.value.manifest ?: return
        if (isMandatory(m)) return
        cancelDownload()
        _prompt.value = false
        scope.launch { prefs.skipUpdate(m.versionCode) }
    }

    fun install() {
        val m = _state.value.manifest ?: return
        if (busy) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(m)
            return
        }
        downloadJob = scope.launch {
            try {
                val apk = download(m)
                verify(apk, m)
                _state.value = UpdateState.Installing(m)
                commit(apk)
            } catch (e: CancellationException) {
                _state.value = UpdateState.Available(m)
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(m, e.message ?: "Update failed")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    /** Intent for the per-app "Install unknown apps" switch. */
    fun unknownSourcesSettingsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Called from MainActivity.onResume: continue once the user has allowed installs. */
    fun onResume() {
        if (_state.value is UpdateState.NeedsPermission && context.packageManager.canRequestPackageInstalls()) install()
    }

    /** Result of the PackageInstaller session (from [UpdateInstallReceiver]). */
    internal fun onInstallFailed(message: String?, cancelled: Boolean) {
        val m = _state.value.manifest
        _state.value = if (cancelled) {
            if (m != null) UpdateState.Available(m) else UpdateState.Idle
        } else {
            UpdateState.Failed(m, message?.takeIf { it.isNotBlank() }?.let { "Install failed: $it" } ?: "Install failed")
        }
    }

    // --- download & verification -------------------------------------------------------------

    private fun updatesDir() = File(context.cacheDir, "updates")

    private suspend fun download(m: UpdateManifest): File {
        val dir = updatesDir().apply { mkdirs() }
        val target = File(dir, "ETBridge-${m.versionCode}.apk")
        dir.listFiles()?.filter { it.name != target.name }?.forEach { it.delete() }
        if (target.exists() && sha256(target) == m.sha256) return target // Downloaded earlier.
        target.delete()

        val part = File(dir, "${target.name}.part")
        _state.value = UpdateState.Downloading(m, 0, m.sizeBytes)
        val req = Request.Builder().url(m.apkUrl).cacheControl(CacheControl.FORCE_NETWORK).build()
        val digest = MessageDigest.getInstance("SHA-256")
        http.newCall(req).execute().use { res ->
            if (!UpdateManifest.isAllowedDownloadUrl(res.request.url.toString(), initial = false)) {
                throw IOException("Download was redirected to an unexpected host")
            }
            if (!res.isSuccessful) throw IOException("Download failed (HTTP ${res.code})")
            val total = res.body.contentLength().takeIf { it > 0 } ?: m.sizeBytes
            res.body.byteStream().use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        done += n
                        if (done - lastReport >= 128 * 1024 || done == total) {
                            lastReport = done
                            _state.value = UpdateState.Downloading(m, done, total)
                        }
                    }
                }
            }
        }
        val actual = digest.digest().toHex()
        if (actual != m.sha256) {
            part.delete()
            throw IOException("Downloaded file is corrupted (checksum mismatch). Try again.")
        }
        if (!part.renameTo(target)) throw IOException("Couldn't save the update")
        return target
    }

    /** The APK must be this app, the advertised version, and signed with the same key as this install. */
    private fun verify(apk: File, m: UpdateManifest) {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
            ?: throw IOException("Downloaded file isn't a valid app package")
        if (archive.packageName != context.packageName) throw IOException("Downloaded app has the wrong package name")
        if (PackageInfoCompat.getLongVersionCode(archive) != m.versionCode.toLong()) {
            throw IOException("Downloaded app is not version ${m.versionName}")
        }
        val installed = signerDigests(pm.getPackageInfo(context.packageName, flags))
        val incoming = signerDigests(archive)
        // Older Android versions may not expose an archive's signatures; the installer still checks.
        if (installed.isNotEmpty() && incoming.isNotEmpty() && installed.intersect(incoming).isEmpty()) {
            throw IOException(
                "This update is signed with a different key than the installed app. " +
                    "Download it from GitHub and reinstall instead.",
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val s = info.signingInfo ?: return emptySet()
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else {
            info.signatures
        }
        return sigs.orEmpty().filterNotNull().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).toHex() }.toSet()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    // --- install -------------------------------------------------------------------------------

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Once ETBridge installed itself, Android 12+ can update without another confirmation.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java).setAction(ACTION_INSTALL_STATUS)
                // Mutable: the installer adds the status extras to this explicit intent.
                val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pi = PendingIntent.getBroadcast(context, sessionId, intent, piFlags)
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }
}
