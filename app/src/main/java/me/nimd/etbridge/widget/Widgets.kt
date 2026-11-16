package me.nimd.etbridge.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as FixedColor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import me.nimd.etbridge.MainActivity
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.data.repo.DutyLeaveRepository
import me.nimd.etbridge.core.data.repo.EtlabData
import me.nimd.etbridge.core.data.repo.EtlabRepository
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.domain.AttendanceCalculator
import me.nimd.etbridge.core.domain.OverallAttendance
import me.nimd.etbridge.core.domain.Schedule
import me.nimd.etbridge.core.model.formatPercent
import java.time.LocalDateTime

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repo(): EtlabRepository
    fun dutyLeaves(): DutyLeaveRepository
    fun sessionStore(): SessionStore
    fun prefs(): UserPreferencesRepository
}

private fun Context.entry() = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching {
            val manager = GlanceAppWidgetManager(context)
            if (manager.getGlanceIds(NextClassWidget::class.java).isNotEmpty()) NextClassWidget().updateAll(context)
            if (manager.getGlanceIds(AttendanceWidget::class.java).isNotEmpty()) AttendanceWidget().updateAll(context)
        }
    }
}

private val Safe = Color(0xFF34D399)
private val Warn = Color(0xFFFBBF24)
private val Danger = Color(0xFFFB7185)

private fun pctColor(p: Double, target: Double) = when {
    p >= target + 5 -> Safe
    p >= target -> Warn
    else -> Danger
}

@Composable
private fun WidgetFrame(content: @Composable () -> Unit) {
    Column(
        GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) { content() }
}

@Composable
private fun Label(text: String) = Text(
    text.uppercase(),
    style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
)

// --- Next class ------------------------------------------------------------------------------

private data class NextClassData(
    val signedIn: Boolean,
    val heading: String,
    val subject: String?,
    val detail: String?,
    val following: String?,
)

class NextClassWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(data) } }
    }

    private suspend fun load(context: Context): NextClassData {
        val e = context.entry()
        e.sessionStore().load() ?: return NextClassData(false, "ETBridge", null, "Sign in to see your timetable", null)
        val repo = e.repo()
        val now = LocalDateTime.now()
        val focus = Schedule.focus(
            now,
            repo.snapshot(EtlabData.Timetable).orEmpty(),
            repo.snapshot(EtlabData.SemesterTimetable).orEmpty(),
            repo.snapshot(EtlabData.Profile)?.timetable.orEmpty(),
        )
        val period = focus.activePeriod ?: focus.nextPeriod
            ?: return NextClassData(true, "Today", null, "No more classes today", null)
        val heading = when {
            focus.activePeriod != null -> "Now · Hour ${period.hour}"
            focus.isTomorrow -> "Tomorrow · Hour ${period.hour}"
            else -> "Next · Hour ${period.hour}"
        }
        val time = Schedule.slotFor(period.hour)?.label
        val after = focus.periods.firstOrNull { it.hour > period.hour }
        return NextClassData(
            signedIn = true,
            heading = heading,
            subject = period.subject.ifBlank { period.subjectCode },
            detail = listOfNotNull(time, period.teacherName.takeIf { it.isNotBlank() }).joinToString(" · "),
            following = after?.let { "Then: ${it.subject.ifBlank { it.subjectCode }}" },
        )
    }

    @Composable
    private fun Content(d: NextClassData) = WidgetFrame {
        Label(d.heading)
        Spacer(GlanceModifier.height(6.dp))
        if (d.subject != null) {
            Text(
                d.subject, maxLines = 2,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold),
            )
        }
        if (!d.detail.isNullOrBlank()) {
            Spacer(GlanceModifier.height(4.dp))
            Text(d.detail, maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
        }
        if (d.following != null) {
            Spacer(GlanceModifier.height(8.dp))
            Text(d.following, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.secondary, fontSize = 12.sp))
        }
    }
}

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}

// --- Attendance ------------------------------------------------------------------------------

private data class AttendanceWidgetData(
    val signedIn: Boolean,
    val overall: OverallAttendance?,
    val target: Double,
    val atRisk: List<Pair<String, Double>>,
)

class AttendanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(data) } }
    }

    private suspend fun load(context: Context): AttendanceWidgetData {
        val e = context.entry()
        e.sessionStore().load() ?: return AttendanceWidgetData(false, null, 75.0, emptyList())
        val repo = e.repo()
        val pending = e.dutyLeaves().pending.first()
        val userTarget = e.prefs().snapshot().attendanceTarget.toDouble()
        val override = userTarget.takeIf { it != AttendanceCalculator.DEFAULT_TARGET }
        val days = repo.snapshot(EtlabData.SemesterTimetable).orEmpty()
        val subjects = repo.snapshot(EtlabData.Attendance).orEmpty()
            .map { it to AttendanceCalculator.project(it, pending, days, override) }
        val pendingHours = pending.filter { !it.reconciled }.sumOf { it.hours }
        return AttendanceWidgetData(
            signedIn = true,
            overall = OverallAttendance.from(repo.snapshot(EtlabData.Profile)?.attendance, pendingHours),
            target = override ?: AttendanceCalculator.DEFAULT_TARGET,
            atRisk = subjects.sortedBy { it.second.projectedPercentage }.take(3)
                .map { (s, p) -> (s.shownCode.ifBlank { s.subject }) to p.projectedPercentage },
        )
    }

    @Composable
    private fun Content(d: AttendanceWidgetData) = WidgetFrame {
        Label("Attendance")
        val o = d.overall
        if (!d.signedIn || o == null || o.total <= 0) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                if (d.signedIn) "Open ETBridge to sync" else "Sign in to ETBridge",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
            return@WidgetFrame
        }
        val shown = if (o.pendingDlHours > 0) o.projectedPercentage else o.percentage
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${shown.formatPercent()}%",
                style = TextStyle(color = FixedColor(pctColor(shown, d.target)), fontSize = 28.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                if (o.margin.isSafe) "Can bunk ${o.margin.canMiss}" else "Attend ${o.margin.mustAttend} more",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = (shown / 100).toFloat().coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = FixedColor(pctColor(shown, d.target)),
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
        if (d.atRisk.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            for ((code, pct) in d.atRisk) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(
                        code, modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                    )
                    Text(
                        "${pct.formatPercent()}%",
                        style = TextStyle(color = FixedColor(pctColor(pct, d.target)), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

class AttendanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AttendanceWidget()
}

