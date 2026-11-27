package me.nimd.etbridge.feature.more

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.nimd.etbridge.BuildConfig
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.IconBubble
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.SectionHeader
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.navigation.AboutRoute
import me.nimd.etbridge.ui.EtScreen
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LocalSnackbar
import me.nimd.etbridge.ui.StudentViewModel
import me.nimd.etbridge.ui.openUrl
import me.nimd.etbridge.update.UpdateManager
import me.nimd.etbridge.update.UpdateState
import me.nimd.etbridge.update.manifest
import javax.inject.Inject

@HiltViewModel
class AppInfoViewModel @Inject constructor(val updates: UpdateManager) : ViewModel()

private const val DEVELOPER_URL = "https://nimd.me"
private const val PRIVACY_URL = "https://etbridge.nimd.me/privacy"
private const val DOCS_URL = "https://etbridge.nimd.me/docs"
private const val SOURCE_URL = "https://github.com/ET-Bridge/android"
private const val RELEASES_LIST_URL = "https://github.com/ET-Bridge/android/releases"

/** One place for everything about the app itself: version, updates, privacy, links and credits. */
@Composable
fun AppInfoScreen(vm: AppInfoViewModel = hiltViewModel(), student: StudentViewModel = hiltViewModel()) {
    val app = LocalAppActions.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val update by vm.updates.state.collectAsStateWithLifecycle()
    val snap by student.snapshot.collectAsStateWithLifecycle()
    var feedback by rememberSaveable { mutableStateOf(false) }

    EtScreen("App info", onBack = app.back, refreshable = false) {
        item("hero") { Hero() }

        item("updates-h") { SectionHeader("Updates") }
        item("updates") { UpdatesCard(update, vm.updates) { context.openUrl(UpdateManager.RELEASES_URL) } }

        item("privacy-h") { SectionHeader("Privacy & about") }
        item("privacy") {
            EtCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                LinkRow(Icons.Rounded.PrivacyTip, 0, "Privacy policy", "What ETBridge stores and sends (nothing to us)", external = true) { context.openUrl(PRIVACY_URL) }
                Divider()
                LinkRow(Icons.Rounded.Info, 1, "How ETBridge works", "Data, attendance maths, duty leaves, disclaimer") { app.navigate(AboutRoute) }
                Divider()
                LinkRow(Icons.Rounded.Description, 2, "Technical documentation", "The reverse-engineered ETLab API and formulas", external = true) { context.openUrl(DOCS_URL) }
            }
        }

        item("project-h") { SectionHeader("Project") }
        item("project") {
            EtCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
                LinkRow(Icons.Rounded.Code, 3, "Source code", "Open source on GitHub", external = true) { context.openUrl(SOURCE_URL) }
                Divider()
                LinkRow(Icons.Rounded.History, 4, "Release history", "Every version and what changed", external = true) { context.openUrl(RELEASES_LIST_URL) }
                Divider()
                LinkRow(Icons.Rounded.Feedback, 5, "Send feedback", "Report a bug or suggest a feature") { feedback = true }
            }
        }

        item("credit") { Credit { context.openUrl(DEVELOPER_URL) } }
    }

    if (feedback) {
        FeedbackSheet(
            defaultName = snap.profile?.name.orEmpty(),
            onSent = { scope.launch { snackbar.showSnackbar("Thanks! Feedback sent.") } },
            onDismiss = { feedback = false },
        )
    }
}

@Composable
private fun Hero() {
    val context = LocalContext.current
    val icon = remember {
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap(192, 192).asImageBitmap() }.getOrNull()
    }
    EtCard(Modifier.fillMaxWidth(), brush = EtTheme.heroBrush, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(4.dp))
            if (icon != null) {
                Image(icon, contentDescription = null, modifier = Modifier.size(84.dp).clip(RoundedCornerShape(24.dp)))
            }
            Spacer(Modifier.height(12.dp))
            Text("ETBridge", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A faster, friendlier ETLab client",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Version ${BuildConfig.VERSION_NAME}", tone = Tone.Brand)
                Pill("Build ${BuildConfig.VERSION_CODE}")
                if (BuildConfig.DEBUG) Pill("Debug", tone = Tone.Warning)
            }
        }
    }
}

@Composable
private fun UpdatesCard(update: UpdateState, updates: UpdateManager, openReleases: () -> Unit) {
    val s = EtTheme.status
    EtCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val available = update is UpdateState.Available || update is UpdateState.NeedsPermission ||
                (update is UpdateState.Failed && update.manifest != null)
            IconBubble(
                if (available) Icons.Rounded.NewReleases else Icons.Rounded.SystemUpdate,
                tint = if (available) s.pending else if (update == UpdateState.UpToDate) s.safe else null,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        !updates.enabled -> "Updates from GitHub"
                        update == UpdateState.UpToDate -> "You're up to date"
                        update == UpdateState.Checking -> "Checking for updates…"
                        update is UpdateState.Downloading -> "Downloading ${update.manifest.versionName}…"
                        update is UpdateState.Installing -> "Installing ${update.manifest.versionName}…"
                        available -> "Version ${update.manifest?.versionName} is available"
                        else -> "Check for updates"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    when {
                        !updates.enabled -> "This build can't update itself. Get the official APK from GitHub."
                        update is UpdateState.Failed -> update.message
                        else -> "You have ${BuildConfig.VERSION_NAME}. ETBridge also checks automatically twice a day."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (update == UpdateState.Checking) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        }
        if (update is UpdateState.Downloading) {
            Spacer(Modifier.height(12.dp))
            val p = update.progress
            if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        val notes = update.manifest?.notes.orEmpty()
        if (notes.isNotEmpty() && update !is UpdateState.UpToDate) {
            Spacer(Modifier.height(12.dp))
            Text("What's new", style = MaterialTheme.typography.labelLarge)
            notes.forEach { n ->
                Text("•  $n", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                !updates.enabled -> Button(onClick = openReleases, Modifier.weight(1f)) { Text("Open GitHub releases") }
                update is UpdateState.Available || update is UpdateState.NeedsPermission ||
                    (update is UpdateState.Failed && update.manifest != null) ->
                    Button(onClick = updates::install, Modifier.weight(1f)) { Text("Install update") }
                update is UpdateState.Downloading ->
                    FilledTonalButton(onClick = updates::cancelDownload, Modifier.weight(1f)) { Text("Cancel download") }
                else -> FilledTonalButton(
                    onClick = updates::checkNow, Modifier.weight(1f),
                    enabled = update != UpdateState.Checking && update !is UpdateState.Installing,
                ) { Text("Check for updates") }
            }
        }
    }
}

@Composable
private fun LinkRow(icon: ImageVector, accent: Int, title: String, subtitle: String, external: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, index = accent, size = 38.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            if (external) Icons.AutoMirrored.Rounded.OpenInNew else Icons.AutoMirrored.Rounded.ArrowForward, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(start = 68.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

@Composable
private fun Credit(onOpen: () -> Unit) {
    val accents = EtTheme.accents
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onOpen)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Made with ", style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Rounded.Favorite, "love", tint = if (accents.vibrant) accents[1] else EtTheme.status.danger, modifier = Modifier.size(16.dp))
            Text(
                buildAnnotatedString {
                    append(" by ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) { append("NiMD") }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "nimd.me · ETBridge is an independent project, not affiliated with ETLab or Etuwa Concepts.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
