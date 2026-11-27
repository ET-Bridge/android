package me.nimd.etbridge.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.IconBubble
import me.nimd.etbridge.core.designsystem.NumberStyle
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.SectionHeader
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.designsystem.attendanceColor
import me.nimd.etbridge.core.domain.Schedule
import me.nimd.etbridge.core.domain.ScheduleFocus
import me.nimd.etbridge.core.model.Assignment
import me.nimd.etbridge.core.model.DateStyle
import me.nimd.etbridge.core.model.daysRemaining
import me.nimd.etbridge.core.model.formatCompact
import me.nimd.etbridge.core.model.formatIndianDate
import me.nimd.etbridge.core.model.formatPercent
import me.nimd.etbridge.core.model.formatRupees
import me.nimd.etbridge.core.model.formatSemester
import me.nimd.etbridge.core.model.parseDateSafe
import me.nimd.etbridge.navigation.AcademicsRoute
import me.nimd.etbridge.navigation.AnalysisRoute
import me.nimd.etbridge.navigation.AttendanceRoute
import me.nimd.etbridge.navigation.CampusRoute
import me.nimd.etbridge.navigation.DutyLeavesRoute
import me.nimd.etbridge.navigation.NoticesRoute
import me.nimd.etbridge.navigation.SubjectRoute
import me.nimd.etbridge.navigation.TimetableRoute
import me.nimd.etbridge.ui.HourChip
import me.nimd.etbridge.ui.StudentSnapshot
import me.nimd.etbridge.ui.SubjectCard
import me.nimd.etbridge.ui.keyedUniquely
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Overdue work older than this drops off Home (it stays in Academics). */
private const val OVERDUE_DAYS_SHOWN = 30L

