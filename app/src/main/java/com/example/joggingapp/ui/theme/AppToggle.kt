package com.example.joggingapp.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Behaviour: on every tap a short status label ([onLabel] / [offLabel]) flashes next to
 * the toggle for ~3 seconds and then fades out, telling the user what the toggle now does.
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
    // The flash label: shown for ~3s after each tap, then fades. `flashKey` bumps on every
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

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(visible = flashVisible, enter = fadeIn(), exit = fadeOut()) {
            Text(flashText, fontSize = 11.sp, color = if (checked) c.success else c.textSecondary)
        }
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
    }
}
