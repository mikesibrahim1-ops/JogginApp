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
 * Decide whether a broadcast in [mode] should append a trail breadcrumb.
 * PING -> false (latest-fix circle only); ROUTE -> true (circle + trail).
 * Pure; unit-tested in BuddyShareModeTest. The latest-fix write is unconditional —
 * only the trail append is gated.
 */
fun shouldAppendTrailPoint(mode: BuddyShareMode): Boolean = mode == BuddyShareMode.ROUTE
