package com.example.joggingapp

/**
 * Buddy Link data models — plain Kotlin, no Firebase types.
 *
 * These are the app-side representations used by the UI, the ViewModel, and the
 * BuddyRepository interface. The Firebase implementation maps its documents to/from
 * these types, so the rest of the app never depends on Firebase classes. See
 * .kiro/specs/buddy-link/design.md §4 for the backing Firestore model.
 */

/** State of a link between two users. */
enum class BuddyLinkState { PENDING, ACCEPTED, DECLINED, REMOVED }

/**
 * A Buddy Link user (self or a buddy). `appId` is the stable app-owned unique ID.
 * `sharing` is the derived mutual-on flag (toggle ON && GPS on && recent fix).
 */
data class BuddyUser(
    val appId: String,
    val displayName: String = "",
    val photoRef: String? = null,   // resolves to a profile picture if available
    val buddyEnabled: Boolean = false,
    val gpsOn: Boolean = false,
    val sharing: Boolean = false,
    val updatedAt: Long = 0L
)

/**
 * A link between two users. `linkId` is deterministic (sorted pair of appIds) so the
 * same two people cannot create duplicate links. `a` is the requester, `b` the
 * recipient. `reqCount`/`reqWindowStart` back the "max 2 alerts / 24h" rule.
 */
data class BuddyLink(
    val linkId: String,
    val a: String,                  // requester appId
    val b: String,                  // recipient appId
    val state: BuddyLinkState = BuddyLinkState.PENDING,
    val reqCount: Int = 0,
    val reqWindowStart: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

/** A buddy's latest known location fix. `at` is the fix time (drives staleness). */
data class BuddyLocation(
    val lat: Double,
    val lon: Double,
    val at: Long
)

/** A single sparse breadcrumb point in a buddy's recent trail (7-day retention). */
data class BuddyTrailPoint(
    val lat: Double,
    val lon: Double,
    val at: Long
)

/**
 * Convenience view combining a buddy's user record with their latest location, used
 * by the UI to decide whether to show a live avatar. See BuddyConstants.STALE_MS.
 */
data class BuddyView(
    val user: BuddyUser,
    val location: BuddyLocation? = null,
    val link: BuddyLink? = null
) {
    /** True only if the buddy is mutually sharing AND their fix is recent (not stale). */
    fun isLiveNow(nowMs: Long = System.currentTimeMillis()): Boolean {
        val loc = location ?: return false
        return user.sharing && (nowMs - loc.at) <= BuddyConstants.STALE_MS
    }
}
