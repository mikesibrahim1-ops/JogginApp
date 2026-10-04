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
    SUNRISE("Sunrise Energy", "🌅"),
    FOREST("Forest Trail", "🌲"),
    MIDNIGHT("Midnight Pulse", "💜"),
    OCEAN("Ocean Breeze", "🌊"),
}

// ── Theme 1: Sunrise Energy 🌅 ───────────────────────────────────────────────

private val SunriseLight = JogginColorTokens(
    // Main theme colour on both pane (secondary) and start button (primary); avatar uses primaryDark (~3 tones darker).
    primary = Color(0xFFFF6B35), primaryDark = Color(0xFFC24A1E),
    secondary = Color(0xFFFF6B35), accent = Color(0xFF00B4D8),
    background = Color(0xFFF8F9FA), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFF0F2F5),
    onPrimary = Color.White, onSecondary = Color.White,
    onBackground = Color(0xFF212529), onSurface = Color(0xFF212529), textSecondary = Color(0xFF6C757D),
    success = Color(0xFF4CAF50), error = Color(0xFFE53935),
    running = Color(0xFFFF6B35), cycling = Color(0xFF00B4D8), walking = Color(0xFF7CB342),
    blend = Color(0xFF9C27B0),
    divider = Color(0xFFE9ECEF), gold = Color(0xFFFFD700),
    heroGradient = Brush.linearGradient(listOf(Color(0xFFFF6B35), Color(0xFF00B4D8)))
)
private val SunriseDark = SunriseLight.copy(
    // (secondary override removed so the swapped bright pane colour applies in dark too)
    background = Color(0xFF0D1B2A), surface = Color(0xFF1B263B), surfaceVariant = Color(0xFF243447),
    onBackground = Color(0xFFF8F9FA), onSurface = Color(0xFFF8F9FA), textSecondary = Color(0xFFADB5BD),
    divider = Color(0xFF2D3E52)
)

// ── Theme 2: Forest Trail 🌲 ─────────────────────────────────────────────────

private val ForestLight = JogginColorTokens(
    // Main theme colour on both pane (secondary) and start button (primary); avatar uses primaryDark (~3 tones darker).
    primary = Color(0xFF2D6A4F), primaryDark = Color(0xFF1E4A37),
    secondary = Color(0xFF2D6A4F), accent = Color(0xFFE9C46A),
    background = Color(0xFFF1FAEE), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE8F5E3),
    onPrimary = Color.White, onSecondary = Color.White,
    onBackground = Color(0xFF1B2F1B), onSurface = Color(0xFF1B2F1B), textSecondary = Color(0xFF5F7A61),
    success = Color(0xFF40916C), error = Color(0xFFD62828),
    running = Color(0xFFE76F51), cycling = Color(0xFF3A7CA5), walking = Color(0xFF8AB17D),
    blend = Color(0xFF9C27B0),
    divider = Color(0xFFD4E6D0), gold = Color(0xFFE9C46A),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF2D6A4F), Color(0xFFE9C46A)))
)
private val ForestDark = ForestLight.copy(
    background = Color(0xFF0A1F14), surface = Color(0xFF1B3A2D), surfaceVariant = Color(0xFF254D3A),
    onBackground = Color(0xFFE8F5E3), onSurface = Color(0xFFE8F5E3), textSecondary = Color(0xFF8AB17D),
    divider = Color(0xFF2B4F3B)
)

// ── Theme 3: Midnight Pulse 💜 ───────────────────────────────────────────────

private val MidnightLight = JogginColorTokens(
    // Main theme colour on both pane (secondary) and start button (primary); avatar uses primaryDark (~3 tones darker).
    primary = Color(0xFF7B2FBE), primaryDark = Color(0xFF5A2290),
    secondary = Color(0xFF7B2FBE), accent = Color(0xFF00F5D4),
    background = Color(0xFFF5F3FF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEDE8FF),
    onPrimary = Color.White, onSecondary = Color.White,
    onBackground = Color(0xFF1E1B2E), onSurface = Color(0xFF1E1B2E), textSecondary = Color(0xFF6E6A8A),
    success = Color(0xFF00E676), error = Color(0xFFFF1744),
    running = Color(0xFFFF4D8D), cycling = Color(0xFF00F5D4), walking = Color(0xFFA3E635),
    blend = Color(0xFF9C27B0),
    divider = Color(0xFFE0DBEF), gold = Color(0xFFFFD54F),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF7B2FBE), Color(0xFF00F5D4)))
)
private val MidnightDark = MidnightLight.copy(
    background = Color(0xFF0D0518), surface = Color(0xFF1A0B2E), surfaceVariant = Color(0xFF2A1548),
    onBackground = Color(0xFFF0EAFF), onSurface = Color(0xFFF0EAFF), textSecondary = Color(0xFF9E99B8),
    divider = Color(0xFF2E1B4A)
)

