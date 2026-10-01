package com.example.joggingapp

/**
 * Central Buddy Link tuning constants. Kept in one place so timings/limits can be
 * adjusted without hunting through the code. See .kiro/specs/buddy-link/.
 */
object BuddyConstants {

    /** How often the sharing device broadcasts its location (~5 minutes). Req 6.1. */
    const val BROADCAST_INTERVAL_MS: Long = 5 * 60 * 1000L

    /** A location fix older than this is treated as stale / not-live. Req 5.8. */
    const val STALE_MS: Long = 15 * 60 * 1000L

    /** Max age for stored location + trail data before it is purged. Req 8.1. */
    const val RETENTION_DAYS: Int = 7
    const val RETENTION_MS: Long = RETENTION_DAYS * 24L * 60L * 60L * 1000L

    /** Invite links expire this long after generation. Req 3.5b. */
    const val INVITE_EXPIRY_DAYS: Int = 7
    const val INVITE_EXPIRY_MS: Long = INVITE_EXPIRY_DAYS * 24L * 60L * 60L * 1000L

    /** A re-requested link may raise at most this many alerts per 24h. Req 3.8. */
    const val REQUEST_ALERT_MAX_PER_24H: Int = 2
    const val REQUEST_ALERT_WINDOW_MS: Long = 24L * 60L * 60L * 1000L

    /**
     * Minimum spacing between successive on-demand "request location now" calls to the
     * SAME buddy (client-side rate-limit, Req 6.1b / G6). Prevents a viewer from
     * spamming a broadcaster with location pushes. 2 minutes balances "fresh enough"
     * against nuisance.
     */
    const val ON_DEMAND_REQUEST_COOLDOWN_MS: Long = 2 * 60 * 1000L

    /**
     * Minimum spacing between stored trail breadcrumb points, so the trail stays
     * sparse (not a dense track) to minimise data. Req 5b.2. Defaults to the
     * broadcast interval — one point per broadcast.
     */
    const val TRAIL_MIN_SPACING_MS: Long = BROADCAST_INTERVAL_MS

    /** Invite link scheme/host used by the deep link. Req 2.4, 3.1. */
    const val INVITE_SCHEME = "joggin"
    const val INVITE_PATH_PREFIX = "buddy/v1"
    // Firebase Hosting fallback page (free Spark plan; no GitHub account needed).
    // Served from the `joggin-a69a7` project → firebase-hosting/public/buddy/index.html.
    const val INVITE_HTTPS_FALLBACK = "https://joggin-a69a7.web.app/buddy"
    const val INVITE_HTTPS_HOST = "joggin-a69a7.web.app"
}
