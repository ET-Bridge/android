package me.nimd.etbridge.feature.analysis

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.nimd.etbridge.core.designsystem.AttendanceBar
import me.nimd.etbridge.core.designsystem.EmptyState
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtMotion
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.NumberStyle
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.SectionHeader
import me.nimd.etbridge.core.designsystem.SkeletonBlock
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.model.formatCompact
import me.nimd.etbridge.core.model.formatPercent
import me.nimd.etbridge.core.model.formatSemester
import me.nimd.etbridge.ui.EtScreen
import me.nimd.etbridge.ui.LocalAppActions
import me.nimd.etbridge.ui.StudentViewModel
import java.util.Locale

@Composable
fun AnalysisScreen(vm: StudentViewModel = hiltViewModel()) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val app = LocalAppActions.current
    val a = snap.analysis

    EtScreen("Academic analysis", onBack = app.back) {
        if (!snap.loaded) {
            items(3) { SkeletonBlock(height = 140.dp) }
            return@EtScreen
        }
        if (a == null) {
            item { EmptyState(Icons.Rounded.Insights, "No analysis available yet") }
            return@EtScreen
        }
        item("hero") {
            EtCard {
                Text("Cumulative GPA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (a.cgpa > 0) String.format(Locale.ENGLISH, "%.2f", a.cgpa) else "—",
                    style = MaterialTheme.typography.displaySmall.merge(NumberStyle),
                )
                a.student?.let { s ->
                    Text(
                        listOfNotNull(s.batch.takeIf { it.isNotBlank() }, formatSemester(s.currentSem).takeIf { s.currentSem.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Credits", "${a.earnedCredit.formatCompact()}/${a.totalCredit.formatCompact()}", Modifier.weight(1f))
                    Metric("Backlogs", a.totalBacklogs.toString(), Modifier.weight(1f))
                    Metric("Passed", "${a.totalPassed}/${a.totalExams}", Modifier.weight(1f))
                }
            }
        }

        val points = a.semesters.sortedBy { it.position }.mapNotNull { s ->
            s.sgpa.toDoubleOrNull()?.takeIf { it > 0 }?.let { formatSemester(s.semester, "S") to it }
        }
        if (points.size >= 2) {
            item("trend") {
                EtCard {
                    Text("SGPA by semester", style = MaterialTheme.typography.titleMedium)
                    Text("Tap a point for its value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    SgpaChart(points)
                }
            }
        }

        if (a.coursesAtRisk.isNotEmpty()) {
            item("risk-h") { SectionHeader("Courses at risk", subtitle = "Low attendance or internal marks") }
            a.coursesAtRisk.sortedByDescending { it.riskLevel }.forEach { r ->
                item("risk-${r.code}") {
                    EtCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, null, tint = if (r.riskLevel >= 4) EtTheme.status.danger else EtTheme.status.warning)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(r.name, style = MaterialTheme.typography.titleSmall)
                            }
                            Pill(
                                when {
                                    r.riskLevel >= 4 -> "High risk"
                                    r.riskLevel == 3 -> "Moderate"
                                    else -> "Watch"
                                },
                                tone = if (r.riskLevel >= 4) Tone.Danger else Tone.Warning,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Attendance ${r.attendancePer.formatPercent()}%", style = MaterialTheme.typography.labelSmall)
                        AttendanceBar(r.attendancePer, Modifier.padding(vertical = 4.dp))
                        if (r.evaluationAvg > 0) Text("Evaluation average ${r.evaluationAvg.formatCompact()}%", style = MaterialTheme.typography.labelSmall)
                        r.reasons.forEach { reason ->
                            Text("• $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (a.semesters.isNotEmpty()) {
            item("sem-h") { SectionHeader("Semesters") }
            a.semesters.sortedByDescending { it.position }.forEach { s ->
                item("sem-${s.position}-${s.semester}") {
                    EtCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(formatSemester(s.semester), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    listOfNotNull(
                                        s.result.takeIf { it.isNotBlank() },
                                        "${s.totalCourses} courses".takeIf { s.totalCourses > 0 },
                                        "${s.credit.formatCompact()} credits".takeIf { s.credit > 0 },
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    s.sgpa.toDoubleOrNull()?.let { "SGPA ${String.format(Locale.ENGLISH, "%.2f", it)}" } ?: s.sgpa.ifBlank { "—" },
                                    style = MaterialTheme.typography.titleSmall.merge(NumberStyle), fontWeight = FontWeight.Bold,
                                )
                                if (s.isPassed) Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.CheckCircle, null, Modifier.padding(end = 4.dp).width(14.dp), tint = EtTheme.status.safe)
                                    Text("Passed", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                        s.attendance?.takeIf { it.total > 0 }?.let { att ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Attendance ${att.percent.formatPercent()}% (${att.present.formatCompact()}/${att.total.formatCompact()})",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AttendanceBar(att.percent, Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium.merge(NumberStyle), fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Single-series line chart: one hue, 2dp line, 8dp markers with a surface ring, recessive
 * grid, value label on the latest point only, tap a point to read its value.
 */
@Composable
private fun SgpaChart(points: List<Pair<String, Double>>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, EtMotion.slow()) }
    var selected by remember { mutableStateOf(points.lastIndex) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall

    val minY = (points.minOf { it.second } - 0.5).coerceAtLeast(0.0).let { kotlin.math.floor(it) }
    val maxY = (points.maxOf { it.second } + 0.5).coerceAtMost(10.0).let { kotlin.math.ceil(it) }

    Column {
        val sel = points[selected]
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format(Locale.ENGLISH, "%.2f", sel.second), style = MaterialTheme.typography.titleLarge.merge(NumberStyle), color = ink)
            Text("  ${sel.first}", style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics {
                    contentDescription = "SGPA by semester: " + points.joinToString { "${it.first} ${String.format(Locale.ENGLISH, "%.2f", it.second)}" }
                }
                .pointerInput(points) {
                    detectTapGestures { pos ->
                        val stepX = if (points.size > 1) size.width / (points.size - 1).toFloat() else 0f
                        selected = ((pos.x + stepX / 2) / stepX).toInt().coerceIn(points.indices)
                    }
                },
        ) {
            val padTop = 8.dp.toPx()
            val padBottom = 22.dp.toPx()
            val h = size.height - padTop - padBottom
            val stepX = size.width / (points.size - 1).toFloat()
            fun y(v: Double) = padTop + h * (1 - ((v - minY) / (maxY - minY)).toFloat())
            val offsets = points.mapIndexed { i, p -> Offset(i * stepX, y(p.second)) }

            // Recessive grid: three dashed lines.
            for (k in 0..2) {
                val gy = padTop + h * k / 2f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            val path = Path().apply {
                offsets.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            }
            clipRect(right = size.width * reveal.value + 8.dp.toPx()) {
                drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                offsets.forEachIndexed { i, o ->
                    val r = if (i == selected) 6.dp.toPx() else 4.dp.toPx()
                    drawCircle(surface, r + 2.dp.toPx(), o) // surface ring
                    drawCircle(line, r, o)
                }
            }
            // X-axis labels centred under each point (edge labels kept inside the canvas).
            points.forEachIndexed { i, p ->
                val style = labelStyle.copy(color = if (i == selected) ink else muted)
                val layout = measurer.measure(p.first, style)
                val x = (offsets[i].x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, size.height - layout.size.height))
            }
            if (selected in offsets.indices) {
                val o = offsets[selected]
                drawLine(muted.copy(alpha = 0.4f), Offset(o.x, padTop), Offset(o.x, padTop + h), 1.dp.toPx())
            }
        }
    }
}
