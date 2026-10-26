package com.example.joggingapp.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// ── Token data class ──────────────────────────────────────────────────────────

@Immutable
data class JogginColorTokens(
    val primary: Color,
    val primaryDark: Color,
    val secondary: Color,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val textSecondary: Color,
    val success: Color,
    val error: Color,
    val running: Color,
    val cycling: Color,
    val walking: Color,
    val blend: Color,
    val divider: Color,
    val gold: Color,
    val heroGradient: Brush,
)

// ── Theme enum ────────────────────────────────────────────────────────────────

enum class AppTheme(val displayName: String, val emoji: String) {
    SUNRISE("Sunrise", "🌅"),
    FOREST("Forest Trail", "🌲"),
    MIDNIGHT("Midnight", "💜"),
    OCEAN("Ocean Breeze", "🌊"),
}

// ── Activity-colour ramp ──────────────────────────────────────────────────────

/**
 * Produces an ordered ramp of [count] on-hue shades from [base]. The hue (H) is held
 * constant so every shade stays in the same colour family; value (V) steps up by a
 * fixed amount per index so adjacent shades are always distinguishable, and saturation
 * (S) is eased down slightly so lighter shades do not look neon. index 0 = darkest
 * on-hue shade, higher indices = progressively lighter.
 */
private fun hueRamp(base: Color, count: Int): List<Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    val h = hsv[0]
    val baseS = hsv[1]
    val baseV = hsv[2]
    // Pin the ramp floor BELOW the base value so there is always headroom for the steps,
    // even when the base is already very bright (Sunrise, every dark-theme primary).
    val v0 = minOf(baseV, 0.55f)
    return List(count) { i ->
        val v = (v0 + i * 0.13f).coerceAtMost(0.95f)   // guarantees ~0.13 ΔV between steps
        val s = (baseS * (1f - 0.10f * i)).coerceIn(0.30f, 1f)
        Color.hsv(h, s, v)
    }
}

// ── Theme 1: Sunrise 🌅 (orange) ─────────────────────────────────────────────

private val SunriseLight = JogginColorTokens(
    // Main theme colour on both pane (secondary) and start button (primary); avatar uses primaryDark (~3 tones darker).
    primary = Color(0xFFC24A1E), primaryDark = Color(0xFF8F3614),
    secondary = Color(0xFFC24A1E), accent = Color(0xFFFFB07A),
    background = Color(0xFFFFF7F0), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFFCEBDD),
    onPrimary = Color.White, onSecondary = Color.White,
    onBackground = Color(0xFF2E1A0C), onSurface = Color(0xFF2E1A0C), textSecondary = Color(0xFF8A6B52),
    success = Color(0xFF3F8B55), error = Color(0xFFC62828),
    // running/cycling/walking/blend are derived from the theme hue at resolve time (see getTokens).
    running = Color.Unspecified, cycling = Color.Unspecified, walking = Color.Unspecified,
    blend = Color.Unspecified,
    divider = Color(0xFFF3E0D0), gold = Color(0xFFE0A52E),
    heroGradient = Brush.linearGradient(listOf(Color(0xFFC24A1E), Color(0xFFFFB07A)))
)
private val SunriseDark = SunriseLight.copy(
    background = Color(0xFF26140A), surface = Color(0xFF40230F), surfaceVariant = Color(0xFF5C3317),
    onBackground = Color(0xFFFFF0E3), onSurface = Color(0xFFFFF0E3), textSecondary = Color(0xFFD1A98F),
    divider = Color(0xFF4A2B17)
)

// ── Theme 2: Forest Trail 🌲 (green) ─────────────────────────────────────────

private val ForestLight = JogginColorTokens(
    // Main theme colour on both pane (secondary) and start button (primary); avatar uses primaryDark (~3 tones darker).
    primary = Color(0xFF2D6A4F), primaryDark = Color(0xFF1E4A37),
    secondary = Color(0xFF2D6A4F), accent = Color(0xFF95D5B2),
    background = Color(0xFFF1FAEE), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE3F1E8),
    onPrimary = Color.White, onSecondary = Color.White,
    onBackground = Color(0xFF14281D), onSurface = Color(0xFF14281D), textSecondary = Color(0xFF52796F),
    success = Color(0xFF40916C), error = Color(0xFFD62828),
    running = Color.Unspecified, cycling = Color.Unspecified, walking = Color.Unspecified,
    blend = Color.Unspecified,
    divider = Color(0xFFD4E6D0), gold = Color(0xFFC9A227),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF2D6A4F), Color(0xFF95D5B2)))
)
private val ForestDark = ForestLight.copy(
    background = Color(0xFF0A1F14), surface = Color(0xFF16352A), surfaceVariant = Color(0xFF1F4A39),
    onBackground = Color(0xFFE8F5E3), onSurface = Color(0xFFE8F5E3), textSecondary = Color(0xFF9BC6AD),
    divider = Color(0xFF2B4F3B)
)

// ── Theme 3: Midnight 💜 (purple) ────────────────────────────────────────────

