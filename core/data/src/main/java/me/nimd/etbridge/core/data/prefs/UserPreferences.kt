package me.nimd.etbridge.core.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prefsDataStore by preferencesDataStore(name = "etbridge_prefs")

enum class ThemeMode { System, Light, Dark }

/** Colour palette: ETBridge blue, the cheerful Vibrant set, or Material You (wallpaper, Android 12+). */
enum class ColorPalette { Classic, Vibrant, Dynamic }

data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.System,
    val palette: ColorPalette = ColorPalette.Dynamic,
    val biometricLock: Boolean = false,
    /** Re-lock after the app has been in the background this many minutes (0 = immediately). */
    val lockTimeoutMinutes: Int = 1,
    val backgroundSync: Boolean = true,
    val notifyLowAttendance: Boolean = true,
    val notifyNewContent: Boolean = true,
    val notifyNextClass: Boolean = false,
    /** Attendance target used for warnings (defaults to the KTU 75%). */
    val attendanceTarget: Int = 75,
    val lastSyncMillis: Long? = null,
    val lastSelectedInstituteId: String? = null,
    /** Ordered ids of the Home cards the user turned on; null = app default. */
    val homeSections: List<String>? = null,
) {
    val dynamicColor: Boolean get() = palette == ColorPalette.Dynamic
    val vibrant: Boolean get() = palette == ColorPalette.Vibrant
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val palette = stringPreferencesKey("color_palette")
        val homeSections = stringPreferencesKey("home_sections")
        val biometric = booleanPreferencesKey("biometric_lock")
        val lockTimeout = intPreferencesKey("lock_timeout_minutes")
        val bgSync = booleanPreferencesKey("background_sync")
        val notifyLow = booleanPreferencesKey("notify_low_attendance")
        val notifyNew = booleanPreferencesKey("notify_new_content")
        val notifyNext = booleanPreferencesKey("notify_next_class")
        val target = intPreferencesKey("attendance_target")
        val lastSync = longPreferencesKey("last_sync_millis")
        val lastInstitute = stringPreferencesKey("last_institute")
        val remoteApiOrigin = stringPreferencesKey("remote_api_origin")
        val remoteConfigChecked = longPreferencesKey("remote_config_checked_millis")
        val updateManifest = stringPreferencesKey("update_manifest_json")
        val updateChecked = longPreferencesKey("update_checked_millis")
        val updateSkipped = intPreferencesKey("update_skipped_version")
        val updateSnoozedUntil = longPreferencesKey("update_snoozed_until_millis")
    }

    /** Self-update state: last fetched android-update.json, when, and what the user dismissed. */
    data class UpdatePrefs(
        val manifestJson: String?,
        val checkedAt: Long?,
        val skippedVersionCode: Int?,
        val snoozedUntil: Long?,
    )

    suspend fun updatePrefs(): UpdatePrefs = context.prefsDataStore.data.first().let {
        UpdatePrefs(it[Keys.updateManifest], it[Keys.updateChecked], it[Keys.updateSkipped], it[Keys.updateSnoozedUntil])
    }

    suspend fun setUpdateManifest(json: String, checkedAt: Long) {
        context.prefsDataStore.edit {
            it[Keys.updateManifest] = json
            it[Keys.updateChecked] = checkedAt
        }
    }

    suspend fun skipUpdate(versionCode: Int) = set(Keys.updateSkipped, versionCode)
    suspend fun snoozeUpdate(untilMillis: Long) = set(Keys.updateSnoozedUntil, untilMillis)

    /** ETLab API origin from ETBridge's remote endpoint config, and when it was last checked. */
    suspend fun remoteApiOrigin(): Pair<String?, Long?> =
        context.prefsDataStore.data.first().let { it[Keys.remoteApiOrigin] to it[Keys.remoteConfigChecked] }

    suspend fun setRemoteApiOrigin(origin: String?, checkedAt: Long) {
        context.prefsDataStore.edit {
            if (origin == null) it.remove(Keys.remoteApiOrigin) else it[Keys.remoteApiOrigin] = origin
            it[Keys.remoteConfigChecked] = checkedAt
        }
    }

    val preferences: Flow<AppPreferences> = context.prefsDataStore.data
        .map { it.toAppPreferences() }
        .distinctUntilChanged()

    suspend fun snapshot(): AppPreferences = preferences.first()

    private fun Preferences.toAppPreferences() = AppPreferences(
        themeMode = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
        palette = this[Keys.palette]?.let { runCatching { ColorPalette.valueOf(it) }.getOrNull() }
            // Before 1.3.0 there was only the Material You switch.
            ?: if (this[Keys.dynamic] == false) ColorPalette.Classic else ColorPalette.Dynamic,
        homeSections = this[Keys.homeSections]?.split(',')?.map(String::trim)?.filter(String::isNotEmpty),
        biometricLock = this[Keys.biometric] ?: false,
        lockTimeoutMinutes = this[Keys.lockTimeout] ?: 1,
        backgroundSync = this[Keys.bgSync] ?: true,
        notifyLowAttendance = this[Keys.notifyLow] ?: true,
        notifyNewContent = this[Keys.notifyNew] ?: true,
        notifyNextClass = this[Keys.notifyNext] ?: false,
        attendanceTarget = this[Keys.target] ?: 75,
        lastSyncMillis = this[Keys.lastSync],
        lastSelectedInstituteId = this[Keys.lastInstitute],
    )

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        context.prefsDataStore.edit { it[key] = value }
    }

    suspend fun setThemeMode(mode: ThemeMode) = set(Keys.theme, mode.name)
    suspend fun setPalette(palette: ColorPalette) = set(Keys.palette, palette.name)
    suspend fun setHomeSections(ids: List<String>) = set(Keys.homeSections, ids.joinToString(","))
    suspend fun resetHomeSections() {
        context.prefsDataStore.edit { it.remove(Keys.homeSections) }
    }
    suspend fun setBiometricLock(enabled: Boolean) = set(Keys.biometric, enabled)
    suspend fun setLockTimeout(minutes: Int) = set(Keys.lockTimeout, minutes)
    suspend fun setBackgroundSync(enabled: Boolean) = set(Keys.bgSync, enabled)
    suspend fun setNotifyLowAttendance(enabled: Boolean) = set(Keys.notifyLow, enabled)
    suspend fun setNotifyNewContent(enabled: Boolean) = set(Keys.notifyNew, enabled)
    suspend fun setNotifyNextClass(enabled: Boolean) = set(Keys.notifyNext, enabled)
    suspend fun setAttendanceTarget(target: Int) = set(Keys.target, target.coerceIn(50, 100))
    suspend fun setLastSync(millis: Long) = set(Keys.lastSync, millis)
    suspend fun setLastInstitute(id: String) = set(Keys.lastInstitute, id)

    /** Logout: drop sync metadata but keep device-level choices (theme, lock, notifications). */
    suspend fun clearAccountState() {
        context.prefsDataStore.edit { it.remove(Keys.lastSync) }
    }
}
