package me.nimd.etbridge.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Picks safe/warning/danger for a percentage relative to [target]. */
@Composable
fun attendanceColor(percentage: Double, target: Double = 75.0): Color {
    val s = EtTheme.status
    return when {
        percentage >= target + 5 -> s.safe
        percentage >= target -> s.warning
        else -> s.danger
    }
}

/**
 * Circular attendance gauge. The arc sweeps in with a spring on first composition and
 * whenever the value changes; an optional ghost arc shows the projected value (pending DL).
 */
@Composable
fun AttendanceRing(
    percentage: Double,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 7.dp,
    target: Double = 75.0,
    projected: Double? = null,
    showLabel: Boolean = true,
    labelStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
) {
    val color = attendanceColor(projected ?: percentage, target)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val pendingColor = EtTheme.status.pending
    val tickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(percentage) {
        anim.animateTo((percentage / 100.0).toFloat().coerceIn(0f, 1f), EtMotion.slow())
    }
    val projectedFraction by animateFloatAsState(
        targetValue = ((projected ?: percentage) / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = EtMotion.slow(),
        label = "projected",
    )
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Attendance ${String.format(Locale.ENGLISH, "%.1f", percentage)} percent" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val inset = strokePx / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            if (projected != null && projected > percentage) {
                drawArc(
                    pendingColor.copy(alpha = 0.55f), -90f, 360f * projectedFraction, false, topLeft, arcSize,
                    style = Stroke(strokePx, cap = StrokeCap.Round),
                )
            }
            drawArc(color, -90f, 360f * anim.value, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
            // Target tick
            val angle = Math.toRadians(-90.0 + 360.0 * target / 100.0)
            val r = (this.size.minDimension - strokePx) / 2
            val c = center
            // Target tick stays inside the track so it never pokes out of the ring.
            val inner = r - strokePx * 0.45f
            val outer = r + strokePx * 0.45f
            drawLine(
                color = tickColor,
                start = Offset(c.x + inner * kotlin.math.cos(angle).toFloat(), c.y + inner * kotlin.math.sin(angle).toFloat()),
                end = Offset(c.x + outer * kotlin.math.cos(angle).toFloat(), c.y + outer * kotlin.math.sin(angle).toFloat()),
                strokeWidth = strokePx / 4,
                cap = StrokeCap.Round,
            )
        }
        if (showLabel) {
            Text(
                text = String.format(Locale.ENGLISH, "%.0f%%", anim.value * 100),
                style = labelStyle.merge(NumberStyle),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Linear attendance bar with target marker and optional projected (pending) segment. */
@Composable
fun AttendanceBar(
    percentage: Double,
    modifier: Modifier = Modifier,
    target: Double = 75.0,
    projected: Double? = null,
    height: Dp = 6.dp,
) {
    val color = attendanceColor(projected ?: percentage, target)
    val pending = EtTheme.status.pending
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val marker = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val anim = remember { Animatable(0f) }
    LaunchedEffect(percentage) { anim.animateTo((percentage / 100).toFloat().coerceIn(0f, 1f), EtMotion.slow()) }
    val proj by animateFloatAsState(((projected ?: percentage) / 100).toFloat().coerceIn(0f, 1f), EtMotion.slow(), label = "proj")
    Canvas(
        modifier.fillMaxWidth().height(height).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo((percentage / 100).toFloat().coerceIn(0f, 1f), 0f..1f)
            contentDescription = buildString {
                append(String.format(Locale.ENGLISH, "%.1f percent", percentage))
                if (projected != null && projected > percentage) append(String.format(Locale.ENGLISH, ", %.1f percent with duty leave", projected))
                append(String.format(Locale.ENGLISH, ", target %.0f percent", target))
            }
        },
    ) {
        val h = size.height
        val r = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2)
        drawRoundRect(track, cornerRadius = r)
        if (projected != null && projected > percentage) {
            drawRoundRect(pending.copy(alpha = 0.5f), size = size.copy(width = size.width * proj), cornerRadius = r)
        }
        drawRoundRect(color, size = size.copy(width = size.width * anim.value), cornerRadius = r)
        val x = size.width * (target / 100).toFloat()
        drawLine(marker, Offset(x, -h * 0.4f), Offset(x, h * 1.4f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

enum class Tone { Safe, Warning, Danger, Pending, Neutral, Brand }

@Composable
private fun toneColors(tone: Tone): Pair<Color, Color> {
    val s = EtTheme.status
    val cs = MaterialTheme.colorScheme
    return when (tone) {
        Tone.Safe -> s.safeContainer to s.onSafeContainer
        Tone.Warning -> s.warningContainer to s.onWarningContainer
        Tone.Danger -> s.dangerContainer to s.onDangerContainer
        Tone.Pending -> s.pendingContainer to s.onPendingContainer
        Tone.Neutral -> cs.surfaceContainerHighest to cs.onSurfaceVariant
        Tone.Brand -> cs.primaryContainer to cs.onPrimaryContainer
    }
}

/** Small rounded label, e.g. "Can bunk 4" or "Substitution". */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.Neutral,
    icon: ImageVector? = null,
) {
    val (bg, fg) = toneColors(tone)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = fg)
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "Can bunk N" / "Attend N more" badge (port of BunkBadge.tsx). */
@Composable
fun BunkBadge(canMiss: Int, mustAttend: Int, isSafe: Boolean, modifier: Modifier = Modifier) {
    when {
        !isSafe -> Pill("Attend $mustAttend more", modifier, Tone.Danger)
        canMiss == 0 -> Pill("On the edge", modifier, Tone.Warning)
        else -> Pill("Can bunk $canMiss", modifier, Tone.Safe)
    }
}

/**
 * Elevated card with a tactile press-scale. Pass [onClick] = null for a static card.
 */
@Composable
fun EtCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, EtMotion.snappy(), label = "press")
    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = MaterialTheme.shapes.large,
        color = color,
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            Modifier
                .then(if (onClick != null) Modifier.clickable(interaction, androidx.compose.material3.ripple(), onClick = onClick) else Modifier)
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
) {
    EtCard(modifier, onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, Modifier.size(16.dp), tint = tint) }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(NumberStyle), fontWeight = FontWeight.ExtraBold, maxLines = 1)
        if (caption != null) {
            Text(
                caption, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Shimmering placeholder used while the first payload loads. */
fun Modifier.shimmer(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart),
        label = "x",
    )
    this.clip(shape).background(base).drawWithContent {
        drawContent()
        val w = size.width
        val startX = w * x
        drawRect(
            Brush.linearGradient(
                listOf(Color.Transparent, highlight, Color.Transparent),
                Offset(startX - w / 2, 0f),
                Offset(startX, 0f),
            ),
        )
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 96.dp) {
    Box(modifier.fillMaxWidth().height(height).shimmer(MaterialTheme.shapes.large))
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp)); action()
        }
    }
}

/** Slim, animated banner for sync errors / offline state. */
@Composable
fun StatusBanner(
    message: String?,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = expandVertically(EtMotion.smooth()) + fadeIn(),
        exit = shrinkVertically(EtMotion.smooth()) + fadeOut(),
        modifier = modifier,
    ) {
        val (bg, fg) = toneColors(if (offline) Tone.Neutral else Tone.Danger)
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(bg)
                .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (offline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(10.dp))
            Text(message.orEmpty(), Modifier.weight(1f).padding(vertical = 6.dp), color = fg, style = MaterialTheme.typography.bodySmall)
            if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel, color = fg) }
            else if (onDismiss != null) TextButton(onClick = onDismiss) { Text("OK", color = fg) }
        }
    }
}

/** Two-letter avatar used when a photo is missing. */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val initials = name.split(' ', '.').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }
    Box(
        modifier.size(size).clip(CircleShape).background(
            Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.38f).sp)
    }
}
