package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the V1.49 Buddy Link decisions: the acceptInvite action (only
 * side `b` accepts a pending link, otherwise send a request) and the bounded session
 * retry/backoff.
 */
class BuddyLinkDecisionsTest {

    private val me = "0123456789abcdef0123456789abcdef"
    private val other = "fedcba9876543210fedcba9876543210"

    // ── acceptInvite decision ──────────────────────────────────────────────────

    @Test
    fun invite_noExistingLink_sendsRequest() {
        assertEquals(
            BuddyLinkDecisions.InviteAction.SEND_REQUEST,
            BuddyLinkDecisions.inviteAction(me, state = null, b = null)
        )
    }

    @Test
    fun invite_pendingToMe_acceptsExisting() {
        assertEquals(
            BuddyLinkDecisions.InviteAction.ACCEPT_EXISTING,
            BuddyLinkDecisions.inviteAction(me, BuddyLinkState.PENDING, b = me)
        )
    }

    @Test
    fun invite_pendingFromMe_sendsRequestNotAccept() {
        // I'm side a of my own pending request — I must never accept it myself.
        assertEquals(
            BuddyLinkDecisions.InviteAction.SEND_REQUEST,
            BuddyLinkDecisions.inviteAction(me, BuddyLinkState.PENDING, b = other)
        )
    }

    @Test
    fun invite_alreadyAccepted_isConnected() {
        assertEquals(
            BuddyLinkDecisions.InviteAction.ALREADY_CONNECTED,
            BuddyLinkDecisions.inviteAction(me, BuddyLinkState.ACCEPTED, b = other)
        )
    }

    @Test
    fun invite_declinedOrRemoved_sendsRequest() {
        assertEquals(
            BuddyLinkDecisions.InviteAction.SEND_REQUEST,
            BuddyLinkDecisions.inviteAction(me, BuddyLinkState.DECLINED, b = me)
        )
        assertEquals(
            BuddyLinkDecisions.InviteAction.SEND_REQUEST,
            BuddyLinkDecisions.inviteAction(me, BuddyLinkState.REMOVED, b = me)
        )
    }

    @Test
    fun canAccept_onlySideBOfPending() {
        assertTrue(BuddyLinkDecisions.canAccept(me, BuddyLinkState.PENDING, b = me))
        assertFalse(BuddyLinkDecisions.canAccept(me, BuddyLinkState.PENDING, b = other))
        assertFalse(BuddyLinkDecisions.canAccept(me, BuddyLinkState.ACCEPTED, b = me))
        assertFalse(BuddyLinkDecisions.canAccept(me, null, b = null))
    }

    // ── requestLink (re-send) decision ─────────────────────────────────────────

    @Test
    fun requestLink_noExistingLink_createsNew() {
        assertEquals(
            BuddyLinkDecisions.RequestLinkAction.CREATE_NEW,
            BuddyLinkDecisions.requestLinkAction(null)
        )
    }

    @Test
    fun requestLink_declined_recreatesPending() {
        assertEquals(
            BuddyLinkDecisions.RequestLinkAction.RECREATE_PENDING,
            BuddyLinkDecisions.requestLinkAction(BuddyLinkState.DECLINED)
        )
    }

    @Test
    fun requestLink_removed_recreatesPending() {
        assertEquals(
            BuddyLinkDecisions.RequestLinkAction.RECREATE_PENDING,
            BuddyLinkDecisions.requestLinkAction(BuddyLinkState.REMOVED)
        )
    }

    @Test
    fun requestLink_pending_bumpsExisting() {
        assertEquals(
            BuddyLinkDecisions.RequestLinkAction.BUMP_EXISTING,
            BuddyLinkDecisions.requestLinkAction(BuddyLinkState.PENDING)
        )
    }

    @Test
    fun requestLink_accepted_bumpsExisting() {
        assertEquals(
            BuddyLinkDecisions.RequestLinkAction.BUMP_EXISTING,
            BuddyLinkDecisions.requestLinkAction(BuddyLinkState.ACCEPTED)
        )
    }

    @Test
    fun parseState_mapsFirestoreStrings() {
        assertNull(BuddyLinkDecisions.parseState(null))
        assertEquals(BuddyLinkState.ACCEPTED, BuddyLinkDecisions.parseState("accepted"))
        assertEquals(BuddyLinkState.DECLINED, BuddyLinkDecisions.parseState("declined"))
        assertEquals(BuddyLinkState.REMOVED, BuddyLinkDecisions.parseState("removed"))
        assertEquals(BuddyLinkState.PENDING, BuddyLinkDecisions.parseState("pending"))
    }

    // ── session retry / backoff ────────────────────────────────────────────────

    @Test
    fun retry_stopsOnSuccess() {
        assertFalse(BuddySessionRetry.shouldRetry(1, succeeded = true, sessionNeeded = true))
    }

    @Test
    fun retry_boundedToMaxAttempts() {
        assertTrue(BuddySessionRetry.shouldRetry(1, succeeded = false, sessionNeeded = true))
        assertTrue(BuddySessionRetry.shouldRetry(2, succeeded = false, sessionNeeded = true))
        assertFalse(BuddySessionRetry.shouldRetry(BuddySessionRetry.MAX_ATTEMPTS, succeeded = false, sessionNeeded = true))
        assertEquals(3, BuddySessionRetry.MAX_ATTEMPTS)
    }

    @Test
    fun retry_notWhenNoSessionNeeded() {
        // NoOp backend (or already signed in): never loop.
        assertFalse(BuddySessionRetry.shouldRetry(1, succeeded = false, sessionNeeded = false))
    }

    @Test
    fun backoff_doublesFromBase() {
        assertEquals(2_000L, BuddySessionRetry.delayAfterAttemptMs(1))
        assertEquals(4_000L, BuddySessionRetry.delayAfterAttemptMs(2))
    }
}
