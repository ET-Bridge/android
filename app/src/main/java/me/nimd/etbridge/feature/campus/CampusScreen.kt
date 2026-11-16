package me.nimd.etbridge.feature.campus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.nimd.etbridge.core.designsystem.EmptyState
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.NumberStyle
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.SectionHeader
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.formatCompact
import me.nimd.etbridge.core.model.formatIndianDate
import me.nimd.etbridge.core.model.formatRupees
import me.nimd.etbridge.navigation.DutyLeavesRoute
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LocalOnlyNotice
import me.nimd.etbridge.ui.StudentSnapshot
import me.nimd.etbridge.ui.StudentViewModel
import me.nimd.etbridge.ui.TabbedScreen

/** KTU requires 100 activity points for B.Tech degree eligibility. */
private const val ACTIVITY_TARGET = 100.0

@Composable
fun CampusScreen(vm: StudentViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    TabbedScreen("Campus", listOf("Activity points", "Fees", "Duty leaves", "Surveys"), onBack = app.back) { tab ->
        when (tab) {
            0 -> activity(snap)
            1 -> fees(snap)
            2 -> dutyLeaves(snap) { app.navigate(DutyLeavesRoute) }
            3 -> surveys(snap)
        }
    }
}

private fun LazyListScope.activity(snap: StudentSnapshot) {
    val a = snap.activityPoints
    if (a == null) {
        item { EmptyState(Icons.Rounded.EmojiEvents, "No activity points data") }
        return
    }
    item("hero") {
        val pts = a.totalPointsAwarded
        EtCard {
            Text("KTU activity points", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(pts.formatCompact(), style = MaterialTheme.typography.displaySmall.merge(NumberStyle))
                Text(" / ${ACTIVITY_TARGET.formatCompact()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (pts / ACTIVITY_TARGET).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.small),
                color = if (pts >= ACTIVITY_TARGET) EtTheme.status.safe else MaterialTheme.colorScheme.primary,
            )
            Text(
                if (pts >= ACTIVITY_TARGET) "Requirement met for degree eligibility 🎉"
                else "${(ACTIVITY_TARGET - pts).formatCompact()} more points needed for B.Tech eligibility",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    if (a.awarded.isNotEmpty()) item("ah") { SectionHeader("Awarded activities") }
    a.awarded.forEach { act ->
        item("act-${act.id}") {
            EtCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(act.activityName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(act.category, act.semester, act.date?.let { formatIndianDate(it, DateStyle.Short) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("+${act.points.formatCompact()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = EtTheme.status.safe)
                        act.status?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.fees(snap: StudentSnapshot) {
    val f = snap.fees
    if (f == null) {
        item { EmptyState(Icons.Rounded.CurrencyRupee, "No fee information") }
        return
    }
    item("overview") {
        EtCard {
            Text("Fee overview", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeeStat("Total", f.totalFee.formatRupees(), Modifier.weight(1f))
                FeeStat("Paid", f.totalPaid.formatRupees(), Modifier.weight(1f))
                FeeStat("Balance", f.totalBalance.formatRupees(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            if (f.hasDue || f.totalBalance > 0) Pill("Dues pending", tone = Tone.Danger) else Pill("No dues — all clear", tone = Tone.Safe)
        }
    }
    f.feeTypes.forEach { t ->
        item("ft-${t.feeTypeId}-${t.feeTypeName}") {
            EtCard {
                Text(t.feeTypeName, style = MaterialTheme.typography.titleSmall)
                t.installments.forEach { ins ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ins.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                listOfNotNull(ins.receiptNo?.let { "Receipt $it" }, ins.dueDate?.let { "Due ${formatIndianDate(it, DateStyle.Short)}" }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(ins.amount.formatRupees(), style = MaterialTheme.typography.titleSmall.merge(NumberStyle))
                            ins.status?.let { Pill(it, tone = if (it.contains("paid", true)) Tone.Safe else Tone.Warning) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeeStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium.merge(NumberStyle), fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.dutyLeaves(snap: StudentSnapshot, onOpen: () -> Unit) {
    item("dl-notice") { LocalOnlyNotice(compact = true) }
    item("dl") {
        val pending = snap.unreconciledPending
        EtCard(onClick = onOpen) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EventAvailable, null, tint = EtTheme.status.pending)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Manage duty leaves", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${snap.dutyLeaves.size} official · ${pending.size} pending (${pending.sumOf { it.hours }}h)",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
            }
        }
    }
    snap.dutyLeaves.take(10).forEach { d ->
        item("o-${d.id}-${d.fromDate}") {
            EtCard {
                Text(d.reason ?: "Duty leave", style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(d.fromDate?.let { formatIndianDate(it, DateStyle.Short) }, d.status).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LazyListScope.surveys(snap: StudentSnapshot) {
    if (snap.surveys.isEmpty()) {
        item { EmptyState(Icons.Rounded.Poll, "No surveys", message = "Faculty feedback surveys appear here when they open.") }
        return
    }
    snap.surveys.sortedByDescending { it.isOpen }.forEach { s ->
        item("s-${s.id}") {
            EtCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(s.subject, s.teacherNames, s.session, s.deadline?.let { "Due ${formatIndianDate(it, DateStyle.Short)}" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Pill(s.status ?: if (s.isOpen) "Open" else "Closed", tone = if (s.isOpen) Tone.Warning else Tone.Neutral)
                }
            }
        }
    }
}