/** Everything a Home card needs; [section] adds that card's items to the Dashboard's LazyColumn. */
internal class HomeContext(
    val snap: StudentSnapshot,
    val focus: ScheduleFocus,
    val now: LocalDateTime,
    val navigate: (Any) -> Unit,
    val logDutyLeave: () -> Unit,
) {
    fun LazyListScope.section(section: HomeSection) {
        when (section) {
            HomeSection.RiskBanner -> {
                val below = snap.subjects.count { !it.projection.isProjectedSafe }
                if (below > 0) item("risk") { RiskBanner(below) { navigate(AttendanceRoute) } }
            }
            HomeSection.NextClass -> item("live") { LiveClassCard(focus, now) { navigate(TimetableRoute) } }
            HomeSection.Attendance -> item("overall") { OverallCard(snap, onOpen = { navigate(AttendanceRoute) }, onLog = logDutyLeave) }
            HomeSection.Deadlines -> deadlines()
            HomeSection.Exams -> exams()
            HomeSection.QuickLinks -> item("quick") { QuickLinks(navigate) }
            HomeSection.NeedsAttention -> needsAttention()
            HomeSection.Notices -> notices()
            HomeSection.TodaySchedule -> today()
            HomeSection.LatestMarks -> latestMarks()
            HomeSection.Academic -> academic()
            HomeSection.DutyLeaves -> dutyLeaves()
            HomeSection.Materials -> materials()
            HomeSection.FeeDues -> fees()
            HomeSection.ActivityPoints -> activity()
            HomeSection.Surveys -> surveys()
        }
    }

    // --- Deadlines: upcoming soonest-first, then recently overdue ----------------------------

    private data class Deadline(val kind: String?, val a: Assignment, val daysLeft: Long?)

    private fun LazyListScope.deadlines() {
        val today = now.toLocalDate()
        val ev = snap.evaluations
        val all = (ev?.assignments.orEmpty().map { null to it } + ev?.otherWork.orEmpty())
            .filter { (_, a) -> a.isOpen }
            .map { (k, a) -> Deadline(k, a, daysUntil(a.lastDate, today)) }
            .filter { it.daysLeft == null || it.daysLeft >= -OVERDUE_DAYS_SHOWN }
        val upcoming = all.filter { (it.daysLeft ?: 0) >= 0 }.sortedBy { it.a.lastDate }
        val overdue = all.filter { (it.daysLeft ?: 0) < 0 }.sortedByDescending { it.a.lastDate }
        val shown = upcoming + overdue
        item("deadlines-h") {
            SectionHeader(
                "Assignment deadlines",
                subtitle = when {
                    shown.isEmpty() -> null
                    overdue.isEmpty() -> "${upcoming.size} due"
                    upcoming.isEmpty() -> "${overdue.size} overdue"
                    else -> "${upcoming.size} due · ${overdue.size} overdue"
                },
                action = "All", onAction = { navigate(AcademicsRoute) },
            )
        }
        if (shown.isEmpty()) {
            item("deadlines-none") {
                EtCard(Modifier.fillMaxWidth(), onClick = { navigate(AcademicsRoute) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.TaskAlt, tint = EtTheme.status.safe)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("All caught up 🎉", style = MaterialTheme.typography.titleSmall)
                            Text("No open assignments right now.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            return
        }
        item("deadlines") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown.take(8).keyedUniquely { "${it.kind}-${it.a.id}" }, key = { it.first }) { (_, d) -> DeadlineCard(d) }
            }
        }
    }

    @Composable
    private fun DeadlineCard(d: Deadline) {
        val s = EtTheme.status
        val left = d.daysLeft
        val overdue = left != null && left < 0
        val (label, tone) = when {
            left == null -> (formatIndianDate(d.a.lastDate, DateStyle.Short) to Tone.Neutral)
            overdue -> ("Overdue ${-left}d" to Tone.Danger)
            left == 0L -> ("Due today" to Tone.Danger)
            left == 1L -> ("Tomorrow" to Tone.Warning)
            left <= 3 -> ("$left days left" to Tone.Warning)
            else -> ("$left days left" to Tone.Neutral)
        }
        EtCard(
            Modifier.width(240.dp), onClick = { navigate(AcademicsRoute) },
            color = if (overdue) s.dangerContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    listOfNotNull(d.a.subjectCode.ifBlank { null }, d.kind).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Pill(label, tone = tone)
            }
            Spacer(Modifier.height(6.dp))
            Text(d.a.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(d.a.subjectName ?: d.a.subject.ifBlank { null }, "Due ${formatIndianDate(d.a.lastDate, DateStyle.Short)}").joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }

    // --- Existing cards ---------------------------------------------------------------------

    private fun LazyListScope.exams() {
        val today = now.toLocalDate().toString()
        val upcoming = snap.evaluations?.seriesExams.orEmpty()
            .filter { !it.isPublished && it.date >= today }
            .sortedWith(compareBy({ it.date }, { it.period }))
        if (upcoming.isEmpty()) return
        item("exams-h") {
            SectionHeader("Upcoming exams", subtitle = "${upcoming.size} scheduled", action = "All", onAction = { navigate(AcademicsRoute) })
        }
        item("exams") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(upcoming.take(8).keyedUniquely { it.id }, key = { it.first }) { (_, e) ->
                    val left = daysRemaining(e.date)
                    EtCard(Modifier.width(220.dp), onClick = { navigate(AcademicsRoute) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.subjectCode, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                            Pill(
                                when (left.text) { "Due today" -> "Today"; "1 day left" -> "Tomorrow"; else -> left.text.replace("left", "away") },
                                tone = if (left.isUrgent) Tone.Warning else Tone.Neutral,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(e.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(e.subjectName ?: e.subject, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(formatIndianDate(e.date, DateStyle.DayShort), e.period?.takeIf { it.isNotBlank() }?.let { "H$it" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    private fun LazyListScope.needsAttention() {
        val attention = snap.subjects.sortedBy { it.projection.projectedPercentage }.take(3)
        if (attention.isEmpty()) return
        item("attention-h") {
            SectionHeader("Needs attention", subtitle = "Your lowest subjects", action = "All subjects", onAction = { navigate(AttendanceRoute) })
        }
        attention.keyedUniquely { "att-${it.subject.stableKey}" }.forEach { (k, row) ->
            item(k) { SubjectCard(row, Modifier.animateItem()) { navigate(SubjectRoute(row.subject.stableKey)) } }
        }
    }

    private fun LazyListScope.notices() {
        val notices = snap.profile?.notices.orEmpty().take(2)
        if (notices.isEmpty()) return
        item("notices-h") { SectionHeader("Recent notices", action = "All", onAction = { navigate(NoticesRoute) }) }
        notices.keyedUniquely { "notice-${it.id}" }.forEach { (k, n) ->
            item(k) {
                EtCard(onClick = { navigate(NoticesRoute) }) {
                    Text(n.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!n.content.isNullOrBlank()) {
                        Text(
                            n.content!!, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        listOfNotNull(formatIndianDate(n.date, DateStyle.Short), n.postedBy).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }

    // --- New optional cards -----------------------------------------------------------------

    private fun LazyListScope.today() {
        val periods = focus.periods
        if (periods.isEmpty()) return
        item("today-h") {
            SectionHeader(
                if (focus.isTomorrow) "Tomorrow · ${Schedule.dayName(focus.date)}" else "Today's timetable",
                subtitle = "${periods.size} hours", action = "Week", onAction = { navigate(TimetableRoute) },
            )
        }
        item("today") {
            EtCard(Modifier.fillMaxWidth(), onClick = { navigate(TimetableRoute) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
                periods.forEach { p ->
                    val active = p.hour == focus.activeHour && focus.activeHour != 0
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (active) Modifier.padding(horizontal = 8.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)) else Modifier.padding(horizontal = 8.dp))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HourChip(p, active = active, showStatus = !focus.isTomorrow)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.subject.ifBlank { p.subjectCode }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(Schedule.slotFor(p.hour)?.label, p.teacherName.takeIf { it.isNotBlank() }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (active) Pill("Now", tone = Tone.Safe)
                    }
                }
            }
        }
    }

    private fun LazyListScope.latestMarks() {
        val marks = snap.evaluations?.seriesExams.orEmpty().filter { it.isPublished }.sortedByDescending { it.date }.take(4)
        if (marks.isEmpty()) return
        item("marks-h") { SectionHeader("Latest marks", action = "All", onAction = { navigate(AcademicsRoute) }) }
        item("marks") {
            EtCard(Modifier.fillMaxWidth(), onClick = { navigate(AcademicsRoute) }) {
                marks.forEachIndexed { i, e ->
                    val pct = e.mark.toDoubleOrNull()?.takeIf { e.maximumMark > 0 }?.let { it / e.maximumMark * 100 }
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.subjectName ?: e.subject.ifBlank { e.subjectCode }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${e.subjectCode} · ${e.title}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (e.maximumMark > 0) "${e.mark} / ${e.maximumMark.formatCompact()}" else e.mark,
                                style = MaterialTheme.typography.titleSmall.merge(NumberStyle), fontWeight = FontWeight.Bold,
                                color = pct?.let { attendanceColor(it, 50.0) } ?: MaterialTheme.colorScheme.onSurface,
                            )
                            pct?.let { Text("${it.formatPercent()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    if (pct != null) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { (pct / 100).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                            color = attendanceColor(pct, 50.0),
                        )
                    }
                }
            }
        }
    }

    private fun LazyListScope.academic() {
        val a = snap.analysis ?: return
        val latest = a.semesters.filter { it.sgpaValue != null }.maxByOrNull { it.position }
        if (a.cgpa <= 0 && latest == null) return
        item("academic") {
            EtCard(Modifier.fillMaxWidth(), onClick = { navigate(AnalysisRoute) }, brush = EtTheme.heroBrush) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Insights, index = 2)
                    Spacer(Modifier.width(14.dp))
                    Text("Academic snapshot", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    Stat("CGPA", if (a.cgpa > 0) String.format(Locale.ENGLISH, "%.2f", a.cgpa) else "—", Modifier.weight(1f))
                    Stat(
                        latest?.let { "SGPA ${formatSemester(it.semester, "S")}" } ?: "SGPA",
                        latest?.sgpaValue?.let { String.format(Locale.ENGLISH, "%.2f", it) } ?: "—", Modifier.weight(1f),
                    )
                    Stat("Backlogs", a.totalBacklogs.toString(), Modifier.weight(1f), danger = a.totalBacklogs > 0)
                }
            }
        }
    }

    private fun LazyListScope.dutyLeaves() {
        val pending = snap.unreconciledPending
        item("dl") {
            EtCard(Modifier.fillMaxWidth(), onClick = { navigate(DutyLeavesRoute) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.EventAvailable, tint = EtTheme.status.pending)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Duty leaves", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (pending.isEmpty()) "${snap.dutyLeaves.size} official · none pending"
                            else "${pending.size} pending · +${pending.sumOf { it.hours }}h → ${snap.overall.projectedPercentage.formatPercent()}% projected",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = logDutyLeave) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Log")
                    }
                }
            }
        }
    }

    private fun LazyListScope.materials() {
        val latest = snap.materials.sortedByDescending { it.addedOn }.take(3)
        if (latest.isEmpty()) return
        item("mat-h") { SectionHeader("New study materials", action = "All", onAction = { navigate(AcademicsRoute) }) }
        item("mat") {
            EtCard(Modifier.fillMaxWidth(), onClick = { navigate(AcademicsRoute) }) {
                latest.forEachIndexed { i, m ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBubble(Icons.Rounded.FolderOpen, index = i + 3, size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(m.subject.ifBlank { null }, m.addedOn.ifBlank { null }?.let { formatIndianDate(it, DateStyle.Short) }).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun LazyListScope.fees() {
        val f = snap.fees ?: return
        if (f.totalBalance <= 0 && !f.hasDue) return
        item("fees") {
            val s = EtTheme.status
            EtCard(Modifier.fillMaxWidth(), onClick = { navigate(CampusRoute) }, color = s.warningContainer.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.CurrencyRupee, tint = s.warning)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Fee due", style = MaterialTheme.typography.titleSmall)
                        Text("${f.totalPaid.formatRupees()} paid of ${f.totalFee.formatRupees()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(f.totalBalance.formatRupees(), style = MaterialTheme.typography.titleMedium.merge(NumberStyle), fontWeight = FontWeight.Bold, color = s.onWarningContainer)
                }
            }
        }
    }

    private fun LazyListScope.activity() {
        val a = snap.activityPoints ?: return
        item("activity") {
            val pts = a.totalPointsAwarded
            val done = pts >= 100
            EtCard(Modifier.fillMaxWidth(), onClick = { navigate(CampusRoute) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.EmojiEvents, tint = if (done) EtTheme.status.safe else EtTheme.status.warning)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Activity points", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (done) "Requirement met 🎉" else "${(100 - pts).formatCompact()} more for B.Tech eligibility",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("${pts.formatCompact()}/100", style = MaterialTheme.typography.titleMedium.merge(NumberStyle), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (pts / 100).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = if (done) EtTheme.status.safe else MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    private fun LazyListScope.surveys() {
        val open = snap.surveys.filter { it.isOpen }
        if (open.isEmpty()) return
        item("surveys") {
            EtCard(Modifier.fillMaxWidth(), onClick = { navigate(CampusRoute) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(Icons.Rounded.Poll, index = 1)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${open.size} open survey${if (open.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            open.first().let { s -> listOfNotNull(s.name, s.deadline?.let { "due ${formatIndianDate(it, DateStyle.Short)}" }).joinToString(" · ") },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, danger: Boolean = false) {
    Column(modifier) {
        Text(
            value, style = MaterialTheme.typography.headlineSmall.merge(NumberStyle), fontWeight = FontWeight.Bold,
            color = if (danger) EtTheme.status.danger else MaterialTheme.colorScheme.onSurface,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private fun daysUntil(date: String, today: LocalDate): Long? =
    parseDateSafe(date)?.let { ChronoUnit.DAYS.between(today, it) }
