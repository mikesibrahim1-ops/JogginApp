package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunTogetherTest {

    private val now = 1_000_000_000L
    private val outgoing = OutgoingRunInvite("mine", "buddy", "Buddy", 1)

    private fun signal(kind: RunSignalKind, inviteId: String = "mine", at: Long = now, from: String = "buddy") =
        RunSignal("doc", from, "Buddy", kind, inviteId, 1, at)

    private fun decide(s: RunSignal, out: OutgoingRunInvite? = null, inc: RunSignal? = null) =
        RunTogether.decide(s, out, inc, now)

    @Test fun `fresh invite is shown`() =
        assertEquals(RunTogether.Action.SHOW_INVITE, decide(signal(RunSignalKind.INVITE, "theirs")))

    @Test fun `invite at the time limit is still shown`() {
        val s = signal(RunSignalKind.INVITE, "theirs", at = now - BuddyConstants.RUN_INVITE_TTL_MS)
        assertEquals(RunTogether.Action.SHOW_INVITE, decide(s))
    }

    @Test fun `expired invite is ignored`() {
        val s = signal(RunSignalKind.INVITE, "theirs", at = now - BuddyConstants.RUN_INVITE_TTL_MS - 1)
        assertEquals(RunTogether.Action.IGNORE, decide(s))
    }

    @Test fun `invites crossing between the same buddies accept each other`() =
        assertEquals(RunTogether.Action.ACCEPT_CROSSED_INVITE, decide(signal(RunSignalKind.INVITE, "theirs"), outgoing))

    @Test fun `invite from someone else while waiting is still shown`() {
        val s = signal(RunSignalKind.INVITE, "theirs", from = "other")
        assertEquals(RunTogether.Action.SHOW_INVITE, decide(s, outgoing))
    }

    @Test fun `accept and decline only count for my current invite`() {
        assertEquals(RunTogether.Action.OUTGOING_ACCEPTED, decide(signal(RunSignalKind.ACCEPT), outgoing))
        assertEquals(RunTogether.Action.OUTGOING_DECLINED, decide(signal(RunSignalKind.DECLINE), outgoing))
        assertEquals(RunTogether.Action.IGNORE, decide(signal(RunSignalKind.ACCEPT, "old"), outgoing))
        assertEquals(RunTogether.Action.IGNORE, decide(signal(RunSignalKind.DECLINE, "old"), outgoing))
        assertEquals(RunTogether.Action.IGNORE, decide(signal(RunSignalKind.ACCEPT)))
    }

    @Test fun `cancel only clears the invite it refers to`() {
        val incoming = signal(RunSignalKind.INVITE, "theirs")
        assertEquals(RunTogether.Action.INCOMING_CANCELLED, decide(signal(RunSignalKind.CANCEL, "theirs"), inc = incoming))
        assertEquals(RunTogether.Action.IGNORE, decide(signal(RunSignalKind.CANCEL, "old"), inc = incoming))
    }

    @Test fun `wire values round trip and unknown values are rejected`() {
        RunSignalKind.values().forEach { assertEquals(it, RunSignalKind.fromWire(it.wire)) }
        assertNull(RunSignalKind.fromWire("nope"))
        assertNull(RunSignalKind.fromWire(null))
    }
}
