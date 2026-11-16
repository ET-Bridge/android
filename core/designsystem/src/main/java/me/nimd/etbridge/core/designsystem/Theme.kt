package me.nimd.etbridge.core.designsystem

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Brand palette from webapp/tailwind.config.js
object Brand {
    val Blue50 = Color(0xFFEFF6FF)
    val Blue100 = Color(0xFFDBEAFE)
    val Blue200 = Color(0xFFBFDBFE)
    val Blue300 = Color(0xFF93C5FD)
    val Blue400 = Color(0xFF60A5FA)
    val Blue500 = Color(0xFF3B82F6)
    val Blue600 = Color(0xFF2563EB)
    val Blue700 = Color(0xFF1D4ED8)
    val Blue800 = Color(0xFF1E40AF)
    val Blue900 = Color(0xFF1E3A8A)
    val Blue950 = Color(0xFF0B162C)
    val Ink = Color(0xFF09090B)
    val Card = Color(0xFF18181B)
    val Border = Color(0xFF27272A)
    val Slate50 = Color(0xFFF8FAFC)
}

private val DarkBrandScheme = darkColorScheme(
    primary = Brand.Blue400,
    onPrimary = Color(0xFF041A3D),
    primaryContainer = Brand.Blue800,
    onPrimaryContainer = Brand.Blue100,
    secondary = Color(0xFFA5B4FC),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF272A4A),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFF5EEAD4),
    onTertiary = Color(0xFF042F2E),
    tertiaryContainer = Color(0xFF134E4A),
    onTertiaryContainer = Color(0xFFCCFBF1),
    background = Brand.Ink,
    onBackground = Color(0xFFF4F4F5),
    surface = Brand.Ink,
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Brand.Border,
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color(0xFF050506),
    surfaceContainerLow = Color(0xFF111113),
    surfaceContainer = Brand.Card,
    surfaceContainerHigh = Color(0xFF1F1F23),
    surfaceContainerHighest = Color(0xFF27272A),
    surfaceBright = Color(0xFF2E2E33),
    surfaceDim = Brand.Ink,
    outline = Color(0xFF3F3F46),
    outlineVariant = Brand.Border,
    error = Color(0xFFFB7185),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF4C0519),
    onErrorContainer = Color(0xFFFFE4E6),
    inverseSurface = Color(0xFFF4F4F5),
    inverseOnSurface = Brand.Ink,
    inversePrimary = Brand.Blue600,
)

private val LightBrandScheme = lightColorScheme(
    primary = Brand.Blue600,
    onPrimary = Color.White,
    primaryContainer = Brand.Blue100,
    onPrimaryContainer = Brand.Blue900,
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF042F2E),
    background = Brand.Slate50,
    onBackground = Color(0xFF0F172A),
    surface = Brand.Slate50,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F9),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF1F5F9),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF4C0519),
    inverseSurface = Color(0xFF0F172A),
    inverseOnSurface = Brand.Slate50,
    inversePrimary = Brand.Blue300,
)

/** Semantic attendance colors that stay recognisable on top of any dynamic palette. */
@Immutable
data class StatusColors(
    val safe: Color,
    val onSafeContainer: Color,
    val safeContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val pending: Color,
    val pendingContainer: Color,
    val onPendingContainer: Color,
)

private val DarkStatus = StatusColors(
    safe = Color(0xFF34D399), safeContainer = Color(0xFF052E22), onSafeContainer = Color(0xFFA7F3D0),
    warning = Color(0xFFFBBF24), warningContainer = Color(0xFF3B2A05), onWarningContainer = Color(0xFFFDE68A),
    danger = Color(0xFFFB7185), dangerContainer = Color(0xFF3F0A17), onDangerContainer = Color(0xFFFECDD3),
    pending = Color(0xFFA78BFA), pendingContainer = Color(0xFF261A4A), onPendingContainer = Color(0xFFDDD6FE),
)

private val LightStatus = StatusColors(
    safe = Color(0xFF059669), safeContainer = Color(0xFFD1FAE5), onSafeContainer = Color(0xFF064E3B),
    warning = Color(0xFFD97706), warningContainer = Color(0xFFFEF3C7), onWarningContainer = Color(0xFF78350F),
    danger = Color(0xFFE11D48), dangerContainer = Color(0xFFFFE4E6), onDangerContainer = Color(0xFF881337),
    pending = Color(0xFF7C3AED), pendingContainer = Color(0xFFEDE9FE), onPendingContainer = Color(0xFF4C1D95),
)

val LocalStatusColors = staticCompositionLocalOf { DarkStatus }

private val EtTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Tabular numerals keep percentages from jittering while they animate. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum")

private val EtShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Spring specs used across the app so motion feels consistent. */
object EtMotion {
    fun <T> bouncy() = spring<T>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    fun <T> smooth() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
    fun <T> snappy() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
    fun <T> slow() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)
}

@Composable
fun EtbridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context).withInkSurfaces() else dynamicLightColorScheme(context)
        darkTheme -> DarkBrandScheme
        else -> LightBrandScheme
    }
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = scheme, typography = EtTypography, shapes = EtShapes, content = content)
    }
}

/** Keep the webapp's deep near-black canvas in dark mode even with dynamic color. */
private fun ColorScheme.withInkSurfaces(): ColorScheme = copy(
    background = Color(0xFF0B0B0D),
    surface = Color(0xFF0B0B0D),
    surfaceDim = Color(0xFF0B0B0D),
)

object EtTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
}
