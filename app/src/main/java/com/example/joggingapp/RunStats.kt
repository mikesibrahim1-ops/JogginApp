package com.example.joggingapp

import java.util.Calendar

/**
 * Pure personal-records + trends calculations for the Records / Trends tabs of the
 * Activity History 📊 panel. Android-free and deterministic ([nowMs] and [cal] injected)
 * so it is JVM-unit-tested in RunStatsTest.
 */

/** A record value and the route that set it ([timestamp] = that route's start time). */
data class RecordEntry<T>(val value: T, val timestamp: Long)

data class PersonalRecords(
    val fastest1kMs: RecordEntry<Long>?,
    val fastest5kMs: RecordEntry<Long>?,
    val longestKm: RecordEntry<Double>?,
    val longestDurationSec: RecordEntry<Long>?,
    val mostSteps: RecordEntry<Int>?
)

data class Trends(
    /** Distance per calendar week, oldest first; the last entry is the current week. */
    val weeklyKm: List<Double>,
    val thisWeekKm: Double,
    val thisMonthKm: Double,
    val currentStreakDays: Int,
    val longestStreakDays: Int
)

/** Splits implying a faster speed than this are GPS glitches, not records. */
private const val MAX_PLAUSIBLE_SPLIT_KMH = 70.0

/**
 * The activity types a route counts as. Blended routes count for each of their types;
 * an untyped walk-mode route faster than [WALK_RUN_THRESHOLD_KMH] counts as a run (the same
 * inference the history cards use).
 */
fun routeTypes(r: SavedRoute): Set<Int> {
    val types = (r.activityTypes ?: listOf(r.activityType)).toSet()
    return if (types == setOf(0) && r.avgSpeed * 3.6f > WALK_RUN_THRESHOLD_KMH) setOf(1) else types
}

/**
 * Fastest time (ms) to cover [targetKm] anywhere within [points], or null if the route is
 * shorter than that. NaN pause-break sentinels contribute no distance; time across a pause
 * still counts, which only makes such a window slower (never a false record).
 */
fun fastestSplitMs(points: List<Point>, targetKm: Double): Long? {
    // One pass: keep valid points + cumulative distance, adding no distance across a pause.
    val valid = ArrayList<Point>(points.size)
    val cumList = ArrayList<Double>(points.size)
    var pausedSinceLast = false
    for (p in points) {
        if (p.lat.isNaN() || p.lon.isNaN()) { pausedSinceLast = true; continue }
        val add = if (valid.isEmpty() || pausedSinceLast) 0.0 else haversineKm(valid.last(), p)
        cumList.add((cumList.lastOrNull() ?: 0.0) + add)
        valid.add(p)
        pausedSinceLast = false
    }
    if (valid.size < 2) return null
    val cum = cumList.toDoubleArray()
    if (cum.last() < targetKm) return null
    var best: Long? = null
    var i = 0
    for (j in 1 until valid.size) {
        // Shrink from the left while the window [i+1, j] still covers the target.
        while (i + 1 < j && cum[j] - cum[i + 1] >= targetKm) i++
        if (cum[j] - cum[i] < targetKm) continue
        val ms = valid[j].timeMs - valid[i].timeMs
        if (ms <= 0) continue
        val kmh = targetKm / (ms / 3_600_000.0)
        if (kmh > MAX_PLAUSIBLE_SPLIT_KMH) continue
        if (best == null || ms < best) best = ms
    }
    return best
}

/** Moving duration in seconds: the sum of time spans between pause breaks. */
fun movingDurationSec(points: List<Point>): Long {
    var total = 0L
    var segStart: Point? = null
    var last: Point? = null
    for (p in points) {
        if (p.lat.isNaN()) {
            if (segStart != null && last != null) total += last.timeMs - segStart.timeMs
            segStart = null; last = null
        } else {
            if (segStart == null) segStart = p
            last = p
        }
    }
    if (segStart != null && last != null) total += last.timeMs - segStart.timeMs
    return total / 1000L
}

