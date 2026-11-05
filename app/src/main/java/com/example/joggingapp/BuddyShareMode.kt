package com.example.joggingapp

/** Sharer-side location-sharing mode (design: Buddy Link Ping/Route). */
enum class BuddyShareMode(val wire: String) {
    /** Default. Broadcast the latest fix only (static circle); no trail is shared. */
    PING("ping"),
    /** Broadcast the latest fix AND the breadcrumb trail (dashed blue route on buddy's map). */
    ROUTE("route");

    companion object {
        val DEFAULT = PING
        /** Parse a persisted/Firestore wire string; unknown/absent -> DEFAULT (fail safe to PING). */
        fun fromWire(s: String?): BuddyShareMode =
            entries.firstOrNull { it.wire == s } ?: DEFAULT
    }
}

/**
 * Decide whether a broadcast should append a trail breadcrumb: only in ROUTE mode while
 * actually tracking, so the shared route is the workout itself (not idle or paused
 * movement). Pure; unit-tested in BuddyLiveRouteTest. The latest-fix write is separate.
 */
fun shouldAppendTrailPoint(mode: BuddyShareMode, state: LiveActivityState): Boolean =
    mode == BuddyShareMode.ROUTE && state == LiveActivityState.TRACKING
