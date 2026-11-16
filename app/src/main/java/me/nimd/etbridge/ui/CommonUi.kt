package me.nimd.etbridge.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.nimd.etbridge.core.designsystem.AttendanceBar
import me.nimd.etbridge.core.designsystem.AttendanceRing
import me.nimd.etbridge.core.designsystem.BunkBadge
import me.nimd.etbridge.core.designsystem.EtCard
import me.nimd.etbridge.core.designsystem.EtTheme
import me.nimd.etbridge.core.designsystem.NumberStyle
import me.nimd.etbridge.core.designsystem.Pill
import me.nimd.etbridge.core.designsystem.Tone
import me.nimd.etbridge.core.domain.PeriodStatus
import me.nimd.etbridge.core.domain.Schedule
import me.nimd.etbridge.core.domain.isSpecialPeriod
import me.nimd.etbridge.core.domain.isSubstitutionPeriod
import me.nimd.etbridge.core.domain.status
import me.nimd.etbridge.core.model.TimetablePeriod
import me.nimd.etbridge.core.model.formatCompact
import me.nimd.etbridge.core.model.formatPercent

@Composable
fun statusColor(status: PeriodStatus): Color = when (status) {
    PeriodStatus.Present -> EtTheme.status.safe
    PeriodStatus.Absent -> EtTheme.status.danger
    PeriodStatus.DutyLeave -> EtTheme.status.pending
    PeriodStatus.Suspended -> MaterialTheme.colorScheme.outline
    PeriodStatus.Other -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun StatusPill(status: PeriodStatus, modifier: Modifier = Modifier) {
    when (status) {
        PeriodStatus.Present -> Pill("Present", modifier, Tone.Safe, Icons.Rounded.Check)
        PeriodStatus.Absent -> Pill("Absent", modifier, Tone.Danger, Icons.Rounded.Close)
        PeriodStatus.DutyLeave -> Pill("Duty leave", modifier, Tone.Pending, Icons.Rounded.Event)
        PeriodStatus.Suspended -> Pill("Suspended", modifier, Tone.Neutral, Icons.Rounded.PauseCircle)
        PeriodStatus.Other -> Pill("Not marked", modifier, Tone.Neutral, Icons.Rounded.HourglassTop)
    }
}

/** Attendance summary card for one subject; the ring morphs into the detail screen. */
@Composable
fun SubjectCard(row: SubjectRow, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = row.subject
    val p = row.projection
    EtCard(modifier.sharedBounds("subject-card-${s.stableKey}"), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    s.shownCode.ifBlank { s.subjectType }, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    s.subject, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedBounds("subject-title-${s.stableKey}"),
                )
                if (s.teacherName.isNotBlank()) {
                    Text(
                        s.teacherName, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            AttendanceRing(
                percentage = p.currentPercentage,
                projected = if (p.hasPending) p.projectedPercentage else null,
                target = p.target,
                size = 64.dp,
                modifier = Modifier.sharedElement("subject-ring-${s.stableKey}"),
            )
        }
        Spacer(Modifier.height(12.dp))
        AttendanceBar(p.currentPercentage, target = p.target, projected = if (p.hasPending) p.projectedPercentage else null)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${p.currentAttended.formatCompact()}/${p.totalClasses.formatCompact()} hrs",
                style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (p.hasPending) {
                Pill("+${p.pendingDlHours} DL → ${p.projectedPercentage.formatPercent()}%", tone = Tone.Pending)
            }
            Spacer(Modifier.weight(1f))
            BunkBadge(
                canMiss = if (p.hasPending) p.projectedCanMiss else p.currentCanMiss,
                mustAttend = if (p.hasPending) p.projectedMustAttend else p.currentMustAttend,
                isSafe = if (p.hasPending) p.isProjectedSafe else p.isCurrentlySafe,
            )
        }
    }
}

/** One timetable hour: number + time rail, subject, teacher, badges, topics. */
@Composable
fun PeriodRow(
    period: TimetablePeriod,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    showStatus: Boolean = true,
    showTopics: Boolean = true,
) {
    val accent by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        label = "accent",
    )
    val slot = Schedule.slotFor(period.hour)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    period.hour.toString(), fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (slot != null) {
                Text(
                    slot.start.let { "%d:%02d".format(if (it.hour % 12 == 0) 12 else it.hour % 12, it.minute) },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Column(
            Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, accent, MaterialTheme.shapes.medium)
                .background(
                    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surfaceContainer,
                )
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    period.subjectCode.ifBlank { "LAB / OTHER" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (isActive) Pill("Now", tone = Tone.Brand)
                else if (showStatus && period.attendanceStatus != null) StatusPill(period.status())
            }
            Text(
                period.subject.ifBlank { "Free period" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(slot?.label, period.teacherName.takeIf { it.isNotBlank() }).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (period.isSubstitutionPeriod() || period.isSpecialPeriod() || period.suspended) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (period.isSubstitutionPeriod()) Pill("Substitution", tone = Tone.Warning, icon = Icons.Rounded.SwapHoriz)
                    if (period.isSpecialPeriod()) Pill("Special", tone = Tone.Brand)
                    if (period.suspended) Pill("Suspended: ${period.suspensionReason ?: "Holiday"}", tone = Tone.Neutral)
                }
                val was = listOfNotNull(
                    period.regularSubject?.let { "was $it" },
                    period.inPlaceOf?.let { "in place of $it" },
                ).joinToString(" ")
                if (was.isNotBlank()) {
                    Text(was, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showTopics && period.coveredTopics.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Topics: " + period.coveredTopics.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Compact "chip" rail of a day's hours used on the dashboard. */
@Composable
fun HourChip(period: TimetablePeriod, active: Boolean, modifier: Modifier = Modifier, showStatus: Boolean = true) {
    val st = period.status()
    val bg = when {
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val fg = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("H${period.hour}", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            if (showStatus && period.attendanceStatus != null && st != PeriodStatus.Other) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor(st)))
            }
        }
        Text(
            period.subjectCode.ifBlank { period.subject.take(8) }, color = fg, fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
