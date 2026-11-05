package com.example.joggingapp

import kotlinx.coroutines.flow.MutableStateFlow

/** Whether a workout is running on this device, as far as Buddy Link sharing cares. */
enum class LiveActivityState { IDLE, TRACKING, PAUSED }

/**
 * Process-wide workout state. Written by [ForegroundLocationService] (the source of truth,
 * so notification pause/resume/end are covered) and read by [BuddyLocationService] to
 * switch between the idle 5-minute broadcast and live sharing.
 */
object LiveActivityBus {
    val state = MutableStateFlow(LiveActivityState.IDLE)
}

/** Pure decisions for live sharing during a workout. Android/Firebase-free → JVM-testable. */
object BuddyLiveRoute {

    data class WriteDecision(val writeFix: Boolean, val appendTrail: Boolean)

    /**
     * Decide what to do with a live GPS fix. Small movements are skipped to limit Firestore
     * writes, but a heartbeat keeps a stationary buddy (e.g. paused for a drink) live.
     * [movedFromLastFixM]/[movedFromLastTrailM] are null when nothing has been written yet.
     */
    fun decide(
        state: LiveActivityState,
        mode: BuddyShareMode,
        movedFromLastFixM: Float?,
        movedFromLastTrailM: Float?,
        msSinceLastFix: Long
    ): WriteDecision {
        val fixDue = movedFromLastFixM == null ||
            movedFromLastFixM >= BuddyConstants.LIVE_MIN_MOVE_M ||
            msSinceLastFix >= BuddyConstants.LIVE_HEARTBEAT_MS
        val trailDue = shouldAppendTrailPoint(mode, state) &&
            (movedFromLastTrailM == null || movedFromLastTrailM >= BuddyConstants.LIVE_MIN_MOVE_M)
        return WriteDecision(writeFix = fixDue || trailDue, appendTrail = trailDue)
    }

    /**
     * The buddy's current route: the newest run of points with no gap longer than
     * [BuddyConstants.LIVE_ROUTE_GAP_MS], or empty once the newest point is older than that.
     * Keeps a drink stop in the same route while hiding an earlier, finished activity.
     */
    fun currentSegment(trail: List<BuddyTrailPoint>, nowMs: Long): List<BuddyTrailPoint> {
        val sorted = trail.sortedBy { it.at }
        val newest = sorted.lastOrNull() ?: return emptyList()
        if (nowMs - newest.at > BuddyConstants.LIVE_ROUTE_GAP_MS) return emptyList()
        var start = sorted.lastIndex
        while (start > 0 && sorted[start].at - sorted[start - 1].at <= BuddyConstants.LIVE_ROUTE_GAP_MS) start--
        return sorted.subList(start, sorted.size)
    }
}
