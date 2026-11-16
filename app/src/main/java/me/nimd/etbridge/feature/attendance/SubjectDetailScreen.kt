package me.nimd.etbridge.feature.attendance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.nimd.etbridge.core.designsystem.AttendanceRing
import me.nimd.etbridge.core.designsystem.BunkBadge
import me.nimd.etbridge.core.designsystem.EmptyState
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.NumberStyle
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.SectionHeader
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.designsystem.attendanceColor
import me.nimd.etbridge.core.domain.ClassRecord
import me.nimd.etbridge.core.domain.SubjectHistory
import me.nimd.etbridge.core.domain.SubjectProjection
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.formatCompact
import me.nimd.etbridge.core.model.formatIndianDate
import me.nimd.etbridge.core.model.formatPercent
import me.nimd.etbridge.ui.EtScreen
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LogDutyLeaveSheet
import me.nimd.etbridge.ui.PeriodRow
import me.nimd.etbridge.ui.StudentViewModel
import me.nimd.etbridge.ui.sharedBounds
import me.nimd.etbridge.ui.sharedElement
import java.time.LocalDate
import kotlin.math.roundToInt

private enum class RecordFilter(val label: String) { Absent("Absent"), Present("Present"), DutyLeave("Duty leave"), Upcoming("Upcoming"), All("All") }

@Composable
fun SubjectDetailScreen(key: String, vm: StudentViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    val row = snap.subjects.firstOrNull { it.subject.stableKey == key }
    var filter by rememberSaveable { mutableStateOf(RecordFilter.Absent) }
    var logging by rememberSaveable { mutableStateOf(false) }

    val history = remember(row, snap.allDays) {
        row?.let { SubjectHistory.forSubject(it.subject, snap.allDays, LocalDate.now().toString()) }
    }

    EtScreen(
        title = row?.subject?.shownCode?.ifBlank { null } ?: "Subject",
        subtitle = row?.subject?.teacherName,
        onBack = app.back,
        actions = { IconButton(onClick = { logging = true }) { Icon(Icons.Rounded.Add, "Log duty leave") } },
    ) {
        if (row == null) {
            item { EmptyState(Icons.Rounded.EventBusy, "Subject not found", message = "It may have been removed after the last sync.") }
            return@EtScreen
        }
        val p = row.projection
        item("hero") {
            EtCard(Modifier.sharedBounds("subject-card-$key")) {
                Text(
                    row.subject.subject, style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.sharedBounds("subject-title-$key"),
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AttendanceRing(
                        p.currentPercentage, size = 128.dp, strokeWidth = 12.dp, target = p.target,
                        projected = if (p.hasPending) p.projectedPercentage else null,
                        labelStyle = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.sharedElement("subject-ring-$key"),
                    )
                    Spacer(Modifier.width(18.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Stat("Attended", "${p.currentAttended.formatCompact()} / ${p.totalClasses.formatCompact()} hrs")
                        Stat("Absent", "${(p.totalClasses - p.currentAttended).coerceAtLeast(0.0).formatCompact()} hrs")
                        Stat("Target", "${p.target.formatCompact()}%")
                        if (p.hasPending) Stat("With duty leave", "${p.projectedPercentage.formatPercent()}%", EtTheme.status.pending)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BunkBadge(p.currentCanMiss, p.currentMustAttend, p.isCurrentlySafe)
                    if (p.hasPending) {
                        Pill(
                            if (p.isProjectedSafe) "Projected: bunk ${p.projectedCanMiss}" else "Projected: attend ${p.projectedMustAttend}",
                            tone = Tone.Pending,
                        )
                    }
                }
            }
        }
        item("whatif") { WhatIfCard(p) }

        if (history != null) {
            item("records-h") {
                SectionHeader(
                    "Class history",
                    subtitle = "${history.absent.size} absent · ${history.present.size} present · ${history.dutyLeave.size} DL in the last 4 months",
                )
            }
            item("filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RecordFilter.entries) { f ->
                        val count = when (f) {
                            RecordFilter.Absent -> history.absent.size
                            RecordFilter.Present -> history.present.size
                            RecordFilter.DutyLeave -> history.dutyLeave.size
                            RecordFilter.Upcoming -> history.upcoming.size
                            RecordFilter.All -> history.past.size
                        }
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} ($count)") })
                    }
                }
            }
            val records: List<ClassRecord> = when (filter) {
                RecordFilter.Absent -> history.absent
                RecordFilter.Present -> history.present
                RecordFilter.DutyLeave -> history.dutyLeave
                RecordFilter.Upcoming -> history.upcoming
                RecordFilter.All -> history.past
            }
            if (records.isEmpty()) {
                item("records-empty") {
                    Text(
                        "Nothing here.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp),
                    )
                }
            }
            records.take(60).forEach { r ->
                item("rec-${filter.name}-${r.date}-${r.period.hour}") {
                    Column(Modifier.animateItem()) {
                        Text(
                            formatIndianDate(r.date, DateStyle.DayShort),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 56.dp, bottom = 4.dp),
                        )
                        PeriodRow(r.period)
                    }
                }
            }
            val topics = history.coveredTopics
            if (topics.isNotEmpty()) {
                item("topics-h") { SectionHeader("Topics covered", subtitle = "${topics.size} topics") }
                item("topics") {
                    EtCard {
                        topics.take(40).forEach { (date, topic) ->
                            Row(Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    formatIndianDate(date, DateStyle.DayMonth), Modifier.width(56.dp),
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(topic, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (logging && row != null) {
        LogDutyLeaveSheet(
            days = snap.allDays,
            onDismiss = { logging = false },
            onSave = { reason, date, hours, notes ->
                vm.logDutyLeave(reason, date, hours, row.subject.code, row.subject.subject, notes)
            },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall.merge(NumberStyle), color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** Native extra: drag to see what skipping or attending the next N hours does to your %. */
@Composable
private fun WhatIfCard(p: SubjectProjection) {
    var attend by rememberSaveable { mutableStateOf(false) }
    var hours by remember { mutableFloatStateOf(0f) }
    val n = hours.roundToInt()
    val attended = p.projectedAttended + if (attend) n else 0
    val total = p.totalClasses + n
    val pct = if (total > 0) attended / total * 100 else 0.0
    EtCard {
        Text("What if…", style = MaterialTheme.typography.titleMedium)
        Text(
            "Slide to simulate the next few hours of this subject.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(!attend, { attend = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("I skip") }
            SegmentedButton(attend, { attend = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("I attend") }
        }
        Slider(value = hours, onValueChange = { hours = it }, valueRange = 0f..20f, steps = 19)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (n == 0) "No change" else "${if (attend) "Attend" else "Skip"} next $n hour${if (n == 1) "" else "s"}",
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
            )
            AnimatedContent(pct.formatPercent(), transitionSpec = { fadeIn().togetherWith(fadeOut()) }, label = "pct") { t ->
                Text(
                    "$t%", style = MaterialTheme.typography.headlineSmall.merge(NumberStyle),
                    color = attendanceColor(pct, p.target), fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
