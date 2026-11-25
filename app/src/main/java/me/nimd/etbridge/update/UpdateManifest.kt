package me.nimd.etbridge.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * https://etbridge.nimd.me/android-update.json (webapp/public/android-update.json), written by
 * scripts/release.sh after the APK is uploaded to the GitHub release.
 */
@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    /** Installed versions below this can no longer work (e.g. ETLab changed its API): update is mandatory. */
    val minSupportedVersionCode: Int = 0,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long = 0,
    val notes: List<String> = emptyList(),
    val releaseUrl: String? = null,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val SHA256 = Regex("^[0-9a-f]{64}$")

        /** Parses and validates; null if anything is off, so a bad file can never point the app elsewhere. */
        fun parse(raw: String?): UpdateManifest? {
            val m = runCatching { json.decodeFromString(serializer(), raw ?: return null) }.getOrNull() ?: return null
            if (m.versionCode <= 0 || m.versionName.isBlank()) return null
            if (!isAllowedDownloadUrl(m.apkUrl, initial = true)) return null
            val sha = m.sha256.trim().lowercase()
            if (!SHA256.matches(sha)) return null
            return m.copy(
                sha256 = sha,
                notes = m.notes.map(String::trim).filter(String::isNotEmpty).take(20),
                releaseUrl = m.releaseUrl?.takeIf { isAllowedDownloadUrl(it, initial = true) },
            )
        }

        /**
         * APKs come only from GitHub releases: the manifest URL must be on github.com, and GitHub's
         * redirect may only land on its own asset CDN (*.githubusercontent.com). Android's signature
         * check and the SHA-256 are the real guarantees; this just keeps downloads on known hosts.
         */
        fun isAllowedDownloadUrl(url: String, initial: Boolean): Boolean {
            val u = url.toHttpUrlOrNull() ?: return false
            if (u.scheme != "https") return false
            return u.host == "github.com" || (!initial && u.host.endsWith(".githubusercontent.com"))
        }
    }
}
