package com.example.joggingapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.joggingapp.ui.theme.JogginTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The three tabs of the Activity History 📊 panel. */
enum class StatsTab { RECENT, RECORDS, TRENDS }

/** Pill tab row for the 📊 panel. Selected = solid theme colour, others = light tint. */
@Composable
fun StatsTabRow(selected: StatsTab, onSelect: (StatsTab) -> Unit) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(StatsTab.RECENT to S.statsTabRecent, StatsTab.RECORDS to S.statsTabRecords,
            StatsTab.TRENDS to S.statsTabTrends).forEach { (tab, label) ->
            val on = tab == selected
            Text(label, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                color = if (on) c.onPrimary else c.onSurface, maxLines = 1,
                modifier = Modifier.clip(CircleShape)
                    .background(if (on) c.primary else c.primary.copy(alpha = 0.10f))
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

/** "1h 22m" / "48m" for long durations (fmtTime is mm:ss, which overflows past an hour). */
private fun fmtDurationHm(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Personal records for one activity type (Run / Walk / Cycle selector at the top). */
@Composable
fun RecordsTab(routes: List<SavedRoute>) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    var type by remember { mutableStateOf(1) }
    val rec = remember(routes, type) { personalRecords(routes, type) }
    val dateFmt = remember { SimpleDateFormat(DATE_FMT_DATE, Locale.getDefault()) }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 10.dp)) {
        listOf(1 to "🏃 ${S.run}", 0 to "🚶 ${S.walk}", 2 to "🚴 ${S.cycle}").forEach { (t, label) ->
            val on = t == type
            Text(label, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                color = if (on) c.onSurface else c.textSecondary, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (on) c.primary.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { type = t }
                    .padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
    val rows = listOfNotNull(
        rec.fastest1kMs?.let { Triple("⚡ ${S.recFastest1k}", fmtTime((it.value / 1000).toInt()), it.timestamp) },
        rec.fastest5kMs?.let { Triple("⚡ ${S.recFastest5k}", fmtTime((it.value / 1000).toInt()), it.timestamp) },
        rec.longestKm?.let { Triple("📍 ${S.recLongestDist}", String.format("%.2f %s", it.value, S.km), it.timestamp) },
        rec.longestDurationSec?.let { Triple("⏱ ${S.recLongestTime}", fmtDurationHm(it.value), it.timestamp) },
        rec.mostSteps?.let { Triple("👟 ${S.recMostSteps}", String.format(Locale.getDefault(), "%,d", it.value), it.timestamp) }
    )
    if (rows.isEmpty()) {
        Text(S.recNone, fontSize = 13.sp, color = c.textSecondary, modifier = Modifier.padding(vertical = 16.dp))
        return
    }
    rows.forEachIndexed { i, (label, value, ts) ->
        if (i > 0) Divider(color = c.divider.copy(alpha = 0.5f))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = c.onSurface, modifier = Modifier.weight(1f), maxLines = 1)
            Column(horizontalAlignment = Alignment.End) {
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onSurface)
                Text(dateFmt.format(Date(ts)), fontSize = 10.sp, color = c.textSecondary)
            }
        }
    }
}

/** Weekly distance bar chart + this-week / this-month / streak tiles (all activity types). */
@Composable
fun TrendsTab(routes: List<SavedRoute>) {
    val c = JogginTheme.colors
    val S = LocalStrings.current
    val cal = remember { Calendar.getInstance() }
    val t = remember(routes) { trends(routes, System.currentTimeMillis(), cal) }
    // Week-start labels for each bar, oldest first (matches t.weeklyKm).
    val weekLabels = remember(routes) {
        val fmt = SimpleDateFormat("d MMM", Locale.getDefault())
        val now = (cal.clone() as Calendar).apply {
            timeInMillis = System.currentTimeMillis()
            while (get(Calendar.DAY_OF_WEEK) != firstDayOfWeek) add(Calendar.DAY_OF_MONTH, -1)
        }
        (t.weeklyKm.size - 1 downTo 0).map { back ->
            fmt.format((now.clone() as Calendar).apply { add(Calendar.WEEK_OF_YEAR, -back) }.time)
        }
    }
    // Selected bar (tap to inspect); defaults to the current week.
    var sel by remember(routes) { mutableStateOf(t.weeklyKm.lastIndex) }
    val fmtKm = { km: Double -> String.format("%.1f %s", km, S.km) }

    Spacer(Modifier.height(10.dp))
    Text(S.trendWeeklyTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
    // Direct label for the selected bar (text tokens, not the bar colour).
    Text(S.trendWeekOf(weekLabels[sel], fmtKm(t.weeklyKm[sel])), fontSize = 12.sp, color = c.textSecondary,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

    val maxKm = (t.weeklyKm.maxOrNull() ?: 0.0).coerceAtLeast(0.1)
    val barColor = c.primary
    val dimColor = c.primary.copy(alpha = 0.55f)
    val gridColor = c.divider.copy(alpha = 0.6f)
    Canvas(
        modifier = Modifier.fillMaxWidth().height(120.dp)
            .semantics { contentDescription = t.weeklyKm.indices.joinToString { S.trendWeekOf(weekLabels[it], fmtKm(t.weeklyKm[it])) } }
            .pointerInput(t.weeklyKm) {
                detectTapGestures { pos ->
                    val slot = size.width / t.weeklyKm.size.toFloat()
                    sel = (pos.x / slot).toInt().coerceIn(0, t.weeklyKm.lastIndex)
                }
            }
    ) {
        val n = t.weeklyKm.size
        val slot = size.width / n
        val barW = (slot * 0.6f).coerceAtLeast(2f)   // thin bars: 60% of each week's slot
        val gap = slot - barW
        val radius = 4.dp.toPx()
        // Recessive baseline.
        drawLine(gridColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        t.weeklyKm.forEachIndexed { i, km ->
            val h = (km / maxKm * (size.height - radius)).toFloat()
            if (h <= 0f) return@forEachIndexed
            val left = i * slot + gap / 2
            // Rounded data-end only (top), square at the baseline.
            val path = Path().apply {
                addRoundRect(RoundRect(left, size.height - h, left + barW, size.height,
                    topLeftCornerRadius = CornerRadius(radius), topRightCornerRadius = CornerRadius(radius),
                    bottomLeftCornerRadius = CornerRadius.Zero, bottomRightCornerRadius = CornerRadius.Zero))
            }
            drawPath(path, if (i == sel) barColor else dimColor)
        }
    }
    // Sparse axis labels: first, middle and current week only (avoids collisions at ~390dp).
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(weekLabels.first(), fontSize = 10.sp, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text(weekLabels[weekLabels.size / 2], fontSize = 10.sp, color = c.textSecondary, modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(weekLabels.last(), fontSize = 10.sp, color = c.textSecondary, modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }

    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(S.trendThisWeek, fmtKm(t.thisWeekKm), Modifier.weight(1f))
        StatTile(S.trendThisMonth, fmtKm(t.thisMonthKm), Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("🔥 ${S.trendCurrentStreak}", S.trendDays(t.currentStreakDays), Modifier.weight(1f))
        StatTile("🏆 ${S.trendLongestStreak}", S.trendDays(t.longestStreakDays), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = JogginTheme.colors
    Column(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(c.primary.copy(alpha = 0.08f))
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        Text(label, fontSize = 11.sp, color = c.textSecondary, maxLines = 1)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onSurface, maxLines = 1)
    }
}
