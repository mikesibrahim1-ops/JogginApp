package com.example.joggingapp

/** A message in the "run together" handshake between two buddies. */
enum class RunSignalKind(val wire: String) {
    INVITE("invite"), ACCEPT("accept"), DECLINE("decline"), CANCEL("cancel");

    companion object {
        fun fromWire(value: String?): RunSignalKind? = values().firstOrNull { it.wire == value }
    }
}

/** One received handshake message. [activityType] uses the app's 0 walk / 1 run / 2 cycle. */
data class RunSignal(
    val id: String,
    val fromAppId: String,
    val fromName: String,
    val kind: RunSignalKind,
    val inviteId: String,
    val activityType: Int,
    val at: Long
)

/** The invite I sent and am waiting on. */
data class OutgoingRunInvite(
    val inviteId: String,
    val buddyAppId: String,
    val buddyName: String,
    val activityType: Int
)

/** A one-shot request for the UI to start tracking. */
data class RunStart(val activityType: Int, val at: Long)

/** A short result line for the inviter. */
data class RunNotice(val kind: Kind, val buddyName: String) {
    enum class Kind { DECLINED, NO_ANSWER, SEND_FAILED }
}

/** Pure handshake decisions. Android/Firebase-free → JVM-testable. */
object RunTogether {

    enum class Action { IGNORE, SHOW_INVITE, ACCEPT_CROSSED_INVITE, OUTGOING_ACCEPTED, OUTGOING_DECLINED, INCOMING_CANCELLED }

    fun decide(
        signal: RunSignal,
        outgoing: OutgoingRunInvite?,
        incoming: RunSignal?,
        nowMs: Long
    ): Action = when (signal.kind) {
        RunSignalKind.INVITE -> when {
            // Sent while one of us was offline: nobody is still waiting on it.
            nowMs - signal.at > BuddyConstants.RUN_INVITE_TTL_MS -> Action.IGNORE
            // We both tapped: each has already agreed, so go straight to the run.
            outgoing?.buddyAppId == signal.fromAppId -> Action.ACCEPT_CROSSED_INVITE
            else -> Action.SHOW_INVITE
        }
        RunSignalKind.ACCEPT ->
            if (outgoing?.inviteId == signal.inviteId) Action.OUTGOING_ACCEPTED else Action.IGNORE
        RunSignalKind.DECLINE ->
            if (outgoing?.inviteId == signal.inviteId) Action.OUTGOING_DECLINED else Action.IGNORE
        RunSignalKind.CANCEL ->
            if (incoming?.inviteId == signal.inviteId) Action.INCOMING_CANCELLED else Action.IGNORE
    }
}
