package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Pure JVM tests for the Exercise Targets logic: [periodStart] boundaries,
 * [evaluateTargets] progress against the rep log (manual types) and the GPS route
 * history (distance types), and the [TargetProgress] fraction/isMet helpers.
 * No Android/Robolectric needed — all inputs are plain data classes.
 */
class ExerciseTargetsTest {

    // Fixed "now": Wednesday 15 July 2026, 12:00 local.
    private fun now(): Long = Calendar.getInstance().apply {
        set(2026, Calendar.JULY, 15, 12, 0, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun daysBefore(now: Long, days: Int): Long = now - days * 86_400_000L
    private fun hoursBefore(now: Long, hours: Int): Long = now - hours * 3_600_000L

    private fun rep(type: ExerciseType, count: Int, at: Long) =
        RepLogEntry(type = type, count = count, timestamp = at)

    // Build a route whose two points are ~1 km apart (0.009° latitude ≈ 1.0 km).
    private fun routeKm(mode: Int, at: Long, km: Double): SavedRoute {
        val degPerKm = 1.0 / 111.0
        val p1 = Point(0.0, 0.0, at, 1f)
        val p2 = Point(km * degPerKm, 0.0, at + 1000L, 1f)
        return SavedRoute(
            id = "r$at", points = listOf(p1, p2), avgSpeed = 1f, minSpeed = 1f,
            maxSpeed = 1f, calories = 0f, timestamp = at, activityType = mode
        )
    }

    // ── periodStart ────────────────────────────────────────────────────────────

    @Test
    fun periodStart_daily_isMidnightToday() {
        val n = now()
        val start = periodStart(TargetPeriod.DAILY, n)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
        assertTrue(start <= n)
    }

    @Test
    fun periodStart_monthly_isFirstOfMonth() {
        val n = now()
        val start = periodStart(TargetPeriod.MONTHLY, n)
        val cal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JULY, cal.get(Calendar.MONTH))
    }

    @Test
    fun periodStart_weekly_isAtOrBeforeNow_withinSevenDays() {
        val n = now()
        val start = periodStart(TargetPeriod.WEEKLY, n)
        assertTrue(start <= n)
        assertTrue(n - start < 7 * 86_400_000L)
    }

    // ── Manual (rep) targets ─────────────────────────────────────────────────────

    @Test
    fun manual_daily_sumsOnlyTodaysReps() {
        val n = now()
        val target = ExerciseTarget(type = ExerciseType.PUSHUPS, period = TargetPeriod.DAILY, amount = 50f)
        val log = listOf(
            rep(ExerciseType.PUSHUPS, 20, hoursBefore(n, 2)),   // today
            rep(ExerciseType.PUSHUPS, 15, hoursBefore(n, 5)),   // today
            rep(ExerciseType.PUSHUPS, 100, daysBefore(n, 3)),   // earlier this week, NOT today
        )
        val tp = evaluateTargets(listOf(target), emptyList(), log, n).single()
        assertEquals(35f, tp.current, 0.001f)
        assertEquals(50f, tp.goal, 0.001f)
        assertFalse(tp.isMet)
        assertEquals(0.7f, tp.fraction, 0.001f)
    }

    @Test
    fun manual_weekly_includesEarlierThisWeek() {
        val n = now()
        val target = ExerciseTarget(type = ExerciseType.SITUPS, period = TargetPeriod.WEEKLY, amount = 100f)
        // 1 day ago is within the same week (week starts <7 days back from Wed).
        val log = listOf(
            rep(ExerciseType.SITUPS, 60, hoursBefore(n, 3)),
            rep(ExerciseType.SITUPS, 60, daysBefore(n, 1)),
        )
        val tp = evaluateTargets(listOf(target), emptyList(), log, n).single()
        assertEquals(120f, tp.current, 0.001f)
        assertTrue(tp.isMet)
        assertEquals(1.0f, tp.fraction, 0.001f) // clamped to 1.0 even though 120 > 100
    }

    @Test
    fun manual_ignoresOtherExerciseTypes() {
        val n = now()
        val target = ExerciseTarget(type = ExerciseType.SQUATS, period = TargetPeriod.DAILY, amount = 30f)
        val log = listOf(
            rep(ExerciseType.SQUATS, 10, hoursBefore(n, 1)),
            rep(ExerciseType.PUSHUPS, 99, hoursBefore(n, 1)),  // different type → ignored
        )
        val tp = evaluateTargets(listOf(target), emptyList(), log, n).single()
        assertEquals(10f, tp.current, 0.001f)
    }

    // ── Distance targets (auto from routes) ──────────────────────────────────────

    @Test
    fun distance_daily_sumsTodaysMatchingRoutes() {
        val n = now()
        val target = ExerciseTarget(type = ExerciseType.RUNNING, period = TargetPeriod.DAILY, amount = 5f)
        val routes = listOf(
            routeKm(1, hoursBefore(n, 2), 3.0),   // run today
            routeKm(1, hoursBefore(n, 4), 1.0),   // run today
            routeKm(0, hoursBefore(n, 1), 10.0),  // walk today → wrong type, ignored
            routeKm(1, daysBefore(n, 2), 20.0),   // run but earlier → outside day
        )
        val tp = evaluateTargets(listOf(target), routes, emptyList(), n).single()
        // ~4 km from the two short runs; haversine is approximate, allow tolerance.
        assertEquals(4.0f, tp.current, 0.1f)
        assertFalse(tp.isMet)
    }

    @Test
    fun distance_walkingSeparateFromRunning() {
        val n = now()
        val walkTarget = ExerciseTarget(type = ExerciseType.WALKING, period = TargetPeriod.DAILY, amount = 2f)
        val routes = listOf(
            routeKm(0, hoursBefore(n, 1), 2.5),   // walk
            routeKm(1, hoursBefore(n, 1), 9.0),   // run → ignored for walking target
        )
        val tp = evaluateTargets(listOf(walkTarget), routes, emptyList(), n).single()
        assertEquals(2.5f, tp.current, 0.1f)
        assertTrue(tp.isMet)
    }

    @Test
    fun distance_blendedRoute_countsForEachContainedType() {
        val n = now()
        val target = ExerciseTarget(type = ExerciseType.RUNNING, period = TargetPeriod.DAILY, amount = 1f)
        // A blended walk+run route: activityTypes contains both 0 and 1.
        val blended = routeKm(0, hoursBefore(n, 1), 3.0).copy(activityTypes = listOf(0, 1))
        val tp = evaluateTargets(listOf(target), listOf(blended), emptyList(), n).single()
        assertTrue(tp.current > 0f) // the run target picks up the blended route
    }

    // ── TargetProgress helpers ───────────────────────────────────────────────────

    @Test
    fun fraction_zeroGoal_isZeroNotCrash() {
        val tp = TargetProgress(ExerciseTarget(amount = 0f), current = 10f, goal = 0f)
        assertEquals(0f, tp.fraction, 0.001f)
        assertFalse(tp.isMet)
    }
}
