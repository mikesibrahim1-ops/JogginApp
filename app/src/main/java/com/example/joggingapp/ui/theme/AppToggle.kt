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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
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
 * Behaviour: turning the toggle OFF→ON flashes a short message in a small FLOATING box (a
 * Popup, so it never expands the toggle's row or overlaps neighbouring controls) — the
 * plain-language [onExplanation] when supplied, otherwise the legacy [onLabel]. Turning it
 * ON→OFF shows nothing. The box floats just below the toggle, flips above when there's no
 * room below, and auto-dismisses after ~3 seconds.
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
    // Retained for backward compatibility with existing call sites. OFF taps are now silent
    // (nothing flashes on on->off), so this value is no longer displayed; kept to avoid an
    // out-of-scope API change to the shared component.
    @Suppress("UNUSED_PARAMETER") offLabel: String,
    onExplanation: String? = null,
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
                    // ON-only flash: on an off->on tap show the explanation (preferred) or
                    // the legacy onLabel; on an on->off tap show NOTHING (silent turn-off).
                    val msg = if (next) (onExplanation?.takeIf { it.isNotBlank() } ?: onLabel) else null
                    if (msg != null) {
                        flashText = msg
                        flashKey += 1
                    }
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
                            .widthIn(max = 240.dp)
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
 * Pure positioning logic for the floating explanation popup (no Compose runtime), so it can
 * be unit-tested. Horizontally centres the popup on the anchor (the toggle) and clamps it on
 * screen. Vertically it sits just below the anchor by [gapPx]; if there isn't enough room
 * below (it would spill past the window bottom) it flips ABOVE the anchor instead, clamped to
 * stay on screen.
 */
internal fun explainPopupOffset(
    anchorBounds: IntRect,
    windowSize: IntSize,
    popupContentSize: IntSize,
    gapPx: Int
): IntOffset {
    val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
    val clampedX = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
    val fitsBelow = anchorBounds.bottom + popupContentSize.height + gapPx <= windowSize.height
    val y = if (fitsBelow) {
        anchorBounds.bottom + gapPx
    } else {
        (anchorBounds.top - gapPx - popupContentSize.height).coerceAtLeast(0)
    }
    return IntOffset(clampedX, y)
}

/**
 * Positions the popup horizontally centred on the anchor (the toggle), below it by default and
 * flipped above when there's no room below. Keeps the floating label visually tied to the toggle.
 */
private val belowCenterProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = explainPopupOffset(anchorBounds, windowSize, popupContentSize, gapPx = 8)
}