// ── Theme 4: Ocean Breeze 🌊 ─────────────────────────────────────────────────

private val OceanLight = JogginColorTokens(
    // Main theme colour on both pane (secondary) and start button (primary); avatar uses primaryDark (~3 tones darker).
    primary = Color(0xFF0077B6), primaryDark = Color(0xFF005684),
    secondary = Color(0xFF0077B6), accent = Color(0xFF90E0EF),
    background = Color(0xFFF0F9FF), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFE4F4FD),
    onPrimary = Color.White, onSecondary = Color.White,
    onBackground = Color(0xFF0A2540), onSurface = Color(0xFF0A2540), textSecondary = Color(0xFF5B7A99),
    success = Color(0xFF06D6A0), error = Color(0xFFEF476F),
    running = Color(0xFFFF6B6B), cycling = Color(0xFF2196F3), walking = Color(0xFF66BB6A),
    blend = Color(0xFF9C27B0),
    divider = Color(0xFFD4E8F7), gold = Color(0xFFFFD166),
    heroGradient = Brush.linearGradient(listOf(Color(0xFF0077B6), Color(0xFF90E0EF)))
)
private val OceanDark = OceanLight.copy(
    background = Color(0xFF011627), surface = Color(0xFF023E8A), surfaceVariant = Color(0xFF0A4F9E),
    onBackground = Color(0xFFE4F4FD), onSurface = Color(0xFFE4F4FD), textSecondary = Color(0xFF8AB4D4),
    divider = Color(0xFF0A4070)
)

// ── Theme resolver ────────────────────────────────────────────────────────────

fun getTokens(theme: AppTheme, dark: Boolean): JogginColorTokens = when (theme) {
    AppTheme.SUNRISE  -> if (dark) SunriseDark else SunriseLight
    AppTheme.FOREST   -> if (dark) ForestDark else ForestLight
    AppTheme.MIDNIGHT -> if (dark) MidnightDark else MidnightLight
    AppTheme.OCEAN    -> if (dark) OceanDark else OceanLight
}

fun getPreviewColors(theme: AppTheme): Triple<Color, Color, Color> = when (theme) {
    // Preview dots: main theme colour, the darker avatar shade (primaryDark), accent.
    AppTheme.SUNRISE  -> Triple(Color(0xFFFF6B35), Color(0xFFC24A1E), Color(0xFF00B4D8))
    AppTheme.FOREST   -> Triple(Color(0xFF2D6A4F), Color(0xFF1E4A37), Color(0xFFE9C46A))
    AppTheme.MIDNIGHT -> Triple(Color(0xFF7B2FBE), Color(0xFF5A2290), Color(0xFF00F5D4))
    AppTheme.OCEAN    -> Triple(Color(0xFF0077B6), Color(0xFF005684), Color(0xFF90E0EF))
}

// ── CompositionLocal ──────────────────────────────────────────────────────────

val LocalJogginColors = staticCompositionLocalOf { MidnightLight }

// ── Theme entry point ─────────────────────────────────────────────────────────

@Composable
fun JogginTheme(
    appTheme: AppTheme = AppTheme.MIDNIGHT,
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
    val name = prefs.getString("app_theme", AppTheme.MIDNIGHT.name) ?: AppTheme.MIDNIGHT.name
    return try { AppTheme.valueOf(name) } catch (_: Exception) { AppTheme.MIDNIGHT }
}

fun saveTheme(context: Context, theme: AppTheme) {
    context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
        .edit().putString("app_theme", theme.name).apply()
}
