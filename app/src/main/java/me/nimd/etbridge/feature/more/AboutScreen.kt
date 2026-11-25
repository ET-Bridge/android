package me.nimd.etbridge.feature.more

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.ui.EtScreen
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.openUrl

private val SECTIONS = listOf(
    "How it works" to
        "ETBridge talks directly to ETLab's official student API (api.students.etlab.app). There is no ETBridge server: " +
        "your requests go from this phone straight to ETLab.",
    "Your data" to
        "Your session token is encrypted with a key held in the Android Keystore. Attendance, timetable and other data are " +
        "cached on this device so the app works offline. Signing out deletes all of it. App backups are disabled.",
    "Attendance maths" to
        "Safe: you can miss ⌊P / t − T⌋ hours. Short: attend ⌈(t·T − P) / (1 − t)⌉ hours in a row, where P is hours " +
        "present, T is hours conducted and t is the target (75% by default).",
    "Duty leaves" to
        "Duty leaves you log are counted only in projected figures and clearly marked. Once ETLab records them " +
        "officially, ETBridge reconciles them automatically.",
    "Updates" to
        "ETBridge checks https://etbridge.nimd.me/android-update.json for new versions, at most twice a day, and " +
        "downloads updates only from GitHub Releases when you tap Install. No personal data is sent. Each " +
        "download's checksum and signing key are verified before Android installs it.",
    "Feedback" to
        "Feedback you send goes through Web3Forms to the developer, together with your institute code and username so " +
        "issues can be reproduced.",
    "Disclaimer" to
        "ETBridge is an independent, open-source community project. It is not affiliated with, endorsed by or connected " +
        "to ETLab or Etuwa Concepts.",
)

@Composable
fun AboutScreen() {
    val app = LocalAppActions.current
    val context = LocalContext.current
    EtScreen("About & privacy", onBack = app.back, refreshable = false) {
        SECTIONS.forEach { (title, body) ->
            item(title) {
                EtCard {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item("links") {
            EtCard {
                TextButton(onClick = { context.openUrl("https://etbridge.nimd.me/docs") }) { Text("Technical documentation") }
                TextButton(onClick = { context.openUrl("https://github.com/nimdme/et-bridge") }) { Text("Source code on GitHub") }
            }
        }
    }
}
