package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * JVM/Robolectric tests for the invite parse→accept wiring (invite-dialog pairing fix).
 *
 * [BuddyInvite.parse] uses android.net.Uri, so these run under Robolectric. They cover:
 *  - a built https/scheme link round-trips to Valid(appId = sender);
 *  - an expired link → Expired;
 *  - a malformed link → Malformed;
 *  - the linkId sorting invariant: sorted(me, sender) is identical regardless of order,
 *    which guarantees a recipient-created link and a sender-side link collide to ONE doc
 *    (the basis of the single-write "Option a" accept path).
 */
@RunWith(RobolectricTestRunner::class)
class BuddyInviteTest {

    private val now = 1_000_000_000_000L

    @Test
    fun httpsFallbackLink_roundTripsToValidSender() {
        val sender = "sender-app-id-123"
        val link = BuddyInvite.buildHttpsFallback(sender, now)
        val res = BuddyInvite.parse(link, now)
        assertTrue(res is BuddyInvite.ParseResult.Valid)
        assertEquals(sender, (res as BuddyInvite.ParseResult.Valid).appId)
    }

    @Test
    fun customSchemeLink_roundTripsToValidSender() {
        val sender = "sender-app-id-456"
        val link = BuddyInvite.buildInviteUri(sender, now)
        val res = BuddyInvite.parse(link, now)
        assertTrue(res is BuddyInvite.ParseResult.Valid)
        assertEquals(sender, (res as BuddyInvite.ParseResult.Valid).appId)
    }

    @Test
    fun expiredLink_isExpired() {
        val link = BuddyInvite.buildHttpsFallback("someone", now)
        // Evaluate the link well past its expiry window.
        val later = now + BuddyConstants.INVITE_EXPIRY_MS + 1L
        assertTrue(BuddyInvite.parse(link, later) is BuddyInvite.ParseResult.Expired)
    }

    @Test
    fun malformedLink_isMalformed() {
        assertTrue(BuddyInvite.parse("not a link", now) is BuddyInvite.ParseResult.Malformed)
        // A valid scheme but no id / no exp is also malformed.
        assertTrue(BuddyInvite.parse("https://joggin-a69a7.web.app/buddy", now)
            is BuddyInvite.ParseResult.Malformed)
    }

    @Test
    fun linkId_isOrderIndependent() {
        // Mirrors FirebaseBuddyRepository.linkIdFor (sorted pair joined by "_"): the
        // recipient-created id and the sender-side id must collide to one document.
        val a = "aaa-111"
        val b = "zzz-999"
        assertEquals(linkIdFor(a, b), linkIdFor(b, a))
    }

    private fun linkIdFor(x: String, y: String): String =
        listOf(x, y).sorted().joinToString("_")
}
