package com.example.joggingapp

/**
 * Pure (Android/Firebase-free) decisions used by the Buddy Link repository and
 * controller (V1.49). Kept separate so they can be unit-tested on the JVM.
 */

/** Outcome of opening + accepting an invite deep link (see [BuddyRepository.acceptInvite]). */
enum class InviteOutcome {
    /** The inviter had already requested me; I accepted it, so we're now buddies. */
    CONNECTED,
    /** No acceptable request existed; a pending request was sent back to the inviter. */
    REQUEST_SENT
}

object BuddyLinkDecisions {

    /** What acceptInvite should do for the existing link doc (if any) of the pair. */
    enum class InviteAction {
        /** A pending link with me as side `b` exists → move it to accepted. */
        ACCEPT_EXISTING,
        /** Already accepted → nothing to write. */
        ALREADY_CONNECTED,
        /** Anything else (no doc, I'm side `a`, declined/removed) → send a request. */
        SEND_REQUEST
    }

    /**
     * Decide acceptInvite's action. [state]/[b] describe the existing link doc and are
     * null when it doesn't exist. Only side `b` may ever accept (matches the rules).
     */
    fun inviteAction(me: String, state: BuddyLinkState?, b: String?): InviteAction = when {
        state == BuddyLinkState.ACCEPTED -> InviteAction.ALREADY_CONNECTED
        canAccept(me, state, b) -> InviteAction.ACCEPT_EXISTING
        else -> InviteAction.SEND_REQUEST
    }

    /** Client-side guard mirroring the rules: only the recipient (`b`) accepts a pending link. */
    fun canAccept(me: String, state: BuddyLinkState?, b: String?): Boolean =
        state == BuddyLinkState.PENDING && b == me

    /** Map the Firestore `state` string to the enum (null when the doc doesn't exist). */
    fun parseState(raw: String?): BuddyLinkState? = when (raw) {
        null -> null
        "accepted" -> BuddyLinkState.ACCEPTED
        "declined" -> BuddyLinkState.DECLINED
        "removed" -> BuddyLinkState.REMOVED
        else -> BuddyLinkState.PENDING
    }
}

/**
 * Bounded backoff for re-establishing the Buddy Link backend session on launch when
 * the first anonymous sign-in failed (V1.49). Up to [MAX_ATTEMPTS] tries in total,
 * waiting [BASE_DELAY_MS], then 2×, between them.
 */
object BuddySessionRetry {
    const val MAX_ATTEMPTS = 3
    const val BASE_DELAY_MS = 2_000L

    /**
     * After attempt number [attempt] (1-based), should we try again? Only while it
     * failed, the backend still [sessionNeeded] (no sign-in yet — a NoOp backend never
     * does), and attempts remain.
     */
    fun shouldRetry(attempt: Int, succeeded: Boolean, sessionNeeded: Boolean): Boolean =
        !succeeded && sessionNeeded && attempt < MAX_ATTEMPTS

    /** Delay before the attempt that follows failed attempt [attempt] (1-based). */
    fun delayAfterAttemptMs(attempt: Int): Long =
        BASE_DELAY_MS * (1L shl (attempt.coerceAtLeast(1) - 1))
}
