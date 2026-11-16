package me.nimd.etbridge.feature.attendance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.nimd.etbridge.core.designsystem.AttendanceRing
import me.nimd.etbridge.core.designsystem.EmptyState
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.NumberStyle
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.SectionHeader
import me.nimd.etbridge.core.designsystem.SkeletonBlock
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.domain.DaySummary
import me.nimd.etbridge.core.domain.SubjectHistory
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.formatCompact
import me.nimd.etbridge.core.model.formatIndianDate
import me.nimd.etbridge.core.model.formatPercent
import me.nimd.etbridge.navigation.DutyLeavesRoute
import me.nimd.etbridge.navigation.SubjectRoute
import me.nimd.etbridge.ui.EtScreen
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.LogDutyLeaveSheet
import me.nimd.etbridge.ui.PeriodRow
import me.nimd.etbridge.ui.StudentSnapshot
import me.nimd.etbridge.ui.StudentViewModel
import me.nimd.etbridge.ui.SubjectCard
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class SortMode(val label: String) { Lowest("Lowest first"), Highest("Highest first"), Name("Name") }

@Composable
fun AttendanceScreen(vm: StudentViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    var calendar by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(SortMode.Lowest) }
    var sortMenu by remember { mutableStateOf(false) }
    var logging by rememberSaveable { mutableStateOf(false) }

    EtScreen(
        title = "Attendance",
        actions = {
            IconButton(onClick = { logging = true }) { Icon(Icons.Rounded.Add, "Log duty leave") }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
                DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortMode.entries.forEach { m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = { sort = m; sortMenu = false })
                    }
                }
            }
        },
    ) {
        item("summary") { SummaryCard(snap) }
        item("dl") { PendingDlCard(snap) { app.navigate(DutyLeavesRoute) } }
        item("toggle") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !calendar, onClick = { calendar = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Rounded.DonutLarge, null, Modifier.size(18.dp)) },
                ) { Text("By subject") }
                SegmentedButton(
                    selected = calendar, onClick = { calendar = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) },
                ) { Text("Calendar") }
            }
        }

        if (!snap.loaded || snap.attendance == null) {
            items(4) { SkeletonBlock(height = 150.dp) }
            return@EtScreen
        }

        if (!calendar) {
            val rows = when (sort) {
                SortMode.Lowest -> snap.subjects.sortedBy { it.projection.projectedPercentage }
                SortMode.Highest -> snap.subjects.sortedByDescending { it.projection.projectedPercentage }
                SortMode.Name -> snap.subjects.sortedBy { it.subject.subject }
            }
            if (rows.isEmpty()) {
                item("empty") { EmptyState(Icons.Rounded.DonutLarge, "No attendance yet", message = "Subjects appear once ETLab publishes attendance.") }
            }
            rows.forEach { row ->
                item(row.subject.stableKey, contentType = "subject") {
                    SubjectCard(row, Modifier.animateItem()) { app.navigate(SubjectRoute(row.subject.stableKey)) }
                }
            }
        } else {
            item("calendar") { CalendarView(snap) }
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
private fun SummaryCard(snap: StudentSnapshot) {
    val o = snap.overall
    val absent = (o.total - o.attended).coerceAtLeast(0.0)
    EtCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AttendanceRing(
                o.percentage, size = 88.dp, strokeWidth = 9.dp, target = snap.target,
                projected = if (o.pendingDlHours > 0) o.projectedPercentage else null,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Semester overall", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${o.percentage.formatPercent()}%", style = MaterialTheme.typography.headlineMedium.merge(NumberStyle))
                if (o.total > 0) {
                    if (o.margin.isSafe) Text(
                        "You can miss ${o.margin.canMiss} more hour${if (o.margin.canMiss == 1) "" else "s"}",
                        color = EtTheme.status.safe, style = MaterialTheme.typography.bodySmall,
                    ) else Text(
                        "Attend the next ${o.margin.mustAttend} hours to recover",
                        color = EtTheme.status.danger, style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniStat("Attended", o.attended.formatCompact(), Modifier.weight(1f))
            MiniStat("Absent", absent.formatCompact(), Modifier.weight(1f))
            MiniStat("Conducted", o.total.formatCompact(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge.merge(NumberStyle), fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PendingDlCard(snap: StudentSnapshot, onOpen: () -> Unit) {
    val pending = snap.unreconciledPending
    val reconciled = snap.pendingDutyLeaves.count { it.reconciled }
    if (pending.isEmpty() && reconciled == 0) return
    val s = EtTheme.status
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(s.pendingContainer)
            .clickable(onClick = onOpen)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.EventAvailable, null, tint = s.onPendingContainer)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (pending.isNotEmpty()) "${pending.sumOf { it.hours }}h duty leave pending" else "Duty leaves reconciled",
                style = MaterialTheme.typography.titleSmall, color = s.onPendingContainer,
            )
            Text(
                if (pending.isNotEmpty()) "Saved on this phone only · counted in projections until ETLab records them"
                else "$reconciled entr${if (reconciled == 1) "y is" else "ies are"} now official",
                style = MaterialTheme.typography.bodySmall, color = s.onPendingContainer.copy(alpha = 0.8f),
            )
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = s.onPendingContainer)
    }
}

@Composable
private fun CalendarView(snap: StudentSnapshot) {
    val summaries = remember(snap.allDays) { SubjectHistory.daySummaries(snap.allDays) }
    val months = remember(summaries) {
        summaries.keys.mapNotNull { runCatching { YearMonth.from(LocalDate.parse(it)) }.getOrNull() }.distinct().sorted()
    }
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)
    var selected by rememberSaveable { mutableStateOf<String?>(LocalDate.now().toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EtCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { month = ym.minusMonths(1).toString() },
                    enabled = months.isEmpty() || ym > months.first(),
                ) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                Text(
                    "${ym.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${ym.year}",
                    Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium,
                )
                IconButton(
                    onClick = { month = ym.plusMonths(1).toString() },
                    enabled = ym < YearMonth.now(),
                ) { Icon(Icons.Rounded.ChevronRight, "Next month") }
            }
            val monthDays = summaries.values.filter { it.date.startsWith(month) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                Pill("${monthDays.sumOf { it.present }} present", tone = Tone.Safe)
                Pill("${monthDays.sumOf { it.absent }} absent", tone = Tone.Danger)
                if (monthDays.sumOf { it.dutyLeave } > 0) Pill("${monthDays.sumOf { it.dutyLeave }} DL", tone = Tone.Pending)
            }
            AnimatedContent(ym, transitionSpec = { fadeIn().togetherWith(fadeOut()) }, label = "month") { m ->
                MonthGrid(m, summaries, selected) { selected = it }
            }
        }
        val day = selected?.let { sel -> snap.allDays.firstOrNull { it.date == sel } }
        if (selected != null) {
            SectionHeader(formatIndianDate(selected, DateStyle.DayLong), subtitle = day?.let { "${it.sub.size} periods" } ?: "No timetable data")
            day?.sub?.sortedBy { it.hour }?.forEach { p -> PeriodRow(p, Modifier.padding(bottom = 8.dp)) }
        }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, summaries: Map<String, DaySummary>, selected: String?, onSelect: (String) -> Unit) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7 // Sunday-first grid
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    val today = LocalDate.now().toString()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until 7) {
                    val index = r * 7 + c - lead
                    if (index < 0 || index >= month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(index + 1).toString()
                        DayCell(index + 1, summaries[date], date == selected, date == today, Modifier.weight(1f)) { onSelect(date) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, summary: DaySummary?, selected: Boolean, today: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val s = EtTheme.status
    val bg = when {
        summary == null -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
        summary.suspended -> MaterialTheme.colorScheme.surfaceContainerHighest
        summary.marked == 0 -> MaterialTheme.colorScheme.surfaceContainerHigh
        summary.absent == 0 -> s.safeContainer
        summary.present == 0 && summary.dutyLeave == 0 -> s.dangerContainer
        else -> s.warningContainer
    }
    val border by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primary
            today -> MaterialTheme.colorScheme.outline
            else -> androidx.compose.ui.graphics.Color.Transparent
        },
        label = "cell",
    )
    Box(
        modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .border(2.dp, border, MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append("Day $day")
                    if (today) append(", today")
                    when {
                        summary == null -> append(", no data")
                        summary.suspended -> append(", no classes")
                        summary.marked > 0 -> append(", ${summary.present} present, ${summary.absent} absent" + if (summary.dutyLeave > 0) ", ${summary.dutyLeave} duty leave" else "")
                    }
                }
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.toString(), style = MaterialTheme.typography.labelLarge)
            if (summary != null && summary.marked > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (summary.absent > 0) Dot(s.danger)
                    if (summary.dutyLeave > 0) Dot(s.pending)
                    if (summary.present > 0) Dot(s.safe)
                }
            }
        }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) =
    Box(Modifier.size(5.dp).clip(CircleShape).background(color))

