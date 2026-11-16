package me.nimd.etbridge.feature.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.nimd.etbridge.core.designsystem.EmptyState
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.SectionHeader
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.PendingDutyLeave
import me.nimd.etbridge.core.model.formatCompact
import me.nimd.etbridge.core.model.formatIndianDate
import me.nimd.etbridge.ui.EtScreen
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LocalOnlyNotice
import me.nimd.etbridge.ui.LogDutyLeaveSheet
import me.nimd.etbridge.ui.StudentViewModel

@Composable
fun DutyLeavesScreen(vm: StudentViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    var logging by rememberSaveable { mutableStateOf(false) }
    val pending = snap.pendingDutyLeaves.filter { !it.reconciled }
    val reconciled = snap.pendingDutyLeaves.filter { it.reconciled }

    EtScreen(
        title = "Duty leaves",
        onBack = app.back,
        actions = { IconButton(onClick = { logging = true }) { Icon(Icons.Rounded.Add, "Log duty leave") } },
    ) {
        item("local-notice") { LocalOnlyNotice() }
        item("pending-h") {
            SectionHeader(
                "Pending (self-reported)",
                subtitle = "Counted in projections until ETLab records them. Swipe left to delete.",
            )
        }
        if (pending.isEmpty()) {
            item("pending-empty") {
                EmptyState(
                    Icons.Rounded.HourglassTop, "Nothing pending",
                    message = "Log a duty leave as soon as it's approved to see your projected attendance.",
                    action = { TextButton(onClick = { logging = true }) { Text("Log duty leave") } },
                )
            }
        }
        pending.forEach { p ->
            item("p-${p.id}") { SwipeToDelete(Modifier.animateItem(), onDelete = { vm.deleteDutyLeave(p.id) }) { PendingRow(p) } }
        }

        if (reconciled.isNotEmpty()) {
            item("rec-h") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Reconciled", subtitle = "Now recorded officially by ETLab", modifier = Modifier.weight(1f))
                    TextButton(onClick = vm::clearReconciled) { Text("Clear") }
                }
            }
            reconciled.forEach { p -> item("r-${p.id}") { PendingRow(p, Modifier.animateItem()) } }
        }

        item("official-h") { SectionHeader("Official (from ETLab)") }
        if (snap.dutyLeaves.isEmpty()) {
            item("official-empty") {
                Text(
                    "No approved duty leaves on record.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp),
                )
            }
        }
        snap.dutyLeaves.forEach { d ->
            item("o-${d.id}-${d.fromDate}") {
                EtCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.EventAvailable, null, tint = EtTheme.status.safe)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.reason ?: "Duty leave", style = MaterialTheme.typography.titleSmall)
                            val range = listOfNotNull(d.fromDate, d.toDate?.takeIf { it != d.fromDate })
                                .joinToString(" → ") { formatIndianDate(it, DateStyle.Short) }
                            Text(
                                listOfNotNull(range, d.hours.takeIf { it > 0 }?.let { "${it.formatCompact()} hrs" }, d.approvedBy?.let { "by $it" })
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        d.status?.let { Pill(it, tone = if (it.contains("approv", true)) Tone.Safe else Tone.Neutral) }
                    }
                }
            }
        }
    }

    if (logging) {
        LogDutyLeaveSheet(
            days = snap.allDays,
            onDismiss = { logging = false },
            onSave = { reason, date, hours, notes -> vm.logDutyLeave(reason, date, hours, notes = notes) },
        )
    }
}

@Composable
private fun PendingRow(p: PendingDutyLeave, modifier: Modifier = Modifier) {
    EtCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (p.reconciled) Icons.Rounded.CheckCircle else Icons.Rounded.HourglassTop, null,
                tint = if (p.reconciled) EtTheme.status.safe else EtTheme.status.pending,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.reason, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(
                        formatIndianDate(p.date, DateStyle.DayShort),
                        p.selectedHours.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Hours ") ?: "${p.hours} hrs",
                        p.subjectName,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                p.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Pill(if (p.reconciled) "Official" else "Pending", tone = if (p.reconciled) Tone.Safe else Tone.Pending)
        }
    }
}

@Composable
private fun SwipeToDelete(modifier: Modifier = Modifier, onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.large).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
    ) { content() }
}

