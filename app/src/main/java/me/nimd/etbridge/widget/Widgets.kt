package me.nimd.etbridge.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as DayNight
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import me.nimd.etbridge.MainActivity
import me.nimd.etbridge.R
import me.nimd.etbridge.core.data.prefs.ColorPalette
import me.nimd.etbridge.core.data.prefs.UserPreferencesRepository
import me.nimd.etbridge.core.data.repo.DutyLeaveRepository
import me.nimd.etbridge.core.data.repo.EtlabData
import me.nimd.etbridge.core.data.repo.EtlabRepository
import me.nimd.etbridge.core.data.session.SessionStore
import me.nimd.etbridge.core.domain.AttendanceCalculator
import me.nimd.etbridge.core.domain.OverallAttendance
import me.nimd.etbridge.core.domain.Schedule
import me.nimd.etbridge.core.model.formatPercent
import me.nimd.etbridge.sync.Notifications
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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

// --- Look ------------------------------------------------------------------------------------

/** Widget colours for the app's palette; Material You uses the launcher's dynamic GlanceTheme. */
private class WidgetLook(
    val background: ImageProvider?,
    val text: ColorProvider,
    val subtle: ColorProvider,
    val accent: ColorProvider,
    val accentSoft: ColorProvider,
    val track: ColorProvider,
)

@Composable
private fun look(palette: ColorPalette): WidgetLook = when (palette) {
    ColorPalette.Vibrant -> WidgetLook(
        ImageProvider(R.drawable.widget_bg_vibrant),
        DayNight(Color(0xFF1D1430), Color(0xFFF3EEFF)), DayNight(Color(0xFF5C5373), Color(0xFFC9BFE0)),
        DayNight(Color(0xFF6D3EE8), Color(0xFFB79CFF)), DayNight(Color(0xFFEBE2FF), Color(0xFF4B2A9A)),
        DayNight(Color(0xFFE6DCF5), Color(0xFF3A2F5C)),
    )
    ColorPalette.Classic -> WidgetLook(
        ImageProvider(R.drawable.widget_bg_classic),
        DayNight(Color(0xFF0F172A), Color(0xFFF4F4F5)), DayNight(Color(0xFF475569), Color(0xFFA1A1AA)),
        DayNight(Color(0xFF2563EB), Color(0xFF60A5FA)), DayNight(Color(0xFFDBEAFE), Color(0xFF1E3A8A)),
        DayNight(Color(0xFFE2E8F0), Color(0xFF2E2E33)),
    )
    ColorPalette.Dynamic -> WidgetLook(
        null, GlanceTheme.colors.onSurface, GlanceTheme.colors.onSurfaceVariant,
        GlanceTheme.colors.primary, GlanceTheme.colors.primaryContainer, GlanceTheme.colors.surfaceVariant,
    )
}

private val SafeC = Color(0xFF22C55E)
private val WarnC = Color(0xFFF59E0B)
private val DangerC = Color(0xFFF43F5E)
private val NowC = Color(0xFF10B981)

private fun pctColor(p: Double, target: Double) = when {
    p >= target + 5 -> SafeC
    p >= target -> WarnC
    else -> DangerC
}

private fun openApp(context: Context, destination: String) =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(Notifications.EXTRA_DESTINATION, destination),
    )

@Composable
private fun WidgetFrame(look: WidgetLook, onClick: androidx.glance.action.Action, content: @Composable () -> Unit) {
    val base = GlanceModifier.fillMaxSize()
    val bg = if (look.background != null) base.background(look.background) else base.background(GlanceTheme.colors.widgetBackground)
    Box(
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) bg.cornerRadius(24.dp) else bg)
            .padding(14.dp)
            .clickable(onClick),
    ) { content() }
}

@Composable
private fun Chip(text: String, bg: ColorProvider, fg: ColorProvider) {
    Box(
        GlanceModifier.background(bg).cornerRadius(50.dp).padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, maxLines = 1, style = TextStyle(color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun IconText(icon: Int, text: String, look: WidgetLook) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(icon), null, GlanceModifier.size(13.dp), colorFilter = ColorFilter.tint(look.subtle))
        Spacer(GlanceModifier.width(5.dp))
        Text(text, maxLines = 1, style = TextStyle(color = look.subtle, fontSize = 12.sp))
    }
}

