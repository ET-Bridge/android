package me.nimd.etbridge.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.IconBubble
import me.nimd.etbridge.core.designsystem.SectionHeader
import me.nimd.etbridge.feature.dashboard.HomeLayoutViewModel
import me.nimd.etbridge.feature.dashboard.HomeSection
import me.nimd.etbridge.ui.EtScreen
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LocalSnackbar

/** Choose which cards appear on Home and in what order. Every change saves immediately. */
@Composable
fun HomeLayoutScreen(vm: HomeLayoutViewModel = hiltViewModel()) {
    val app = LocalAppActions.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val sections by vm.sections.collectAsStateWithLifecycle()
    val customised by vm.isCustomised.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }
    val enabled = sections ?: HomeSection.DEFAULT
    val available = HomeSection.entries.filter { it !in enabled }

    EtScreen(
        "Customize Home", onBack = app.back, refreshable = false,
        subtitle = "${enabled.size} of ${HomeSection.entries.size} cards shown",
        actions = {
            TextButton(onClick = { confirmReset = true }, enabled = customised) {
                Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Reset")
            }
        },
    ) {
        item("tip") {
            EtCard(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.TipsAndUpdates, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Use the arrows to reorder, − to hide and + to add. Changes save as you go, and Reset brings back the original layout.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        item("on-h") { SectionHeader("On your Home screen", subtitle = "Top to bottom, below the greeting") }
        if (enabled.isEmpty()) {
            item("on-empty") {
                EtCard(Modifier.fillMaxWidth()) {
                    Text("Your Home screen is empty", style = MaterialTheme.typography.titleSmall)
                    Text("Add a few cards below, or reset to the default layout.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        enabled.forEachIndexed { i, s ->
            item("on-${s.id}") {
                SectionRow(s, i, Modifier.animateItem()) {
                    IconButton(onClick = { vm.move(s, -1) }, enabled = i > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move ${s.title} up") }
                    IconButton(onClick = { vm.move(s, 1) }, enabled = i < enabled.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move ${s.title} down") }
                    IconButton(onClick = { vm.remove(s) }) {
                        Icon(Icons.Rounded.RemoveCircleOutline, "Remove ${s.title}", tint = EtTheme.status.danger)
                    }
                }
            }
        }

        if (available.isNotEmpty()) {
            item("add-h") { SectionHeader("Add more", subtitle = "Tap + to place a card at the bottom of Home") }
            available.forEach { s ->
                item("add-${s.id}") {
                    SectionRow(s, HomeSection.entries.indexOf(s), Modifier.animateItem(), muted = true, onClick = { vm.add(s) }) {
                        IconButton(onClick = { vm.add(s) }) {
                            Icon(Icons.Rounded.AddCircle, "Add ${s.title}", tint = EtTheme.status.safe)
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(Icons.Rounded.RestartAlt, null) },
            title = { Text("Reset Home?") },
            text = { Text("Home goes back to the original cards in their original order.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    val previous = enabled
                    vm.reset()
                    scope.launch {
                        val r = snackbar.showSnackbar("Home reset to default", actionLabel = "Undo", duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) vm.save(previous)
                    }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionRow(
    section: HomeSection,
    accent: Int,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
    onClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit,
) {
    EtCard(
        modifier.fillMaxWidth(), onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        color = if (muted) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(section.icon, index = accent, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(end = 4.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(section.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            actions()
        }
    }
}
