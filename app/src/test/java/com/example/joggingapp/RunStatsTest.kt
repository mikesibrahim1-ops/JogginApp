package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class RunStatsTest {

    // 1 km of latitude ≈ 1 / 111.195 degrees (haversine, R = 6371 km).
    private val degPerKm = 1.0 / 111.19492664455873

    /** A point [km] north of the origin at [sec] seconds. */
    private fun p(km: Double, sec: Long) = Point(51.0 + km * degPerKm, 0.0, sec * 1000L, 0f)
    private val pause = Point(Double.NaN, Double.NaN, 0L, 0f)

    private fun route(points: List<Point>, ts: Long = 0L, type: Int = 1, avgMs: Float = 3f,
                      steps: Int = 0, types: List<Int>? = null) =
        SavedRoute("r$ts", points, avgMs, 0f, 0f, 0f, ts, type, steps, types)

    private val utc: Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.UK)
        .apply { firstDayOfWeek = Calendar.MONDAY }

    // ── fastestSplitMs ──────────────────────────────────────────────────────

    @Test fun `split is null when route is shorter than target`() {
        assertNull(fastestSplitMs(listOf(p(0.0, 0), p(0.5, 150)), 1.0))
    }

    @Test fun `even pace gives exact 1k split`() {
        // 5 min/km for 3 km, a point every 100 m.
        val pts = (0..30).map { i -> p(i * 0.1, i * 30L) }
        val ms = fastestSplitMs(pts, 1.0)!!
        assertEquals(300_000.0, ms.toDouble(), 1_000.0)
    }

    @Test fun `finds the fastest km inside a longer route`() {
        // km 0-1 at 6:00, km 1-2 at 4:00, km 2-3 at 6:00 (points every 100 m).
        val pts = ArrayList<Point>()
        var t = 0L
        for (i in 0..30) {
            pts += p(i * 0.1, t)
            t += if (i in 10 until 20) 24 else 36
        }
        val ms = fastestSplitMs(pts, 1.0)!!
        assertEquals(240_000.0, ms.toDouble(), 1_000.0)
    }

    @Test fun `no distance is counted across a pause break`() {
        // Two 0.4 km halves with a pause and a 5 km GPS jump between them: only 0.8 km moved.
        val pts = listOf(p(0.0, 0), p(0.4, 120), pause, p(5.4, 600), p(5.8, 720))
        assertNull(fastestSplitMs(pts, 1.0))
    }

    @Test fun `implausibly fast split from a GPS glitch is ignored`() {
        // 1 km in 10 s = 360 km/h.
        assertNull(fastestSplitMs(listOf(p(0.0, 0), p(1.0, 10)), 1.0))
    }

    // ── movingDurationSec ───────────────────────────────────────────────────

    @Test fun `moving duration excludes paused time`() {
        val pts = listOf(p(0.0, 0), p(0.5, 300), pause, p(0.5, 900), p(1.0, 1200))
        assertEquals(600L, movingDurationSec(pts))
    }

    // ── routeTypes / personalRecords ────────────────────────────────────────

    @Test fun `fast untyped walk counts as a run`() {
        assertEquals(setOf(1), routeTypes(route(emptyList(), type = 0, avgMs = 2.5f)))   // 9 km/h
        assertEquals(setOf(0), routeTypes(route(emptyList(), type = 0, avgMs = 1.2f)))   // 4.3 km/h
    }

    @Test fun `records pick the best route per metric for the type`() {
        val slow = route((0..20).map { i -> p(i * 0.1, i * 36L) }, ts = 1, steps = 3000)   // 2 km @ 6:00
        val fast = route((0..10).map { i -> p(i * 0.1, i * 24L) }, ts = 2, steps = 1500)   // 1 km @ 4:00
        val bike = route((0..50).map { i -> p(i * 0.1, i * 12L) }, ts = 3, type = 2)       // cycling
        val rec = personalRecords(listOf(slow, fast, bike), type = 1)
        assertEquals(2L, rec.fastest1kMs!!.timestamp)
        assertEquals(1L, rec.longestKm!!.timestamp)
        assertEquals(2.0, rec.longestKm!!.value, 0.01)
        assertEquals(3000, rec.mostSteps!!.value)
        assertNull(rec.fastest5kMs)   // no run reaches 5 km; the bike route doesn't count
    }

    @Test fun `records are empty when no route matches`() {
        val rec = personalRecords(emptyList(), type = 0)
        assertNull(rec.fastest1kMs); assertNull(rec.longestKm); assertNull(rec.mostSteps)
    }

    // ── trends ──────────────────────────────────────────────────────────────

    private fun utcMs(y: Int, m: Int, d: Int, h: Int = 12): Long =
        (utc.clone() as Calendar).apply { clear(); set(y, m - 1, d, h, 0, 0) }.timeInMillis

    @Test fun `weekly buckets, week and month totals`() {
        val now = utcMs(2026, 10, 29)                          // Thursday
        val oneKm = listOf(p(0.0, 0), p(1.0, 360))
        val routes = listOf(
            route(oneKm, ts = utcMs(2026, 10, 27)),            // this week (Tue)
            route(oneKm, ts = utcMs(2026, 10, 26)),            // this week (Mon)
            route(oneKm, ts = utcMs(2026, 10, 25)),            // last week (Sun)
            route(oneKm, ts = utcMs(2026, 9, 30))              // older, September
        )
        val t = trends(routes, now, utc)
        assertEquals(8, t.weeklyKm.size)
        assertEquals(2.0, t.thisWeekKm, 0.01)
        assertEquals(1.0, t.weeklyKm[6], 0.01)
        assertEquals(3.0, t.thisMonthKm, 0.01)
        assertTrue(t.weeklyKm.sum() >= 4.0 - 0.01)
    }

    @Test fun `current streak survives until today ends`() {
        val now = utcMs(2026, 10, 29)
        val routes = listOf(27, 28).map { d -> route(emptyList(), ts = utcMs(2026, 10, d)) }
        val t = trends(routes, now, utc)
        assertEquals(2, t.currentStreakDays)      // yesterday + day before, today not over
        assertEquals(2, t.longestStreakDays)
    }

    @Test fun `streak resets after a missed day`() {
        val now = utcMs(2026, 10, 29)
        val routes = listOf(20, 21, 22, 23, 27).map { d -> route(emptyList(), ts = utcMs(2026, 10, d)) }
        val t = trends(routes, now, utc)
        assertEquals(0, t.currentStreakDays)
        assertEquals(4, t.longestStreakDays)
    }
}
