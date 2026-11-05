package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuddyLiveRouteTest {

    private val minute = 60_000L
    private fun point(at: Long) = BuddyTrailPoint(0.0, 0.0, at)

    private fun decide(
        state: LiveActivityState = LiveActivityState.TRACKING,
        mode: BuddyShareMode = BuddyShareMode.ROUTE,
        fixMoved: Float? = 50f,
        trailMoved: Float? = 50f,
        sinceFix: Long = 10_000L
    ) = BuddyLiveRoute.decide(state, mode, fixMoved, trailMoved, sinceFix)

    @Test
    fun firstFix_isAlwaysWritten() {
        val d = decide(fixMoved = null, trailMoved = null)
        assertTrue(d.writeFix)
        assertTrue(d.appendTrail)
    }

    @Test
    fun movingInRouteMode_writesFixAndTrail() {
        val d = decide()
        assertTrue(d.writeFix)
        assertTrue(d.appendTrail)
    }

    @Test
    fun movingInPingMode_neverWritesTrail() {
        val d = decide(mode = BuddyShareMode.PING)
        assertTrue(d.writeFix)
        assertFalse(d.appendTrail)
    }

    @Test
    fun stationaryBeforeHeartbeat_writesNothing() {
        val d = decide(fixMoved = 2f, trailMoved = 2f, sinceFix = 10_000L)
        assertFalse(d.writeFix)
        assertFalse(d.appendTrail)
    }

    @Test
    fun stationaryAtHeartbeat_refreshesFixWithoutTrail() {
        val d = decide(fixMoved = 2f, trailMoved = 2f, sinceFix = BuddyConstants.LIVE_HEARTBEAT_MS)
        assertTrue(d.writeFix)
        assertFalse(d.appendTrail)
    }

    @Test
    fun paused_movingUpdatesDotButNotRoute() {
        val d = decide(state = LiveActivityState.PAUSED)
        assertTrue(d.writeFix)
        assertFalse(d.appendTrail)
    }

    @Test
    fun trailGate_requiresRouteModeAndTracking() {
        assertTrue(shouldAppendTrailPoint(BuddyShareMode.ROUTE, LiveActivityState.TRACKING))
        assertFalse(shouldAppendTrailPoint(BuddyShareMode.ROUTE, LiveActivityState.PAUSED))
        assertFalse(shouldAppendTrailPoint(BuddyShareMode.ROUTE, LiveActivityState.IDLE))
        assertFalse(shouldAppendTrailPoint(BuddyShareMode.PING, LiveActivityState.TRACKING))
    }

    @Test
    fun segment_emptyTrail_isEmpty() {
        assertTrue(BuddyLiveRoute.currentSegment(emptyList(), 100 * minute).isEmpty())
    }

    @Test
    fun segment_drinkStopStaysInOneRoute() {
        val trail = listOf(point(0), point(minute), point(21 * minute), point(22 * minute))
        assertEquals(4, BuddyLiveRoute.currentSegment(trail, 23 * minute).size)
    }

    @Test
    fun segment_dropsEarlierFinishedActivity() {
        val trail = listOf(point(0), point(minute), point(40 * minute), point(41 * minute))
        val segment = BuddyLiveRoute.currentSegment(trail, 42 * minute)
        assertEquals(listOf(40 * minute, 41 * minute), segment.map { it.at })
    }

    @Test
    fun segment_hiddenOnceNewestPointIsOld() {
        val trail = listOf(point(0), point(minute))
        assertTrue(BuddyLiveRoute.currentSegment(trail, 32 * minute).isEmpty())
    }

    @Test
    fun segment_sortsOutOfOrderPoints() {
        val trail = listOf(point(2 * minute), point(0), point(minute))
        assertEquals(listOf(0L, minute, 2 * minute), BuddyLiveRoute.currentSegment(trail, 3 * minute).map { it.at })
    }
}