/** Ring drawn on a bitmap (Glance has no determinate circular indicator). */
private fun ringBitmap(fraction: Float, target: Float, color: Color, sizePx: Int = 240): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val stroke = sizePx * 0.1f
    val r = RectF(stroke / 2 + 2, stroke / 2 + 2, sizePx - stroke / 2 - 2, sizePx - stroke / 2 - 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
    paint.color = android.graphics.Color.argb(48, 128, 128, 140)
    c.drawArc(r, 0f, 360f, false, paint)
    paint.color = color.toArgb()
    c.drawArc(r, -90f, 360f * fraction.coerceIn(0.005f, 1f), false, paint)
    // Target tick.
    val angle = Math.toRadians((-90 + 360 * target).toDouble())
    val cx = sizePx / 2f; val rad = r.width() / 2
    val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = sizePx * 0.022f; this.color = android.graphics.Color.argb(200, 160, 160, 175); strokeCap = Paint.Cap.ROUND }
    c.drawLine(
        (cx + (rad - stroke * 0.75f) * Math.cos(angle)).toFloat(), (cx + (rad - stroke * 0.75f) * Math.sin(angle)).toFloat(),
        (cx + (rad + stroke * 0.75f) * Math.cos(angle)).toFloat(), (cx + (rad + stroke * 0.75f) * Math.sin(angle)).toFloat(), tick,
    )
    return bmp
}

private val SMALL = DpSize(110.dp, 110.dp)
private val MEDIUM = DpSize(180.dp, 110.dp)
private val WIDE = DpSize(260.dp, 110.dp)
private val TALL = DpSize(180.dp, 200.dp)

// --- Next class ------------------------------------------------------------------------------

private enum class HourState { Past, Now, Next, Later }

private data class NextClassData(
    val signedIn: Boolean,
    val palette: ColorPalette,
    val badge: String,
    val isNow: Boolean,
    val heading: String,
    val subject: String?,
    val time: String?,
    val teacher: String?,
    val following: String?,
    val hours: List<Pair<Int, HourState>> = emptyList(),
    val message: String? = null,
)

class NextClassWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(context, data) } }
    }

    private suspend fun load(context: Context): NextClassData {
        val e = context.entry()
        val palette = e.prefs().snapshot().palette
        e.sessionStore().load()
            ?: return NextClassData(false, palette, "ETBridge", false, "", null, null, null, null, message = "Sign in to see your timetable")
        val repo = e.repo()
        val now = LocalDateTime.now()
        val focus = Schedule.focus(
            now,
            repo.snapshot(EtlabData.Timetable).orEmpty(),
            repo.snapshot(EtlabData.SemesterTimetable).orEmpty(),
            repo.snapshot(EtlabData.Profile)?.timetable.orEmpty(),
        )
        val period = focus.activePeriod ?: focus.nextPeriod
            ?: return NextClassData(
                true, palette, "TODAY", false, "", null, null, null, null,
                hours = focus.periods.map { it.hour to HourState.Past },
                message = if (focus.periods.isEmpty()) "No classes today — enjoy! ☀️" else "You're done for today 🎉",
            )
        val slot = Schedule.slotFor(period.hour)
        val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        val isNow = focus.activePeriod != null
        val badge = when {
            isNow -> "NOW"
            focus.isTomorrow -> "TOMORROW"
            else -> "NEXT"
        }
        val time = slot?.let {
            if (isNow) "Until ${it.end.format(fmt)}" else "${it.start.format(fmt)} – ${it.end.format(fmt)}"
        }
        val after = focus.periods.firstOrNull { it.hour > period.hour }
        val hours = focus.periods.map { p ->
            p.hour to when {
                p.hour == period.hour -> if (isNow) HourState.Now else HourState.Next
                !focus.isTomorrow && p.hour < period.hour -> HourState.Past
                else -> HourState.Later
            }
        }
        return NextClassData(
            signedIn = true, palette = palette, badge = badge, isNow = isNow,
            heading = "Hour ${period.hour}" + if (focus.isTomorrow) " · ${Schedule.dayName(focus.date)}" else "",
            subject = period.subject.ifBlank { period.subjectCode },
            time = time,
            teacher = period.teacherName.takeIf { it.isNotBlank() },
            following = after?.let { "Then · ${it.subject.ifBlank { it.subjectCode }}" },
            hours = hours,
        )
    }

    @Composable
    private fun Content(context: Context, d: NextClassData) {
        val look = look(d.palette)
        val size = LocalSize.current
        val roomy = size.width >= MEDIUM.width
        val tall = size.height >= TALL.height
        WidgetFrame(look, openApp(context, "timetable")) {
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Chip(
                        (if (d.isNow) "● " else "") + d.badge,
                        bg = if (d.isNow) DayNight(Color(0xFFD1FAE5), Color(0xFF064E3B)) else look.accentSoft,
                        fg = if (d.isNow) DayNight(Color(0xFF047857), Color(0xFF6EE7B7)) else look.accent,
                    )
                    if (roomy && d.heading.isNotBlank()) {
                        Spacer(GlanceModifier.width(8.dp))
                        Text(d.heading, maxLines = 1, style = TextStyle(color = look.subtle, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    }
                }
                Spacer(GlanceModifier.height(8.dp))
                if (d.subject == null) {
                    Text(
                        d.message.orEmpty(), maxLines = 3,
                        style = TextStyle(color = look.text, fontSize = if (roomy) 17.sp else 15.sp, fontWeight = FontWeight.Bold),
                    )
                } else {
                    Text(
                        d.subject, maxLines = if (tall) 3 else 2,
                        style = TextStyle(color = look.text, fontSize = if (roomy) 18.sp else 15.sp, fontWeight = FontWeight.Bold),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    d.time?.let { IconText(R.drawable.ic_widget_clock, it, look) }
                    if (roomy && d.teacher != null) {
                        Spacer(GlanceModifier.height(3.dp))
                        IconText(R.drawable.ic_widget_person, d.teacher.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }, look)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                if (roomy && d.hours.isNotEmpty()) {
                    HourDots(d.hours, look)
                    if (d.following != null && (tall || size.width >= WIDE.width)) {
                        Spacer(GlanceModifier.height(6.dp))
                        Text(d.following, maxLines = 1, style = TextStyle(color = look.accent, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                    }
                } else if (d.following != null) {
                    Text(d.following, maxLines = 1, style = TextStyle(color = look.accent, fontSize = 11.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }

    @Composable
    private fun HourDots(hours: List<Pair<Int, HourState>>, look: WidgetLook) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            hours.forEachIndexed { i, (h, st) ->
                if (i > 0) Spacer(GlanceModifier.width(4.dp))
                val (bg, fg) = when (st) {
                    HourState.Now -> ColorProvider(NowC) to ColorProvider(Color.White)
                    HourState.Next -> look.accent to DayNight(Color.White, Color(0xFF111111))
                    HourState.Past -> look.track to look.subtle
                    HourState.Later -> look.accentSoft to look.accent
                }
                Box(
                    GlanceModifier.size(22.dp).background(bg).cornerRadius(11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$h", style = TextStyle(color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
                }
            }
        }
    }
}

class NextClassWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextClassWidget()
}

// --- Attendance ------------------------------------------------------------------------------

private data class AttendanceWidgetData(
    val signedIn: Boolean,
    val palette: ColorPalette,
    val overall: OverallAttendance?,
    val target: Double,
    val atRisk: List<Pair<String, Double>>,
)

class AttendanceWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { GlanceTheme { Content(context, data) } }
    }

    private suspend fun load(context: Context): AttendanceWidgetData {
        val e = context.entry()
        val prefs = e.prefs().snapshot()
        e.sessionStore().load() ?: return AttendanceWidgetData(false, prefs.palette, null, 75.0, emptyList())
        val repo = e.repo()
        val pending = e.dutyLeaves().pending.first()
        val userTarget = prefs.attendanceTarget.toDouble()
        val override = userTarget.takeIf { it != AttendanceCalculator.DEFAULT_TARGET }
        val days = repo.snapshot(EtlabData.SemesterTimetable).orEmpty()
        val subjects = repo.snapshot(EtlabData.Attendance).orEmpty()
            .map { it to AttendanceCalculator.project(it, pending, days, override) }
        val pendingHours = pending.filter { !it.reconciled }.sumOf { it.hours }
        return AttendanceWidgetData(
            signedIn = true,
            palette = prefs.palette,
            overall = OverallAttendance.from(repo.snapshot(EtlabData.Profile)?.attendance, pendingHours),
            target = override ?: AttendanceCalculator.DEFAULT_TARGET,
            atRisk = subjects.sortedBy { it.second.projectedPercentage }.take(4)
                .map { (s, p) -> (s.shownCode.ifBlank { s.subject }) to p.projectedPercentage },
        )
    }

    @Composable
    private fun Content(context: Context, d: AttendanceWidgetData) {
        val look = look(d.palette)
        val size = LocalSize.current
        val o = d.overall
        WidgetFrame(look, openApp(context, "attendance")) {
            if (!d.signedIn || o == null || o.total <= 0) {
                Column(GlanceModifier.fillMaxSize()) {
                    Chip("ATTENDANCE", look.accentSoft, look.accent)
                    Spacer(GlanceModifier.height(8.dp))
                    Text(
                        if (d.signedIn) "Open ETBridge to sync" else "Sign in to ETBridge",
                        style = TextStyle(color = look.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                }
                return@WidgetFrame
            }
            val shown = if (o.pendingDlHours > 0) o.projectedPercentage else o.percentage
            val color = pctColor(shown, d.target)
            val ringSize: Dp = when {
                size.width < MEDIUM.width -> (minOf(size.width, size.height) - 34.dp).coerceIn(56.dp, 96.dp)
                else -> (size.height - 34.dp).coerceIn(64.dp, 92.dp)
            }
            val status = if (o.margin.isSafe) "Can bunk ${o.margin.canMiss}" else "Attend ${o.margin.mustAttend}"
            val statusBg = DayNight(color.copy(alpha = 0.16f), color.copy(alpha = 0.24f))
            val statusFg = DayNight(
                when (color) { SafeC -> Color(0xFF047857); WarnC -> Color(0xFFB45309); else -> Color(0xFFBE123C) },
                color,
            )

            if (size.width < MEDIUM.width) {
                Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                    Ring(shown, d.target, color, ringSize, look)
                    Spacer(GlanceModifier.height(6.dp))
                    Chip(status, statusBg, statusFg)
                }
                return@WidgetFrame
            }
            val showList = d.atRisk.isNotEmpty() && (size.width >= WIDE.width || size.height >= TALL.height)
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Ring(shown, d.target, color, ringSize, look)
                    Spacer(GlanceModifier.width(12.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text("ATTENDANCE", style = TextStyle(color = look.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                        Spacer(GlanceModifier.height(4.dp))
                        Chip(status + if (o.margin.isSafe) " hrs" else " more", statusBg, statusFg)
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            if (o.pendingDlHours > 0) "incl. ${o.pendingDlHours}h duty leave" else "Target ${d.target.formatPercent()}%",
                            maxLines = 1, style = TextStyle(color = look.subtle, fontSize = 11.sp),
                        )
                    }
                    if (showList && size.width >= WIDE.width && size.height < TALL.height) {
                        Spacer(GlanceModifier.width(10.dp))
                        Column(GlanceModifier.defaultWeight()) { SubjectBars(d.atRisk.take(3), d.target, look) }
                    }
                }
                if (showList && size.height >= TALL.height) {
                    Spacer(GlanceModifier.height(10.dp))
                    Text("LOWEST SUBJECTS", style = TextStyle(color = look.subtle, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.height(4.dp))
                    SubjectBars(d.atRisk, d.target, look)
                }
            }
        }
    }

    @Composable
    private fun Ring(pct: Double, target: Double, color: Color, size: Dp, look: WidgetLook) {
        Box(GlanceModifier.size(size), contentAlignment = Alignment.Center) {
            Image(
                ImageProvider(ringBitmap((pct / 100).toFloat(), (target / 100).toFloat(), color)),
                contentDescription = "Attendance ${pct.formatPercent()} percent", modifier = GlanceModifier.size(size),
            )
            Text(
                "${pct.formatPercent()}%",
                style = TextStyle(
                    color = look.text, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    fontSize = if (size >= 80.dp) 19.sp else 15.sp,
                ),
            )
        }
    }

    @Composable
    private fun SubjectBars(rows: List<Pair<String, Double>>, target: Double, look: WidgetLook) {
        rows.forEachIndexed { i, (code, pct) ->
            if (i > 0) Spacer(GlanceModifier.height(5.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(code, modifier = GlanceModifier.defaultWeight(), maxLines = 1, style = TextStyle(color = look.text, fontSize = 11.sp))
                Text(
                    "${pct.formatPercent()}%",
                    style = TextStyle(color = ColorProvider(pctColor(pct, target)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                )
            }
            Spacer(GlanceModifier.height(2.dp))
            LinearProgressIndicator(
                progress = (pct / 100).toFloat().coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = ColorProvider(pctColor(pct, target)),
                backgroundColor = look.track,
            )
        }
    }
}

class AttendanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AttendanceWidget()
}