private val MidnightLight = JogginColorTokens(
    // Main theme colour on both pane (secondary) and start button (primary); avatar uses primaryDark (~3 tones darker).
    primary = Color(0xFF6A2FB8), primaryDark = Color(0xFF4A1F85),
    secondary = Color(0xFF6A2FB8), accent = Color(0xFFC9A7F0),
    background = Color(0xFFF7F3FF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEDE4FB),
    onPrimary = Color.White, onSecondary = Color.White,
    onBackground = Color(0xFF1E1430), onSurface = Color(0xFF1E1430), textSecondary = Color(0xFF6E5F8A),
    success = Color(0xFF3DA35D), error = Color(0xFFD62828),
    running = Color.Unspecified, cycling = Color.Unspecified, walking = Color.Unspecified,
    blend = Color.Unspecified,
    divider = Color(0xFFE2D8F2), gold = Color(0xFFD4AF37),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF6A2FB8), Color(0xFFC9A7F0)))
)
private val MidnightDark = MidnightLight.copy(
    background = Color(0xFF120726), surface = Color(0xFF241140), surfaceVariant = Color(0xFF351B5C),
    onBackground = Color(0xFFF0EAFF), onSurface = Color(0xFFF0EAFF), textSecondary = Color(0xFFB3A3D1),
    divider = Color(0xFF2E1B4A)
)

// ── Theme 4: Ocean Breeze 🌊 (blue) ──────────────────────────────────────────

private val OceanLight = JogginColorTokens(
    // Main theme colour on both pane (secondary) and start button (primary); avatar uses primaryDark (~3 tones darker).
    primary = Color(0xFF0077B6), primaryDark = Color(0xFF023E6B),
    secondary = Color(0xFF0077B6), accent = Color(0xFF90E0EF),
    background = Color(0xFFF0F9FF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE0F0FB),
    onPrimary = Color.White, onSecondary = Color.White,
    onBackground = Color(0xFF0A2540), onSurface = Color(0xFF0A2540), textSecondary = Color(0xFF4A6E8A),
    success = Color(0xFF2A9D8F), error = Color(0xFFD62828),
    running = Color.Unspecified, cycling = Color.Unspecified, walking = Color.Unspecified,
    blend = Color.Unspecified,
    divider = Color(0xFFD4E8F7), gold = Color(0xFFE8B84B),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF0077B6), Color(0xFF90E0EF)))
)
private val OceanDark = OceanLight.copy(
    background = Color(0xFF021726), surface = Color(0xFF083251), surfaceVariant = Color(0xFF0E4675),
    onBackground = Color(0xFFE0F0FB), onSurface = Color(0xFFE0F0FB), textSecondary = Color(0xFF8FB7D6),
    divider = Color(0xFF0C3A63)
)

// ── Theme resolver ────────────────────────────────────────────────────────────

fun getTokens(theme: AppTheme, dark: Boolean): JogginColorTokens {
    val base = when (theme) {
        AppTheme.SUNRISE  -> if (dark) SunriseDark else SunriseLight
        AppTheme.FOREST   -> if (dark) ForestDark else ForestLight
        AppTheme.MIDNIGHT -> if (dark) MidnightDark else MidnightLight
        AppTheme.OCEAN    -> if (dark) OceanDark else OceanLight
    }
    // Activity-type colours are ordered on-hue shades of the theme's own primary, so the
    // Activity-History card borders always track the selected theme (dark→light = run→blend).
    val ramp = hueRamp(base.primary, 4)
    return base.copy(running = ramp[0], cycling = ramp[1], walking = ramp[2], blend = ramp[3])
}

fun getPreviewColors(theme: AppTheme): Triple<Color, Color, Color> {
    // Preview dots derive from the resolved tokens so the picker never drifts from the palette.
    val t = getTokens(theme, dark = false)
    return Triple(t.primary, t.primaryDark, t.accent)
}

// ── CompositionLocal ──────────────────────────────────────────────────────────

val LocalJogginColors = staticCompositionLocalOf { getTokens(AppTheme.FOREST, dark = false) }

// ── Theme entry point ─────────────────────────────────────────────────────────

@Composable
fun JogginTheme(
    appTheme: AppTheme = AppTheme.FOREST,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tokens = getTokens(appTheme, darkTheme)

    val materialColors = if (darkTheme) darkColors(
        primary = tokens.primary, primaryVariant = tokens.primaryDark,
        secondary = tokens.accent, background = tokens.background,
        surface = tokens.surface, error = tokens.error,
        onPrimary = tokens.onPrimary, onSecondary = Color.White,
        onBackground = tokens.onBackground, onSurface = tokens.onSurface, onError = Color.White
    ) else lightColors(
        primary = tokens.primary, primaryVariant = tokens.primaryDark,
        secondary = tokens.accent, background = tokens.background,
        surface = tokens.surface, error = tokens.error,
        onPrimary = tokens.onPrimary, onSecondary = Color.White,
        onBackground = tokens.onBackground, onSurface = tokens.onSurface, onError = Color.White
    )

    CompositionLocalProvider(LocalJogginColors provides tokens) {
        MaterialTheme(colors = materialColors, content = content)
    }
}

object JogginTheme {
    val colors: JogginColorTokens
        @Composable get() = LocalJogginColors.current
}

// ── Persistence helpers ───────────────────────────────────────────────────────

fun loadTheme(context: Context): AppTheme {
    val prefs = context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
    val name = prefs.getString("app_theme", AppTheme.FOREST.name) ?: AppTheme.FOREST.name
    return try { AppTheme.valueOf(name) } catch (_: Exception) { AppTheme.FOREST }
}

fun saveTheme(context: Context, theme: AppTheme) {
    context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
        .edit().putString("app_theme", theme.name).apply()
}
