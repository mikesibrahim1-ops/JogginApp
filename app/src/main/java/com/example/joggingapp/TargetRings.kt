package com.example.joggingapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.joggingapp.ui.theme.JogginTheme

/** How many target rings fit on the home card at ~390dp. */
private const val MAX_RINGS = 3

private const val PREF_SHOW_TARGET_RINGS = "show_target_rings"

/** Whether the exercise rings show on the home card (V1.71: the user can hide them). */
object TargetRingsPref {
    private var state: androidx.compose.runtime.MutableState<Boolean>? = null

    fun shown(context: android.content.Context): Boolean = stateFor(context).value

    fun set(context: android.content.Context, on: Boolean) {
        stateFor(context).value = on
        context.getSharedPreferences("jog_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_SHOW_TARGET_RINGS, on).apply()
    }

    private fun stateFor(context: android.content.Context) = state ?: androidx.compose.runtime.mutableStateOf(
        context.getSharedPreferences("jog_prefs", android.content.Context.MODE_PRIVATE)
            .getBoolean(PREF_SHOW_TARGET_RINGS, true)
    ).also { state = it }
}

/**
 * Home-card row of progress rings for the user's Exercise Targets (no new data — reuses
 * [evaluateTargets]). Shows nothing when there are no targets. Tapping opens the full
 * Exercise Targets screen. [refreshKey] forces a reload (e.g. after that screen closes).
 */
@Composable
fun TargetRings(routes: List<SavedRoute>, refreshKey: Any?, onOpen: () -> Unit) {
    val context = LocalContext.current
    val S = LocalStrings.current
    val progress = remember(routes, refreshKey) {
        evaluateTargets(ExerciseTargetStorage.loadTargets(context), routes, ExerciseTargetStorage.loadRepLog(context))
            .sortedBy { it.target.period.ordinal }   // today first, then week, then month
            .take(MAX_RINGS)
    }
    if (progress.isEmpty()) return

    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        progress.forEach { p -> TargetRing(p, S, Modifier.weight(1f)) }
    }
}

@Composable
private fun TargetRing(p: TargetProgress, S: AppStrings, modifier: Modifier) {
    val c = JogginTheme.colors
    val isDistance = p.target.type.isDistance
    val fmt = { v: Float -> if (isDistance) String.format("%.1f", v) else v.toInt().toString() }
    val value = "${fmt(p.current)}/${fmt(p.goal)}${if (isDistance) " ${S.km}" else ""}"
    val label = "${exerciseTypeLabel(S, p.target.type)} · ${periodLabel(S, p.target.period)}"
    // Dark themes keep the light theme's primary and success, which vanish on the dark card.
    val ringColor = when {
        !MaterialTheme.colors.isLight -> c.accent
        p.isMet -> c.success
        else -> c.primary
    }
    // Track tinted from the text colour so the empty ring reads in light AND dark mode
    // (a c.primary tint vanished on the dark-mode card).
    val trackColor = c.onBackground.copy(alpha = 0.18f)

    Column(modifier = modifier.semantics { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(52.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 6.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(trackColor, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (p.fraction > 0f) drawArc(ringColor, -90f, 360f * p.fraction, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Text(if (p.isMet) "✓" else "${(p.fraction * 100).toInt()}%", fontSize = 12.sp,
                fontWeight = FontWeight.Bold, color = c.onBackground)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, fontSize = 10.sp, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
    }
}
