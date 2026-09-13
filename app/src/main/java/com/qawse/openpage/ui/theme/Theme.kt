package com.qawse.openpage.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Semantic color roles — the ONLY way screens may reference color.
 * Raw palette values stay in Color.kt; nothing outside the theme package
 * touches them.
 */
@Immutable
data class OpenColors(
    // Foundations ---------------------------------------------------------
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val elevatedSurface: Color,   // floating bars, menus, dialogs
    val onSurfaceVariant: Color,  // secondary text, quiet icons
    val divider: Color,           // hairline separators
    val disabled: Color,          // disabled fills & text
    val onDisabled: Color,
    val well: Color,              // recessed areas: preview mats, thumbnails

    // Actions ---------------------------------------------------------------
    val primary: Color,           // the ink button — default action
    val onPrimary: Color,
    val secondary: Color,         // quiet outline button
    val onSecondary: Color,
    val accent: Color,            // the one brand accent (cobalt)
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,

    // Feedback ---------------------------------------------------------------
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

val LocalOpenColors = staticCompositionLocalOf<OpenColors> {
    error("OpenColors not provided — wrap content in OpenPageTheme")
}

/** Light theme: warm paper, near-black ink, cobalt accent. */
private val LightColors = OpenColors(
    background = paperWhite,
    onBackground = ink800,
    surface = paperCard,
    onSurface = ink800,
    elevatedSurface = paperCard,
    onSurfaceVariant = ink500,
    divider = ink100,
    disabled = ink100,
    onDisabled = ink300,
    well = paperWell,
    primary = ink800,
    onPrimary = paperWhite,
    secondary = ink800,
    onSecondary = paperWhite,
    accent = cobalt,
    onAccent = Color.White,
    accentContainer = cobaltContainer,
    onAccentContainer = cobaltOnContainer,
    success = successInk,
    successContainer = successContainer,
    onSuccessContainer = Color(0xFF123B2B),
    warning = warnInk,
    warningContainer = warnContainer,
    onWarningContainer = Color(0xFF4A2E0E),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

/** Dark theme: true night surfaces, ink-quiet accents, same cobalt family. */
private val DarkColors = OpenColors(
    background = nightBg,
    onBackground = nightText,
    surface = nightSurface,
    onSurface = nightText,
    elevatedSurface = nightSurfaceHigh,
    onSurfaceVariant = nightTextDim,
    divider = nightOutlineVariant,
    disabled = nightSurfaceHigh,
    onDisabled = ink500,
    well = nightWell,
    primary = nightText,
    onPrimary = ink900,
    secondary = nightText,
    onSecondary = ink900,
    accent = cobaltLight,
    onAccent = ink900,
    accentContainer = cobaltContainerDark,
    onAccentContainer = cobaltOnContainerDark,
    success = successInkDark,
    successContainer = successContainerDark,
    onSuccessContainer = Color(0xFFC8EDDA),
    warning = warnInkDark,
    warningContainer = warnContainerDark,
    onWarningContainer = Color(0xFFEED9BC),
    error = Color(0xFFF2B8B5),
    errorContainer = errorContainerDark,
    onErrorContainer = Color(0xFFF9DEDC),
)

// Material scheme is derived from the same roles so M3 widgets (menus,
// switches, text fields, dialogs) stay in harmony without per-widget tuning.
private fun lightScheme(c: OpenColors) = lightColorScheme(
    primary = c.primary, onPrimary = c.onPrimary,
    primaryContainer = ink100, onPrimaryContainer = ink800,
    secondary = c.secondary, onSecondary = c.onSecondary,
    secondaryContainer = ink50, onSecondaryContainer = ink600,
    tertiary = c.accent, onTertiary = c.onAccent,
    tertiaryContainer = c.accentContainer, onTertiaryContainer = c.onAccentContainer,
    background = c.background, onBackground = c.onBackground,
    surface = c.surface, onSurface = c.onSurface,
    surfaceVariant = ink50, onSurfaceVariant = c.onSurfaceVariant,
    surfaceDim = ink100, surfaceBright = paperWhite,
    inverseSurface = ink800, inverseOnSurface = paperWhite,
    outline = ink200, outlineVariant = c.divider,
    error = c.error, onError = Color.White,
    errorContainer = c.errorContainer, onErrorContainer = c.onErrorContainer,
    scrim = Color(0x99000000),
)

private fun darkScheme(c: OpenColors) = darkColorScheme(
    primary = c.primary, onPrimary = c.onPrimary,
    primaryContainer = ink600, onPrimaryContainer = nightText,
    secondary = c.secondary, onSecondary = c.onSecondary,
    secondaryContainer = nightSurfaceHigh, onSecondaryContainer = nightText,
    tertiary = c.accent, onTertiary = c.onAccent,
    tertiaryContainer = c.accentContainer, onTertiaryContainer = c.onAccentContainer,
    background = c.background, onBackground = c.onBackground,
    surface = c.surface, onSurface = c.onSurface,
    surfaceVariant = nightSurfaceHigh, onSurfaceVariant = c.onSurfaceVariant,
    surfaceDim = nightWell, surfaceBright = nightSurfaceHigh,
    inverseSurface = paperWhite, inverseOnSurface = ink800,
    outline = nightOutline, outlineVariant = c.divider,
    error = c.error, onError = Color(0xFF601410),
    errorContainer = c.errorContainer, onErrorContainer = c.onErrorContainer,
    scrim = Color(0xB3000000),
)

/**
 * Reduced-motion preference, read from the system animator duration scale.
 * When the user disables animations, every duration in the app collapses
 * to zero — no motion, just instant state changes.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Resolves a base duration against the reduced-motion preference.
 * Use for every animate*AsState / transition in the app.
 */
@Composable
fun rememberMotionDuration(baseMs: Int): Int {
    val reduced = rememberReducedMotion()
    return if (reduced) 0 else baseMs
}

@Composable
fun OpenPageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val open = if (darkTheme) DarkColors else LightColors
    val scheme = if (darkTheme) darkScheme(open) else lightScheme(open)

    CompositionLocalProvider(LocalOpenColors provides open) {
        MaterialTheme(
            colorScheme = scheme,
            typography = OpenTypography,
            content = content,
        )
    }
}

/** Convenience accessor so call sites read `colors.primary`. */
object OpenTheme {
    val colors: OpenColors
        @Composable get() = LocalOpenColors.current
}
