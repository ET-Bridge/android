package me.nimd.etbridge.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import me.nimd.etbridge.BuildConfig
import me.nimd.etbridge.ui.openUrl
import java.util.Locale

private fun mb(bytes: Long) = String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)

/** "Update available" dialog: Install (download with progress → system installer) and What's new. */
@Composable
fun UpdateDialog(
    state: UpdateState,
    mandatory: Boolean,
    onInstall: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onCancelDownload: () -> Unit,
    onOpenInstallSettings: () -> Unit,
) {
    val m = state.manifest ?: return
    val context = LocalContext.current
    var showNotes by rememberSaveable { mutableStateOf(false) }
    val working = state is UpdateState.Downloading || state is UpdateState.Installing

    AlertDialog(
        onDismissRequest = { if (!mandatory && !working) onLater() },
        properties = DialogProperties(dismissOnBackPress = !mandatory && !working, dismissOnClickOutside = false),
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text(if (mandatory) "Update required" else "Update available") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    buildString {
                        append("ETBridge ${m.versionName} is available (you have ${BuildConfig.VERSION_NAME})")
                        if (m.sizeBytes > 0) append(" · ${mb(m.sizeBytes)}")
                        append('.')
                        if (mandatory) append(" This version no longer works properly. Update to keep using ETBridge.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (showNotes) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("What's new", style = MaterialTheme.typography.titleSmall)
                        if (m.notes.isEmpty()) {
                            Text("Bug fixes and improvements.", style = MaterialTheme.typography.bodyMedium)
                        }
                        m.notes.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                        m.releaseUrl?.let { url ->
                            TextButton(onClick = { context.openUrl(url) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("Full release notes on GitHub")
                            }
                        }
                    }
                }

                when (state) {
                    is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val p = state.progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            if (state.total > 0) "Downloading… ${mb(state.bytes)} of ${mb(state.total)}" else "Downloading… ${mb(state.bytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is UpdateState.Installing -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Installing… Android will ask you to confirm.", style = MaterialTheme.typography.bodySmall)
                    }
                    is UpdateState.NeedsPermission -> Text(
                        "To install updates, allow ETBridge to \"Install unknown apps\" in Android settings, then come back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is UpdateState.Failed -> Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }

                if (!mandatory && !working) {
                    TextButton(onClick = onSkip, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("Skip this version", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                when (state) {
                    is UpdateState.Downloading -> TextButton(onClick = onCancelDownload) { Text("Cancel") }
                    is UpdateState.Installing -> Unit
                    else -> {
                        TextButton(onClick = { showNotes = !showNotes }) { Text(if (showNotes) "Hide notes" else "What's new") }
                        when (state) {
                            is UpdateState.NeedsPermission -> Button(onClick = onOpenInstallSettings) { Text("Open settings") }
                            is UpdateState.Failed -> Button(onClick = onInstall) { Text("Retry") }
                            else -> Button(onClick = onInstall) { Text("Install") }
                        }
                    }
                }
            }
        },
        dismissButton = if (!mandatory && !working) {
            { TextButton(onClick = onLater) { Text("Later") } }
        } else null,
    )
}
