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

// "Vibrant": a cheerful violet / pink / teal palette with softly tinted surfaces.
private val DarkVibrantScheme = darkColorScheme(
    primary = Color(0xFFB79CFF),
    onPrimary = Color(0xFF2A0D63),
    primaryContainer = Color(0xFF4B2A9A),
    onPrimaryContainer = Color(0xFFEDE5FF),
    secondary = Color(0xFFFF8AC4),
    onSecondary = Color(0xFF4F0A2C),
    secondaryContainer = Color(0xFF6A1D45),
    onSecondaryContainer = Color(0xFFFFE1EF),
    tertiary = Color(0xFF4FE3CF),
    onTertiary = Color(0xFF003731),
    tertiaryContainer = Color(0xFF0F5A51),
    onTertiaryContainer = Color(0xFFC9FFF5),
    background = Color(0xFF0F0A1C),
    onBackground = Color(0xFFF3EEFF),
    surface = Color(0xFF0F0A1C),
    onSurface = Color(0xFFF3EEFF),
    surfaceVariant = Color(0xFF2C2445),
    onSurfaceVariant = Color(0xFFBDB3D6),
    surfaceContainerLowest = Color(0xFF0A0614),
    surfaceContainerLow = Color(0xFF161026),
    surfaceContainer = Color(0xFF1C1532),
    surfaceContainerHigh = Color(0xFF261D40),
    surfaceContainerHighest = Color(0xFF30264E),
    surfaceBright = Color(0xFF3A2F5C),
    surfaceDim = Color(0xFF0F0A1C),
    outline = Color(0xFF51466F),
    outlineVariant = Color(0xFF2C2445),
    error = Color(0xFFFF7A93),
    onError = Color(0xFF4C0519),
    errorContainer = Color(0xFF5A0D22),
    onErrorContainer = Color(0xFFFFE4E8),
    inverseSurface = Color(0xFFF3EEFF),
    inverseOnSurface = Color(0xFF0F0A1C),
    inversePrimary = Color(0xFF6D3EE8),
)

private val LightVibrantScheme = lightColorScheme(
    primary = Color(0xFF6D3EE8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBE2FF),
    onPrimaryContainer = Color(0xFF2A0D63),
    secondary = Color(0xFFD6337F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE1EF),
    onSecondaryContainer = Color(0xFF4F0A2C),
    tertiary = Color(0xFF00897B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC9FFF5),
    onTertiaryContainer = Color(0xFF003731),
    background = Color(0xFFFBF7FF),
    onBackground = Color(0xFF1D1430),
    surface = Color(0xFFFBF7FF),
    onSurface = Color(0xFF1D1430),
    surfaceVariant = Color(0xFFEDE5F8),
    onSurfaceVariant = Color(0xFF5C5373),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F0FD),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF3ECFC),
    surfaceContainerHighest = Color(0xFFE9E0F6),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE9E0F6),
    outline = Color(0xFFD5CAE6),
    outlineVariant = Color(0xFFEDE5F8),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFFE4E6),
    onErrorContainer = Color(0xFF4C0519),
    inverseSurface = Color(0xFF1D1430),
    inverseOnSurface = Color(0xFFFBF7FF),
    inversePrimary = Color(0xFFB79CFF),
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

private val DarkVibrantStatus = StatusColors(
    safe = Color(0xFF4ADE80), safeContainer = Color(0xFF0A3A23), onSafeContainer = Color(0xFFBBF7D0),
    warning = Color(0xFFFFC53D), warningContainer = Color(0xFF44300A), onWarningContainer = Color(0xFFFFE9A8),
    danger = Color(0xFFFF7A93), dangerContainer = Color(0xFF4A0D20), onDangerContainer = Color(0xFFFFD3DC),
    pending = Color(0xFF7DD3FC), pendingContainer = Color(0xFF0C3550), onPendingContainer = Color(0xFFD6F1FF),
)

private val LightVibrantStatus = StatusColors(
    safe = Color(0xFF16A34A), safeContainer = Color(0xFFD7F9E2), onSafeContainer = Color(0xFF064E2B),
    warning = Color(0xFFEA8A00), warningContainer = Color(0xFFFFF1CC), onWarningContainer = Color(0xFF7A3E00),
    danger = Color(0xFFE11D48), dangerContainer = Color(0xFFFFE1E7), onDangerContainer = Color(0xFF881337),
    pending = Color(0xFF0284C7), pendingContainer = Color(0xFFDDF2FF), onPendingContainer = Color(0xFF0C3A5C),
)

/**
 * Extra accent colours for icon bubbles, chips and highlights. Classic and Material You use only the
 * primary colour (their look is unchanged); Vibrant cycles through a cheerful set and adds gradients.
 */
@Immutable
data class EtAccents(val colors: List<Color>, val vibrant: Boolean) {
    operator fun get(index: Int): Color = colors[Math.floorMod(index, colors.size)]
}

private val DarkVibrantAccents = listOf(
    Color(0xFFB79CFF), Color(0xFFFF8AC4), Color(0xFF4FE3CF), Color(0xFFFFC53D), Color(0xFF7DD3FC), Color(0xFF86EFAC),
)
private val LightVibrantAccents = listOf(
    Color(0xFF6D3EE8), Color(0xFFD6337F), Color(0xFF00897B), Color(0xFFE07A00), Color(0xFF0284C7), Color(0xFF16A34A),
)

val LocalEtAccents = staticCompositionLocalOf { EtAccents(listOf(Brand.Blue400), vibrant = false) }


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
    vibrant: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && !vibrant && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme: ColorScheme = when {
        vibrant -> if (darkTheme) DarkVibrantScheme else LightVibrantScheme
        useDynamic -> if (darkTheme) dynamicDarkColorScheme(context).withInkSurfaces() else dynamicLightColorScheme(context)
        darkTheme -> DarkBrandScheme
        else -> LightBrandScheme
    }
    val status = when {
        vibrant -> if (darkTheme) DarkVibrantStatus else LightVibrantStatus
        else -> if (darkTheme) DarkStatus else LightStatus
    }
    val accents = when {
        vibrant -> EtAccents(if (darkTheme) DarkVibrantAccents else LightVibrantAccents, vibrant = true)
        else -> EtAccents(listOf(scheme.primary), vibrant = false)
    }
    CompositionLocalProvider(LocalStatusColors provides status, LocalEtAccents provides accents) {
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
    val accents: EtAccents
        @Composable get() = LocalEtAccents.current

    /** A soft diagonal gradient for hero cards in the Vibrant palette; null elsewhere (plain card colour). */
    val heroBrush: androidx.compose.ui.graphics.Brush?
        @Composable get() {
            if (!LocalEtAccents.current.vibrant) return null
            val c = MaterialTheme.colorScheme
            return androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(c.primaryContainer.copy(alpha = 0.85f), c.surfaceContainer, c.tertiaryContainer.copy(alpha = 0.55f)),
            )
        }
}
