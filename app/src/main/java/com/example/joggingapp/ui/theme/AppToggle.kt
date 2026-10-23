package com.example.joggingapp.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.delay

/**
 * App-wide standard on/off toggle.
 *
 * Visual contract (consistent throughout the app's themes):
 *  - ON  → solid green ([JogginColorTokens.success]), knob to the right.
 *  - OFF → a fixed neutral grey (NOT the theme `divider`, which is tinted per-theme and
 *          blended into some backgrounds — e.g. the Forest theme's divider reads as green).
 *  - A thin border is always drawn so the pill stands out even when the surrounding
 *    background is a closely-blended colour.
 *
 * Behaviour: on every tap a short status label ([onLabel] / [offLabel]) appears in a small
 * FLOATING box (a Popup, so it never expands the toggle's row or overlaps neighbouring
 * controls), styled in the app theme, and auto-dismisses after ~3 seconds.
 */

// Neutral off-state grey, theme-independent so it never blends into a themed background.
private val ToggleOffGrey = Color(0xFF9AA0A6)
// Thin boundary so the pill is visible against closely-blended backgrounds.
private val ToggleBorder = Color(0x33000000)

@Composable
fun AppToggle(
    checked: Boolean,
    onToggle: () -> Unit,
    onLabel: String,
    offLabel: String,
    modifier: Modifier = Modifier,
    contentDescriptionText: String? = null,
) {
    val c = JogginTheme.colors
    // The flash label: shown for ~3s after each tap, then hides. `flashKey` bumps on every
    // tap so re-tapping restarts the 3s window; `flashText` holds the message to show.
    var flashKey by remember { mutableStateOf(0) }
    var flashVisible by remember { mutableStateOf(false) }
    var flashText by remember { mutableStateOf("") }

    LaunchedEffect(flashKey) {
        if (flashKey == 0) return@LaunchedEffect
        flashVisible = true
        delay(3000)
        flashVisible = false
    }

    Box {
        Box(
            modifier = modifier
                .size(44.dp, 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (checked) c.success else ToggleOffGrey)
                .border(1.dp, ToggleBorder, RoundedCornerShape(12.dp))
                .clickable {
                    val next = !checked
                    flashText = if (next) onLabel else offLabel
                    flashKey += 1
                    onToggle()
                }
                .then(
                    if (contentDescriptionText != null)
                        Modifier.semantics { contentDescription = contentDescriptionText }
                    else Modifier
                ),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(modifier = Modifier.size(20.dp).padding(2.dp).clip(CircleShape).background(Color.White))
        }

        // Floating status label — rendered in its own Popup window so it floats above the UI
        // without affecting layout (no pushing/overlap of neighbouring controls). Anchored
        // just below the toggle, centred on it. Rounded corners at a ~3% radius per request.
        if (flashVisible) {
            Popup(popupPositionProvider = belowCenterProvider) {
                AnimatedVisibility(visible = flashVisible, enter = fadeIn(), exit = fadeOut()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 3))
                            .background(c.primary)
                            .border(1.dp, ToggleBorder, RoundedCornerShape(percent = 3))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(flashText, fontSize = 11.sp, color = c.onPrimary)
                    }
                }
            }
        }
    }
}

/**
 * Positions the popup horizontally centred on the anchor (the toggle) and just below it,
 * with a small gap. Keeps the floating label visually tied to the toggle.
 */
private val belowCenterProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: androidx.compose.ui.unit.IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: androidx.compose.ui.unit.IntSize
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val y = anchorBounds.bottom + 8
        // Clamp horizontally so the floating box never spills off-screen.
        val clampedX = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        return IntOffset(clampedX, y)
    }
}
