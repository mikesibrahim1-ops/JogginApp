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
 * Pure (Android/Firebase-free) partition of the accepted-buddy list into the active
 * list and the Blocked group, driven by my blocked-appId set. The link is preserved
 * while a buddy is blocked (reversible block), so a blocked buddy still arrives as a
 * [BuddyView] from `observeBuddies()` and is merely suppressed from the active list
 * and surfaced — with its real display name — in the Blocked group. JVM-unit-testable.
 */
object BuddyListFilter {

    /** Buddies to show in the active list: accepted buddies whose appId I have NOT blocked. */
    fun activeBuddies(all: List<BuddyView>, blocked: Set<String>): List<BuddyView> =
        all.filterNot { it.user.appId in blocked }

    /** Buddies to show in the Blocked group: accepted buddies I HAVE blocked (carry name). */
    fun blockedBuddies(all: List<BuddyView>, blocked: Set<String>): List<BuddyView> =
        all.filter { it.user.appId in blocked }
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
 * Pure client-side Live Cheer (S1) rate-limit. Enforces a per-buddy cooldown and a
 * per-day cap, both keyed by the target buddy's App ID. Android/Firebase-free and
 * referentially transparent — `nowMs` is injected — so it is JVM-unit-testable exactly
 * like [BuddyLocate.decide] / [BuddyTransitions.canRequestLocation].
 *
 * Best-effort anti-spam, NOT a security boundary: Firestore rules cannot cheaply count
 * prior cheers without a Cloud Function, so this guard stops the common accidental-spam
 * case on the sending device only (see design §"Rate limiting").
 */
object CheerRateLimit {

    data class Decision(val allowed: Boolean, val reason: Reason)
    enum class Reason { OK, COOLDOWN, DAILY_CAP }

    /**
     * Decide whether a cheer to [buddyAppId] is allowed right now. Cooldown is checked
     * before the daily cap. [lastSentAt] maps a buddy's App ID → epoch-ms of the last
     * cheer sent to them; [sentTodayCount] maps a buddy's App ID → cheers sent in the
     * current day window. Both default to 0 for an absent buddy (first-ever cheer OK).
     */
    fun decide(
        lastSentAt: Map<String, Long>,
        sentTodayCount: Map<String, Int>,
        buddyAppId: String,
        nowMs: Long
    ): Decision {
        val last = lastSentAt[buddyAppId] ?: 0L
        if (nowMs - last < BuddyConstants.CHEER_COOLDOWN_MS)
            return Decision(false, Reason.COOLDOWN)
        if ((sentTodayCount[buddyAppId] ?: 0) >= BuddyConstants.CHEER_DAILY_CAP)
            return Decision(false, Reason.DAILY_CAP)
        return Decision(true, Reason.OK)
    }
}

/**
 * A live public share link (S2): a URL-safe token plus the epoch-ms it expires at.
 * Returned by [BuddyRepository.createShareLink]; referenced by [NoOpBuddyRepository]
 * and the Firebase impl. Kept here (Android/Firebase-free) so the main set compiles.
 */
data class ShareLink(val token: String, val expiresAtMs: Long)

/**
 * Pure validity decision for a public share link (S2). Mirrors the Firestore rule's
 * gate ordering so the client never offers to write/read a share the rule would deny.
 * Android/Firebase-free and referentially transparent ([nowMs] injected) → JVM-testable.
 */
object ShareLinkValidity {

    data class Decision(val readable: Boolean, val reason: Reason)
    enum class Reason { OK, NO_TOKEN, REVOKED, EXPIRED }

    /**
     * Decide whether a share is currently readable. Order: no token → NO_TOKEN,
     * revoked → REVOKED, then expiry. The boundary [nowMs] == [expiresAtMs] is EXPIRED,
     * mirroring the rule's strict `request.time < expiresAt`.
     */
    fun decide(hasToken: Boolean, expiresAtMs: Long, revoked: Boolean, nowMs: Long): Decision = when {
        !hasToken -> Decision(false, Reason.NO_TOKEN)
        revoked -> Decision(false, Reason.REVOKED)
        nowMs >= expiresAtMs -> Decision(false, Reason.EXPIRED)
        else -> Decision(true, Reason.OK)
    }
}

/**
 * Pure helpers for a share link's lifetime (S2). Android/Firebase-free → JVM-testable.
 */
object ShareLifetime {

    /** When a share created at [createdAtMs] expires. */
    fun expiresAt(createdAtMs: Long): Long = createdAtMs + BuddyConstants.SHARE_LIFETIME_MS

    /** Time left until [expiresAtMs], clamped so a stale share reports 0, never negative. */
    fun remainingMs(expiresAtMs: Long, nowMs: Long): Long = (expiresAtMs - nowMs).coerceAtLeast(0L)
}

/**
 * Generator for a public share token (S2). 16 random bytes URL-safe-Base64 encoded
 * (unpadded) → a 22-char `^[A-Za-z0-9_-]+$` token. Encoded by hand (no android.util.Base64
 * and no java.util.Base64, which is API 26+ vs minSdk 24) so it stays JVM-pure and
 * unit-testable without Robolectric.
 */
object ShareToken {

    // RFC 4648 §5 URL-safe Base64 alphabet (…+ → -, / → _). Encoding is done by hand
    // rather than via java.util.Base64 because that API is only available from API 26
    // while this module's minSdk is 24 (lint NewApi) — a hand-rolled table encoder is
    // equivalent, stays JVM-pure (no android.util.Base64 / Robolectric), and yields the
    // same 22-char unpadded token for 16 input bytes.
    private const val URL_SAFE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    fun generate(random: java.security.SecureRandom = java.security.SecureRandom()): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return encodeUrlSafeNoPadding(bytes)
    }

    /** URL-safe Base64 (no padding), pure Kotlin. */
    private fun encodeUrlSafeNoPadding(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 8 + 5) / 6)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 6) {
                bitsLeft -= 6
                out.append(URL_SAFE_ALPHABET[(buffer shr bitsLeft) and 0x3F])
            }
        }
        if (bitsLeft > 0) {
            out.append(URL_SAFE_ALPHABET[(buffer shl (6 - bitsLeft)) and 0x3F])
        }
        return out.toString()
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