/** Personal records over the routes that count as [type] (0 = walk, 1 = run, 2 = cycle). */
fun personalRecords(routes: List<SavedRoute>, type: Int): PersonalRecords {
    val typed = routes.filter { type in routeTypes(it) }
    fun <T : Comparable<T>> best(selector: (SavedRoute) -> T?, lowerIsBetter: Boolean): RecordEntry<T>? {
        val scored = typed.mapNotNull { r -> selector(r)?.let { RecordEntry(it, r.timestamp) } }
        return if (lowerIsBetter) scored.minByOrNull { it.value } else scored.maxByOrNull { it.value }
    }
    return PersonalRecords(
        fastest1kMs = best({ fastestSplitMs(it.points, 1.0) }, lowerIsBetter = true),
        fastest5kMs = best({ fastestSplitMs(it.points, 5.0) }, lowerIsBetter = true),
        longestKm = best({ calcDistanceKm(it.points).takeIf { d -> d > 0.0 } }, lowerIsBetter = false),
        longestDurationSec = best({ movingDurationSec(it.points).takeIf { s -> s > 0L } }, lowerIsBetter = false),
        mostSteps = best({ it.steps.takeIf { s -> s > 0 } }, lowerIsBetter = false)
    )
}

/** Local-day key (days since epoch in [cal]'s time zone) for a timestamp. */
private fun dayKey(ms: Long, cal: Calendar): Long {
    val c = cal.clone() as Calendar
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    return (c.timeInMillis + c.timeZone.getOffset(c.timeInMillis)) / 86_400_000L
}

/** Start (ms) of the calendar week containing [ms], honouring [cal]'s firstDayOfWeek. */
private fun weekStart(ms: Long, cal: Calendar): Long {
    val c = cal.clone() as Calendar
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    while (c.get(Calendar.DAY_OF_WEEK) != c.firstDayOfWeek) c.add(Calendar.DAY_OF_MONTH, -1)
    return c.timeInMillis
}

/**
 * Activity trends across ALL activity types: the last [weeks] calendar weeks of distance,
 * this week / this month totals, and the current + longest active-day streaks. The current
 * streak still counts if the last active day was yesterday (today isn't over yet).
 */
fun trends(routes: List<SavedRoute>, nowMs: Long, cal: Calendar, weeks: Int = 8): Trends {
    val thisWeek = weekStart(nowMs, cal)
    val starts = (weeks - 1 downTo 0).map { back ->
        val c = cal.clone() as Calendar
        c.timeInMillis = thisWeek
        c.add(Calendar.WEEK_OF_YEAR, -back)
        c.timeInMillis
    }
    val weekly = DoubleArray(weeks)
    val monthCal = (cal.clone() as Calendar).apply { timeInMillis = nowMs }
    var monthKm = 0.0
    for (r in routes) {
        val km = calcDistanceKm(r.points)
        val ws = weekStart(r.timestamp, cal)
        val idx = starts.indexOf(ws)
        if (idx >= 0) weekly[idx] += km
        val rc = (cal.clone() as Calendar).apply { timeInMillis = r.timestamp }
        if (rc.get(Calendar.YEAR) == monthCal.get(Calendar.YEAR) &&
            rc.get(Calendar.MONTH) == monthCal.get(Calendar.MONTH) && r.timestamp <= nowMs) monthKm += km
    }

    val days = routes.map { dayKey(it.timestamp, cal) }.toSortedSet().toList()
    var longest = 0; var run = 0; var prev: Long? = null
    for (d in days) {
        run = if (prev != null && d == prev + 1) run + 1 else 1
        if (run > longest) longest = run
        prev = d
    }
    val today = dayKey(nowMs, cal)
    val daySet = days.toHashSet()
    var current = 0
    var cursor = if (today in daySet) today else today - 1
    while (cursor in daySet) { current++; cursor-- }

    return Trends(
        weeklyKm = weekly.toList(),
        thisWeekKm = weekly.last(),
        thisMonthKm = monthKm,
        currentStreakDays = current,
        longestStreakDays = longest
    )
}
