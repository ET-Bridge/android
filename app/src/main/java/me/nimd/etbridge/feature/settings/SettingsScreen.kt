package me.nimd.etbridge.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.nimd.etbridge.BuildConfig
import me.nimd.etbridge.core.data.prefs.AppPreferences
import me.nimd.etbridge.core.data.prefs.ThemeMode
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.SectionHeader
import me.nimd.etbridge.lock.AppLock
import me.nimd.etbridge.sync.Notifications
import me.nimd.etbridge.sync.SyncScheduler
import me.nimd.etbridge.ui.EtScreen
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LocalSnackbar
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: UserPreferencesRepository,
    private val scheduler: SyncScheduler,
    sessionStore: SessionStore,
) : ViewModel() {
    val state = prefs.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())
    val session = sessionStore.session

    private fun set(block: suspend UserPreferencesRepository.() -> Unit) {
        viewModelScope.launch { prefs.block() }
    }

    fun theme(m: ThemeMode) = set { setThemeMode(m) }
    fun dynamic(v: Boolean) = set { setDynamicColor(v) }
    fun biometric(v: Boolean) = set { setBiometricLock(v) }
    fun lockTimeout(v: Int) = set { setLockTimeout(v) }
    fun backgroundSync(v: Boolean) = set { setBackgroundSync(v) }
    fun notifyLow(v: Boolean) = set { setNotifyLowAttendance(v) }
    fun notifyNew(v: Boolean) = set { setNotifyNewContent(v) }
    fun notifyNext(v: Boolean) = set { setNotifyNextClass(v) }
    fun target(v: Int) = set { setAttendanceTarget(v) }
    fun syncNow() = scheduler.syncNow()
}

private val LOCK_TIMEOUTS = listOf(0 to "Immediately", 1 to "1 min", 5 to "5 min", 15 to "15 min")

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val p by vm.state.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val activity = context as FragmentActivity
    var confirmLogout by remember { mutableStateOf(false) }
    var pendingNotify by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingNotify?.invoke(granted)
        pendingNotify = null
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    /** Turning a notification on asks for POST_NOTIFICATIONS first on Android 13+. */
    fun withNotificationPermission(enable: Boolean, apply: (Boolean) -> Unit) {
        if (!enable || Notifications.canPost(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            apply(enable); return
        }
        pendingNotify = { granted ->
            apply(granted)
            if (!granted) scope.launch { snackbar.showSnackbar("Notifications are blocked for ETBridge in system settings") }
        }
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    EtScreen("Settings", onBack = app.back, refreshable = false, subtitle = session?.let { "${it.studentName ?: it.username} · ${it.instituteName}" }) {
        item("appearance-h") { SectionHeader("Appearance") }
        item("appearance") {
            EtCard {
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(p.themeMode == m, { vm.theme(m) }, SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size)) { Text(m.name) }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow(Icons.Rounded.Palette, "Material You colours", "Match your wallpaper instead of the ETBridge blue", p.dynamicColor, onChange = vm::dynamic)
                }
            }
        }

        item("attendance-h") { SectionHeader("Attendance") }
        item("target") {
            var local by remember(p.attendanceTarget) { mutableFloatStateOf(p.attendanceTarget.toFloat()) }
            EtCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TrackChanges, null); Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Attendance target", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (local.roundToInt() == 75) "75% — KTU minimum (ETLab's per-subject target is used)"
                            else "${local.roundToInt()}% — overrides ETLab's per-subject target",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Slider(local, { local = it }, valueRange = 60f..95f, steps = 6, onValueChangeFinished = { vm.target(local.roundToInt()) })
            }
        }

        item("security-h") { SectionHeader("Security") }
        item("security") {
            EtCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                val available = remember { AppLock.isAvailable(activity) }
                SwitchRow(
                    Icons.Rounded.Fingerprint, "App lock",
                    if (available) "Require fingerprint, face or screen lock to open" else "Set up a screen lock on this device first",
                    p.biometricLock, enabled = available,
                ) { enable ->
                    // Confirm identity before changing the lock either way.
                    AppLock.prompt(activity, if (enable) "Turn on app lock" else "Turn off app lock", onSuccess = { vm.biometric(enable) }, onError = {})
                }
                if (p.biometricLock) {
                    Text("Lock again after", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp, 8.dp)) {
                        LOCK_TIMEOUTS.forEachIndexed { i, (m, label) ->
                            SegmentedButton(p.lockTimeoutMinutes == m, { vm.lockTimeout(m) }, SegmentedButtonDefaults.itemShape(i, LOCK_TIMEOUTS.size)) { Text(label) }
                        }
                    }
                }
            }
        }

        item("sync-h") { SectionHeader("Sync & notifications") }
        item("sync") {
            EtCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                SwitchRow(Icons.Rounded.Sync, "Background sync", "Refresh every 4 hours, even when closed", p.backgroundSync, onChange = vm::backgroundSync)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SwitchRow(Icons.Rounded.WarningAmber, "Attendance alerts", "When a subject drops below your target", p.notifyLowAttendance, enabled = p.backgroundSync) {
                    withNotificationPermission(it, vm::notifyLow)
                }
                SwitchRow(Icons.Rounded.Campaign, "New notices & marks", "Notices, circulars and published marks", p.notifyNewContent, enabled = p.backgroundSync) {
                    withNotificationPermission(it, vm::notifyNew)
                }
                SwitchRow(Icons.Rounded.Alarm, "Class reminders", "10 minutes before each class", p.notifyNextClass) {
                    withNotificationPermission(it, vm::notifyNext)
                }
                TextButton(onClick = { vm.syncNow(); app.refresh() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Sync now") }
            }
        }

        item("account-h") { SectionHeader("Account") }
        item("account") {
            EtCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                ListItem(
                    headlineContent = { Text("Sign out") },
                    supportingContent = { Text("Removes your session, cached data and logged duty leaves from this device") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = MaterialTheme.colorScheme.error) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(vertical = 4.dp).clickable { confirmLogout = true },
                )
            }
        }
        item("version") {
            Text(
                "ETBridge ${BuildConfig.VERSION_NAME} · not affiliated with ETLab",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Sign out?") },
            text = { Text("Your cached data and self-reported duty leaves will be removed from this device.") },
            confirmButton = { TextButton(onClick = { confirmLogout = false; app.logout() }) { Text("Sign out") } },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, null) },
        // The whole row is one switch for TalkBack; the visual Switch itself isn't separately focusable.
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    )
}
