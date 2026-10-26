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

    /** What requestLink should do with the existing link doc (if any) when re-sending. */
    enum class RequestLinkAction {
        /** No doc exists → create a fresh pending request. */
        CREATE_NEW,
        /** A live pending/accepted doc exists → bump the 24h counter, keep state. */
        BUMP_EXISTING,
        /** A declined/removed dead-end exists → delete it, then create a fresh pending request. */
        RECREATE_PENDING
    }

    /**
     * Decide requestLink's action from the existing link doc's [state] (null when the
     * doc doesn't exist). A declined/removed dead-end can't be reset in place under the
     * deployed rules, so it is deleted and recreated; a pending/accepted link just bumps
     * the re-request counter (Req 3.8) and keeps its state.
     */
    fun requestLinkAction(state: BuddyLinkState?): RequestLinkAction = when (state) {
        null -> RequestLinkAction.CREATE_NEW
        BuddyLinkState.DECLINED, BuddyLinkState.REMOVED -> RequestLinkAction.RECREATE_PENDING
        else -> RequestLinkAction.BUMP_EXISTING
    }

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
 * Pure classifier for a tap on a buddy's name in the Buddy Link list (V1.6x
 * tap-to-locate). Decides whether tapping should centre the map on the buddy or surface
 * a specific reason it can't. Kept here (Android/Firebase-free) so it is unit-testable.
 */
object BuddyLocate {

    /** What a tap on a buddy's name should do. */
    enum class Outcome {
        /** Mutually sharing + a fresh fix -> centre the map on them. */
        LOCATE,

        /** Their Buddy Link master toggle is off on their side. */
        REASON_BUDDY_LINK_OFF,

        /**
         * Buddy Link on, but not currently sharing. NOTE: this is NOT a claim they
         * deliberately turned sharing off. `sharing` is derived as `enabled && gpsOn` and
         * is only ever written from the foreground, so `sharing==false && buddyEnabled==true`
         * cannot be distinguished from a genuine opt-out — it also covers "their GPS is off"
         * or "their app isn't open yet". User-facing wording MUST stay app-state-neutral.
         */
        REASON_SHARING_OFF,

        /** Sharing on, but no fix yet or the last fix is older than STALE_MS. */
        REASON_STALE_OR_NO_FIX
    }

    /**
     * Classify a tap on [view]. The LOCATE case mirrors [BuddyView.isLiveNow] (same
     * sharing + fix-freshness conditions) so the map-focus path — which re-checks
     * isLiveNow — and this decision never disagree on a reachable buddy. Order matters:
     * buddyEnabled is the outermost gate, then sharing, then fix freshness.
     *
     * NOTE: [decide] gates on `buddyEnabled` while [BuddyView.isLiveNow] does not, so the
     * two are NOT a strict biconditional over hand-built states. In production they agree
     * because `disable()` always writes `buddyEnabled=false` together with `sharing=false`,
     * so a `{buddyEnabled=false, sharing=true}` state never occurs.
     *
     * PURITY: pure only when [nowMs] is injected. The default argument reads the wall clock
     * (same convention as [BuddyView.isLiveNow]), so the production call site
     * `BuddyLocate.decide(view)` is NOT referentially transparent; tests always inject nowMs.
     */
    fun decide(view: BuddyView, nowMs: Long = System.currentTimeMillis()): Outcome {
        val user = view.user
        if (!user.buddyEnabled) return Outcome.REASON_BUDDY_LINK_OFF
        if (!user.sharing) return Outcome.REASON_SHARING_OFF
        val loc = view.location
        if (loc == null || (nowMs - loc.at) > BuddyConstants.STALE_MS) return Outcome.REASON_STALE_OR_NO_FIX
        return Outcome.LOCATE
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
