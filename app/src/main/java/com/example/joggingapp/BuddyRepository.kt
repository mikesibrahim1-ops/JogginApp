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

    /** Updates this user's profile/presence fields (name, photo ref, toggle, gps, share mode). */
    suspend fun updateMyUser(
        displayName: String? = null,
        photoRef: String? = null,
        buddyEnabled: Boolean? = null,
        gpsOn: Boolean? = null,
        sharing: Boolean? = null,
        fcmToken: String? = null,
        shareMode: BuddyShareMode? = null
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
     *
     * [fromName] is the sender's current displayName, denormalized onto the link doc
     * (`links/{linkId}.fromName`) so the recipient can show "<name> wants to buddy up"
     * before the sender's user doc becomes readable (which only happens post-accept).
     */
    suspend fun requestLink(targetAppId: String, fromName: String? = null): BuddyResult<Unit>

    /**
     * Called when THIS device opens + accepts an invite link whose embedded id is
     * [fromAppId] (the inviter). Since V1.49 a link can never be born accepted (rules):
     *  - if a PENDING link exists with me as side `b` (the inviter already requested
     *    me), it is moved to accepted → [InviteOutcome.CONNECTED];
     *  - if the link is already accepted → [InviteOutcome.CONNECTED] (no write);
     *  - otherwise a PENDING request is sent to the inviter via [requestLink] with
     *    [fromName] → [InviteOutcome.REQUEST_SENT] (the inviter then accepts).
     * Fails NOT_FOUND for an unknown inviter id, UNKNOWN if `fromAppId == me`.
     */
    suspend fun acceptInvite(fromAppId: String, fromName: String? = null): BuddyResult<InviteOutcome>

    /**
     * True when the backend still needs a session to be (re)established before it can
     * be used (e.g. the first anonymous sign-in failed). Drives the launch-time
     * auto-retry in [BuddyLinkController.start]. Backends with no session concept
     * return false.
     */
    fun needsSession(): Boolean

    /** Accepts a pending incoming link. Only the recipient (side `b`) may accept. */
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

    /**
     * Blocks a user by recording them in my blocked set. Reversible: the accepted link is
     * PRESERVED, so [unblockUser] reinstates the buddy via observeBuddies. While blocked,
     * sensitive paths stay denied server-side by the rules' mutual `!hasBlocked` gate (no
     * link teardown). Req 12.
     */
    suspend fun blockUser(appId: String): BuddyResult<Unit>

    /** Removes a block. Req 12.5. */
    suspend fun unblockUser(appId: String): BuddyResult<Unit>

    /** Live view of blocked users. */
    fun observeBlocked(): Flow<List<String>>

    // ── Location ──────────────────────────────────────────────────────────────

    /**
     * Writes my latest fix (the circle, always) and — only in [BuddyShareMode.ROUTE] —
     * appends a sparse trail point (design §5). The [mode] defaults to PING (fail-safe:
     * circle only, never leak a trail for an omitted mode). Req 6.1, 5b.
     */
    suspend fun writeMyLocation(
        lat: Double, lon: Double, atMs: Long,
        mode: BuddyShareMode = BuddyShareMode.PING
    ): BuddyResult<Unit>

    /** Ask a buddy for a fresh fix now (on-demand). Req 6.1b. */
    suspend fun requestBuddyLocation(buddyAppId: String): BuddyResult<Unit>

    /** Emits when a buddy has requested MY location (so the service can push a fix). */
    fun observeLocationRequests(): Flow<List<String>>

    // ── Live Cheer (S1) ───────────────────────────────────────────────────────

    /**
     * Send a one-tap Live Cheer (S1) to an accepted buddy. Writes an append-only
     * `cheers/{buddyAppId}/inbox/{autoId}` event doc the recipient observes. Gated
     * server-side by the cheers create rule (accepted + mutually-unblocked buddy only).
     * [fromName] is the sender's displayName, denormalized onto the doc (null → "").
     */
    suspend fun sendCheer(buddyAppId: String, fromName: String?): BuddyResult<Unit>

    /**
     * Emits cheers addressed to ME as they arrive (each carries the sender's App ID +
     * name + send time). The implementation deletes each consumed cheer after emitting
     * (self-clean), so the inbox never accumulates; TTL is the backstop.
     */
    fun observeCheers(): Flow<List<BuddyCheer>>

    // ── Live-tracking share link for non-app users (S2) ──────────────────────────

    /**
     * Creates a new public share link (S2) for a non-app viewer. Writes the owner-only
     * control docs (shareOwners/{token} + shares/{token}) and persists the active token
     * to jog_prefs, returning the [ShareLink] (token + expiry). The share stays live for
     * [BuddyConstants.SHARE_LIFETIME_MS]. [label] is an optional viewer-facing caption
     * (trimmed, capped). Fails NOT_FOUND with no session; BACKEND_UNAVAILABLE on NoOp.
     */
    suspend fun createShareLink(label: String?): BuddyResult<ShareLink>

    /**
     * Mirrors my latest fix into the active public share doc shares/{token} (S2). A
     * merge that updates only lat/lon/at (+ the UNCHANGED [expiresAtMs], re-sent so the
     * write-once rule's equality holds) and optional [label]; never writes `revoked`.
     * [expiresAtMs] MUST be the verbatim stored expiry, never recomputed. Non-fatal.
     */
    suspend fun writeShareFix(
        token: String, lat: Double, lon: Double, atMs: Long, expiresAtMs: Long, label: String?
    ): BuddyResult<Unit>

    /** Revokes the active public share link (S2): marks it revoked, clears prefs, cleans up. */
    suspend fun revokeShareLink(token: String): BuddyResult<Unit>

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
