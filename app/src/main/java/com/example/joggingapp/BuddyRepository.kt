package com.example.joggingapp

import kotlinx.coroutines.flow.Flow

/**
 * The Buddy Link backend contract. ALL feature code (ViewModel, service, UI) depends
 * on this interface — never on Firebase directly — so the backend can be swapped
 * (e.g. self-hosted) later without touching feature logic. See
 * .kiro/specs/buddy-link/design.md §2, §2.1.
 *
 * Observation methods return cold [Flow]s that emit on backend changes. Action
 * methods are `suspend` and return a [BuddyResult] rather than throwing, so callers
 * can surface non-blocking status (Req 10) instead of crashing.
 */
interface BuddyRepository {

    // ── Availability ──────────────────────────────────────────────────────────

    /** Emits whether the backend is currently reachable (drives Req 10 status UI). */
    fun observeAvailability(): Flow<Boolean>

    // ── Identity / session ──────────────────────────────────────────────────────

    /**
     * Ensures a backend session exists for the given app-owned App ID (e.g. anonymous
     * sign-in mapped to this ID) and that a user document exists. Called at first
     * enable. Idempotent.
     */
    suspend fun ensureSession(appId: String): BuddyResult<Unit>

    /** Updates this user's profile/presence fields (name, photo ref, toggle, gps). */
    suspend fun updateMyUser(
        displayName: String? = null,
        photoRef: String? = null,
        buddyEnabled: Boolean? = null,
        gpsOn: Boolean? = null,
        sharing: Boolean? = null,
        fcmToken: String? = null
    ): BuddyResult<Unit>

    /**
     * Fetches the current FCM registration token and stores it on my user doc so a
     * future server push (Cloud Function on `links` writes, design §12) can reach this
     * device. No-op / unavailable when the backend or messaging is not configured.
     * Kept here (not in main code) so the messaging API stays inside the gated backend.
     */
    suspend fun refreshFcmToken(): BuddyResult<Unit>

    // ── Linking ───────────────────────────────────────────────────────────────

    /**
     * Initiates a link request to [targetAppId] (from opening an invite link).
     * If the target is offline this still succeeds and the link is left PENDING
     * (Req 3.5a). Fails only for nonexistent IDs, blocks, or duplicates.
     */
    suspend fun requestLink(targetAppId: String): BuddyResult<Unit>

    /** Accepts a pending incoming link. */
    suspend fun acceptLink(linkId: String): BuddyResult<Unit>

    /** Declines a pending incoming link. */
    suspend fun declineLink(linkId: String): BuddyResult<Unit>

    /** Removes / unlinks an existing buddy (both directions). Req 3b, 4.5. */
    suspend fun removeLink(linkId: String): BuddyResult<Unit>

    /** Live view of my accepted buddies (with their user + latest location). */
    fun observeBuddies(): Flow<List<BuddyView>>

    /** Live view of incoming pending link requests. Req 3b.3. */
    fun observePendingRequests(): Flow<List<BuddyLink>>

    /** Observe a single buddy's recent breadcrumb trail (Req 5b). */
    fun observeBuddyTrail(buddyAppId: String): Flow<List<BuddyTrailPoint>>

    /** Who can currently see me (for the eye indicator, Req 11). */
    fun observeWatchers(): Flow<List<BuddyUser>>

    // ── Blocking ────────────────────────────────────────────────────────────────

    /** Blocks a user: unlink, purge shared data, prevent re-link. Req 12. */
    suspend fun blockUser(appId: String): BuddyResult<Unit>

    /** Removes a block. Req 12.5. */
    suspend fun unblockUser(appId: String): BuddyResult<Unit>

    /** Live view of blocked users. */
    fun observeBlocked(): Flow<List<String>>

    // ── Location ──────────────────────────────────────────────────────────────

    /** Writes my latest fix and appends a sparse trail point. Req 6.1, 5b. */
    suspend fun writeMyLocation(lat: Double, lon: Double, atMs: Long): BuddyResult<Unit>

    /** Ask a buddy for a fresh fix now (on-demand). Req 6.1b. */
    suspend fun requestBuddyLocation(buddyAppId: String): BuddyResult<Unit>

    /** Emits when a buddy has requested MY location (so the service can push a fix). */
    fun observeLocationRequests(): Flow<List<String>>

    // ── Data lifecycle ──────────────────────────────────────────────────────────

    /** Deletes all of my Buddy Link data (user doc, links, location, trail). Req 8.4. */
    suspend fun deleteAllMyData(): BuddyResult<Unit>
}

/**
 * A minimal result type so the repository never throws into the UI. Failures carry a
 * reason the UI can map to a localized, non-blocking message (Req 10).
 */
sealed class BuddyResult<out T> {
    data class Success<T>(val value: T) : BuddyResult<T>()
    data class Failure(val reason: Reason, val message: String? = null) : BuddyResult<Nothing>()

    enum class Reason {
        BACKEND_UNAVAILABLE,   // offline / can't reach backend (Req 10)
        NOT_FOUND,             // e.g. nonexistent App ID (Req 3.5)
        BLOCKED,               // action refused because of a block (Req 12)
        DUPLICATE,             // link already exists (Req 3.6)
        PERMISSION,            // missing auth/permission
        UNKNOWN
    }

    val isSuccess: Boolean get() = this is Success
}
