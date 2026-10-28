package com.example.joggingapp.ui.theme

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [explainPopupOffset] — the above-vs-below positioning logic used by the
 * AppToggle floating explanation popup. No Compose runtime needed.
 */
class ToggleExplainPositionTest {

    private val gap = 8

    @Test
    fun ampleRoomBelow_placesBelowAnchor() {
        // Toggle near the top; plenty of room beneath it.
        val anchor = IntRect(left = 100, top = 50, right = 144, bottom = 74)
        val window = IntSize(width = 1080, height = 1920)
        val content = IntSize(width = 200, height = 60)

        val offset = explainPopupOffset(anchor, window, content, gap)

        assertEquals(anchor.bottom + gap, offset.y)
    }

    @Test
    fun noRoomBelow_flipsAboveAnchorAndStaysOnScreen() {
        // Toggle sitting right at the window bottom: popup can't fit below, so flip above.
        val anchor = IntRect(left = 100, top = 1880, right = 144, bottom = 1904)
        val window = IntSize(width = 1080, height = 1920)
        val content = IntSize(width = 200, height = 60)

        val offset = explainPopupOffset(anchor, window, content, gap)

        assertEquals(anchor.top - gap - content.height, offset.y)
        assertTrue("y must stay on screen", offset.y >= 0)
    }

    @Test
    fun flipAboveClampsToZeroWhenNoRoomEitherWay() {
        // Short window where neither below nor a full above-gap fits; y clamps to 0.
        val anchor = IntRect(left = 100, top = 10, right = 144, bottom = 90)
        val window = IntSize(width = 1080, height = 100)
        val content = IntSize(width = 200, height = 60)

        val offset = explainPopupOffset(anchor, window, content, gap)

        assertEquals(0, offset.y)
    }

    @Test
    fun horizontalCentering_onAnchor() {
        val anchor = IntRect(left = 500, top = 50, right = 544, bottom = 74)
        val window = IntSize(width = 1080, height = 1920)
        val content = IntSize(width = 200, height = 60)

        val offset = explainPopupOffset(anchor, window, content, gap)

        // Centre: left + (width - contentWidth)/2 = 500 + (44 - 200)/2 = 500 - 78 = 422
        assertEquals(422, offset.x)
    }

    @Test
    fun horizontalClampLeftToZero() {
        // Anchor hard against the left edge; centring would go negative, so clamp to 0.
        val anchor = IntRect(left = 0, top = 50, right = 44, bottom = 74)
        val window = IntSize(width = 1080, height = 1920)
        val content = IntSize(width = 200, height = 60)

        val offset = explainPopupOffset(anchor, window, content, gap)

        assertEquals(0, offset.x)
    }

    @Test
    fun horizontalClampRightToWindowEdge() {
        // Anchor hard against the right edge; clamp so the box stays fully on screen.
        val window = IntSize(width = 1080, height = 1920)
        val content = IntSize(width = 200, height = 60)
        val anchor = IntRect(left = 1036, top = 50, right = 1080, bottom = 74)

        val offset = explainPopupOffset(anchor, window, content, gap)

        assertEquals(window.width - content.width, offset.x)
    }
}
