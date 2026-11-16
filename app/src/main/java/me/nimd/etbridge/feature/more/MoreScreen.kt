package me.nimd.etbridge.feature.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.data.repo.FeedbackCategory
import me.nimd.etbridge.core.data.repo.FeedbackRepository
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.InitialsAvatar
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.model.formatSemester
import me.nimd.etbridge.navigation.AboutRoute
import me.nimd.etbridge.navigation.AnalysisRoute
import me.nimd.etbridge.navigation.CampusRoute
import me.nimd.etbridge.navigation.CoursesRoute
import me.nimd.etbridge.navigation.DutyLeavesRoute
import me.nimd.etbridge.navigation.NoticesRoute
import me.nimd.etbridge.navigation.SettingsRoute
import me.nimd.etbridge.ui.EtScreen
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LocalSnackbar
import me.nimd.etbridge.ui.StudentViewModel
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(val repo: FeedbackRepository) : ViewModel()

private data class Entry(val title: String, val subtitle: String, val icon: ImageVector, val route: Any)

@Composable
fun MoreScreen(vm: StudentViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    var feedback by rememberSaveable { mutableStateOf(false) }
    val profile = snap.profile

    val entries = listOf(
        Entry("Courses", "Syllabus, outcomes and coverage", Icons.AutoMirrored.Rounded.MenuBook, CoursesRoute),
        Entry("Academic analysis", "CGPA, SGPA trend and courses at risk", Icons.Rounded.Insights, AnalysisRoute),
        Entry("Campus", "Activity points, fees and surveys", Icons.Rounded.AccountBalance, CampusRoute),
        Entry("Duty leaves", "Log, track and reconcile", Icons.Rounded.EventAvailable, DutyLeavesRoute),
        Entry("Notices & circulars", "${profile?.notices?.size ?: 0} notices · ${profile?.circulars?.size ?: 0} circulars", Icons.Rounded.Campaign, NoticesRoute),
        Entry("Settings", "Theme, app lock, notifications", Icons.Rounded.Settings, SettingsRoute),
        Entry("About & privacy", "How ETBridge works", Icons.Rounded.Info, AboutRoute),
    )

    EtScreen("More") {
        item("profile") {
            EtCard(onClick = { app.navigate(SettingsRoute) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val name = profile?.name?.ifBlank { null } ?: "Student"
                    if (profile?.photo.isNullOrBlank()) InitialsAvatar(name, size = 60.dp)
                    else SubcomposeAsyncImage(
                        model = profile.photo, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(60.dp).clip(CircleShape),
                        error = { InitialsAvatar(name, size = 60.dp) }, loading = { InitialsAvatar(name, size = 60.dp) },
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(profile?.course?.ifBlank { null }, profile?.currentSem?.ifBlank { null }?.let { formatSemester(it) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            profile?.admissionNo?.ifBlank { null }?.let { Pill(it) }
                            profile?.rollNo?.ifBlank { null }?.let { Pill("Roll $it") }
                            if (app.isDemo) Pill("Demo", tone = Tone.Pending)
                        }
                    }
                }
            }
        }
        entries.forEach { e ->
            item(e.title) {
                EtCard(onClick = { app.navigate(e.route) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(e.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall)
                            Text(e.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item("feedback") {
            EtCard(onClick = { feedback = true }, color = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Feedback, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Something missing or wrong?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Send feedback to the developer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
    }
    if (feedback) FeedbackSheet(defaultName = profile?.name.orEmpty()) { feedback = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSheet(defaultName: String, vm: FeedbackViewModel = hiltViewModel(), onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var category by rememberSaveable { mutableStateOf(FeedbackCategory.MissingFeature) }
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var email by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Send feedback", style = MaterialTheme.typography.headlineSmall)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeedbackCategory.entries.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email (for a reply)") }, singleLine = true)
            OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth().height(140.dp), label = { Text("What happened?") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    sending = true; error = null
                    scope.launch {
                        vm.repo.submit(category, name, email, message)
                            .onSuccess { onDismiss(); snackbar.showSnackbar("Thanks! Feedback sent.") }
                            .onFailure { error = it.message }
                        sending = false
                    }
                },
                enabled = !sending && message.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Send")
            }
        }
    }
}
