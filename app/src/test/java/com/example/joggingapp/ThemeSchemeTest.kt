package com.example.joggingapp

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.example.joggingapp.ui.theme.AppTheme
import com.example.joggingapp.ui.theme.JogginColorTokens
import com.example.joggingapp.ui.theme.getPreviewColors
import com.example.joggingapp.ui.theme.getTokens
import com.example.joggingapp.ui.theme.loadTheme
import com.example.joggingapp.ui.theme.saveTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Unit coverage for the theme colour-scheme system: the activity-colour ramp stays on-hue
 * and distinguishable, defaults/fallbacks resolve to FOREST, preview dots derive from the
 * resolved palette, and text/surface contrast clears WCAG AA. Robolectric is required
 * because the ramp and contrast maths call android.graphics.Color.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ThemeSchemeTest {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun hueOf(color: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv[0]
    }

    private fun valueOf(color: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv[2]
    }

    private fun satOf(color: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        return hsv[1]
    }

    /** Circular hue distance in degrees (0..180). */
    private fun hueDist(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return if (d > 180f) 360f - d else d
    }

    /** WCAG relative luminance of an sRGB colour. */
    private fun luminance(color: Color): Double {
        fun lin(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val argb = color.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val hi = maxOf(la, lb)
        val lo = minOf(la, lb)
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun activityColors(t: JogginColorTokens) =
        listOf(t.running, t.cycling, t.walking, t.blend)

    // ── getTokens: activity ramp is on-hue + distinguishable ───────────────────

    @Test
    fun activityColours_shareThemeHue_andAreDistinguishable() {
        for (theme in AppTheme.values()) {
            for (dark in listOf(false, true)) {
                val t = getTokens(theme, dark)
                val baseHue = hueOf(t.primary)
                val shades = activityColors(t)

                // All four share the theme's hue family (within tolerance).
                for (shade in shades) {
                    assertTrue(
                        "$theme dark=$dark activity hue off-palette",
                        hueDist(hueOf(shade), baseHue) <= 12f
                    )
                }

                // Strictly increasing value (dark -> light) with a real gap per step.
                val values = shades.map { valueOf(it) }
                for (i in 1 until values.size) {
                    assertTrue(
                        "$theme dark=$dark steps not distinguishable at $i",
                        values[i] - values[i - 1] >= 0.10f
                    )
                }
            }
        }
    }

    @Test
    fun activityColours_identicalAcrossLightAndDark() {
        for (theme in AppTheme.values()) {
            val light = activityColors(getTokens(theme, dark = false))
            val dark = activityColors(getTokens(theme, dark = true))
            assertEquals("$theme activity colours differ across modes", light, dark)
        }
    }

    @Test
    fun hueRamp_highValueBase_keepsStepsDistinguishable() {
        // Sunrise primary is a bright orange; confirm the ramp's pinned floor keeps the
        // four activity shades from collapsing together (the headline border bug).
        val t = getTokens(AppTheme.SUNRISE, dark = false)
        val values = activityColors(t).map { valueOf(it) }
        for (i in 1 until values.size) {
            assertTrue("Sunrise ramp collapsed at step $i", values[i] - values[i - 1] >= 0.10f)
        }
        // Saturation eases down but never clamps to neon-zero.
        for (c in activityColors(t)) {
            assertTrue("Sunrise ramp washed out", satOf(c) >= 0.30f)
        }
    }

    // ── getPreviewColors derives from tokens ───────────────────────────────────

    @Test
    fun previewColours_matchResolvedTokens() {
        for (theme in AppTheme.values()) {
            val t = getTokens(theme, dark = false)
            val (p, pd, a) = getPreviewColors(theme)
            assertEquals("$theme preview dot 1", t.primary, p)
            assertEquals("$theme preview dot 2", t.primaryDark, pd)
            assertEquals("$theme preview dot 3", t.accent, a)
        }
    }

    // ── loadTheme defaults + fallback ──────────────────────────────────────────

    @Test
    fun loadTheme_noPref_defaultsToForest() {
        ctx.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        assertEquals(AppTheme.FOREST, loadTheme(ctx))
    }

    @Test
    fun loadTheme_validPref_returnsSavedValue() {
        saveTheme(ctx, AppTheme.OCEAN)
        assertEquals(AppTheme.OCEAN, loadTheme(ctx))
    }

    @Test
    fun loadTheme_garbagePref_fallsBackToForest() {
        ctx.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
            .edit().putString("app_theme", "NOT_A_REAL_THEME").apply()
        assertEquals(AppTheme.FOREST, loadTheme(ctx))
    }

    // ── contrast ───────────────────────────────────────────────────────────────

    @Test
    fun contrast_whiteOnPrimary_andOnSurface_clearAA() {
        for (theme in AppTheme.values()) {
            for (dark in listOf(false, true)) {
                val t = getTokens(theme, dark)
                assertTrue(
                    "$theme dark=$dark white-on-primary < 4.5",
                    contrastRatio(Color.White, t.primary) >= 4.5
                )
                assertTrue(
                    "$theme dark=$dark onSurface-on-surface < 4.5",
                    contrastRatio(t.onSurface, t.surface) >= 4.5
                )
            }
        }
    }
}
