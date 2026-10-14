package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [BuddyTransitions] — the notification-transition + cooldown
 * decision logic. No Android/Firebase/Robolectric needed.
 */
class BuddyTransitionsTest {

    // A fixed "now" so liveness is deterministic across the suite.
    private val NOW = 1_000_000_000L

    private fun link(id: String, a: String = "a", b: String = "b", reqCount: Int = 1) =
        BuddyLink(linkId = id, a = a, b = b, state = BuddyLinkState.PENDING, reqCount = reqCount)

    private fun buddy(appId: String, name: String = "") =
        BuddyView(user = BuddyUser(appId = appId, displayName = name))

    // A live buddy: mutually sharing AND a fresh fix (so isLiveNow() == true).
    private fun liveBuddy(appId: String, name: String = "", atMs: Long = NOW) =
        BuddyView(
            user = BuddyUser(appId = appId, displayName = name, sharing = true),
            location = BuddyLocation(lat = 0.0, lon = 0.0, at = atMs)
        )

    // A buddy sharing but with a stale fix (older than STALE_MS) -> NOT live.
    private fun staleBuddy(appId: String, name: String = "") =
        BuddyView(
            user = BuddyUser(appId = appId, displayName = name, sharing = true),
            location = BuddyLocation(lat = 0.0, lon = 0.0, at = NOW - BuddyConstants.STALE_MS - 1_000L)
        )

    // ── pendingEvents ────────────────────────────────────────────────────────

    @Test
    fun pending_firstEmission_neverAlerts() {
        val events = BuddyTransitions.pendingEvents(prev = null, newPending = listOf(link("x_y")))
        assertTrue(events.isEmpty())
    }

    @Test
    fun pending_newRequest_alertsOnce() {
        val prev = emptyMap<String, Int>()
        val events = BuddyTransitions.pendingEvents(prev, listOf(link("x_y", reqCount = 1)))
        assertEquals(1, events.size)
        assertTrue(events[0] is BuddyTransitions.Event.RequestReceived)
    }

    @Test
    fun pending_unchangedRequest_doesNotReAlert() {
        val prev = mapOf("x_y" to 1)
        val events = BuddyTransitions.pendingEvents(prev, listOf(link("x_y", reqCount = 1)))
        assertTrue(events.isEmpty())
    }

    @Test
    fun pending_bumpedWithinCap_alertsAgain() {
        val prev = mapOf("x_y" to 1)
        val events = BuddyTransitions.pendingEvents(prev, listOf(link("x_y", reqCount = 2)))
        assertEquals(1, events.size)
    }

    @Test
    fun pending_bumpedBeyondCap_isSilent() {
        // reqCount 3 exceeds the default cap of 2 → no alert.
        val prev = mapOf("x_y" to 2)
        val events = BuddyTransitions.pendingEvents(prev, listOf(link("x_y", reqCount = 3)))
        assertTrue(events.isEmpty())
    }

    @Test
    fun pending_snapshot_capturesCounts() {
        val snap = BuddyTransitions.pendingSnapshot(listOf(link("x_y", reqCount = 2), link("p_q", reqCount = 1)))
        assertEquals(mapOf("x_y" to 2, "p_q" to 1), snap)
    }

    // ── buddyEvents ──────────────────────────────────────────────────────────

    @Test
    fun buddies_firstEmission_neverAlerts() {
        val events = BuddyTransitions.buddyEvents(prev = null, newBuddies = listOf(buddy("id1")))
        assertTrue(events.isEmpty())
    }

    @Test
    fun buddies_appeared_emitsAcceptedWithName() {
        val events = BuddyTransitions.buddyEvents(
            prev = emptySet(),
            newBuddies = listOf(buddy("id1", name = "Alice"))
        )
        assertEquals(1, events.size)
        assertEquals(BuddyTransitions.Event.Accepted("Alice"), events[0])
    }

    @Test
    fun buddies_appearedBlankName_fallsBackToShortId() {
        val longId = "abcdef1234567890"
        val events = BuddyTransitions.buddyEvents(emptySet(), listOf(buddy(longId, name = "")))
        assertEquals(1, events.size)
        val ev = events[0] as BuddyTransitions.Event.Accepted
        assertEquals("abcdef…7890", ev.name)
    }

    @Test
    fun buddies_vanished_emitsUnlinkedUsingPrevName() {
        val events = BuddyTransitions.buddyEvents(
            prev = setOf("id1"),
            newBuddies = emptyList(),
            prevBuddies = listOf(buddy("id1", name = "Bob"))
        )
        assertEquals(1, events.size)
        assertEquals(BuddyTransitions.Event.Unlinked("Bob"), events[0])
    }

    @Test
    fun buddies_noChange_noEvents() {
        val events = BuddyTransitions.buddyEvents(setOf("id1"), listOf(buddy("id1", "Alice")))
        assertTrue(events.isEmpty())
    }

    @Test
    fun buddies_snapshot_capturesIds() {
        val snap = BuddyTransitions.buddySnapshot(listOf(buddy("id1"), buddy("id2")))
        assertEquals(setOf("id1", "id2"), snap)
    }

    // ── onlineEvents / onlineSnapshot (buddy came online) ───────────────────────

    @Test
    fun online_firstEmission_neverAlerts() {
        // prev == null is the baseline: even with online buddies, no event fires.
        val events = BuddyTransitions.onlineEvents(
            prev = null, newBuddies = listOf(liveBuddy("id1", "Alice")), nowMs = NOW
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun online_alreadyOnlineAtBaseline_noEvent() {
        // Baseline already counted id1 online -> staying online fires nothing.
        val events = BuddyTransitions.onlineEvents(
            prev = setOf("id1"), newBuddies = listOf(liveBuddy("id1", "Alice")), nowMs = NOW
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun online_offlineToOnline_emitsOneCameOnlineWithName() {
        // id1 was NOT online before, now is -> exactly one CameOnline with displayName.
        val events = BuddyTransitions.onlineEvents(
            prev = emptySet(), newBuddies = listOf(liveBuddy("id1", "Alice")), nowMs = NOW
        )
        assertEquals(1, events.size)
        assertEquals(BuddyTransitions.Event.CameOnline("id1", "Alice"), events[0])
    }

    @Test
    fun online_blankName_fallsBackToShortId() {
        val longId = "abcdef1234567890"
        val events = BuddyTransitions.onlineEvents(
            prev = emptySet(), newBuddies = listOf(liveBuddy(longId, "")), nowMs = NOW
        )
        assertEquals(1, events.size)
        val ev = events[0] as BuddyTransitions.Event.CameOnline
        assertEquals(longId, ev.appId)
        assertEquals("abcdef…7890", ev.name)
    }

    @Test
    fun online_sharingButStaleFix_isNotOnline_noEvent() {
        // sharing=true but the fix is stale -> isLiveNow() false -> not announced.
        val events = BuddyTransitions.onlineEvents(
            prev = emptySet(), newBuddies = listOf(staleBuddy("id1", "Alice")), nowMs = NOW
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun online_snapshot_capturesOnlyLiveIds() {
        val snap = BuddyTransitions.onlineSnapshot(
            listOf(liveBuddy("id1"), staleBuddy("id2"), buddy("id3")), nowMs = NOW
        )
        assertEquals(setOf("id1"), snap)
    }

    @Test
    fun online_dedupe_staysOnlineAcrossEmissionsFiresOnce() {
        // Emission 1: offline->online fires once; carry the snapshot forward.
        val e1 = BuddyTransitions.onlineEvents(emptySet(), listOf(liveBuddy("id1", "Alice")), NOW)
        assertEquals(1, e1.size)
        val snap1 = BuddyTransitions.onlineSnapshot(listOf(liveBuddy("id1", "Alice")), NOW)
        // Emission 2: still online, carried-forward snapshot -> no repeat alert.
        val e2 = BuddyTransitions.onlineEvents(snap1, listOf(liveBuddy("id1", "Alice")), NOW)
        assertTrue(e2.isEmpty())
    }

    // ── canRequestLocation ─────────────────────────────────────────────────────

    @Test
    fun cooldown_neverRequested_allowed() {
        assertTrue(BuddyTransitions.canRequestLocation(emptyMap(), "id1", nowMs = 1_000L, cooldownMs = 1000L))
    }

    @Test
    fun cooldown_withinWindow_blocked() {
        val last = mapOf("id1" to 1_000L)
        assertFalse(BuddyTransitions.canRequestLocation(last, "id1", nowMs = 1_500L, cooldownMs = 1000L))
    }

    @Test
    fun cooldown_afterWindow_allowed() {
        val last = mapOf("id1" to 1_000L)
        assertTrue(BuddyTransitions.canRequestLocation(last, "id1", nowMs = 2_000L, cooldownMs = 1000L))
    }

    @Test
    fun cooldown_perBuddyIndependent() {
        val last = mapOf("id1" to 1_000L)
        // Different buddy has no recorded request → allowed.
        assertTrue(BuddyTransitions.canRequestLocation(last, "id2", nowMs = 1_100L, cooldownMs = 1000L))
    }

    @Test
    fun shortAppId_shortStaysAsIs_longIsTruncated() {
        assertEquals("short", BuddyTransitions.shortAppId("short"))
        assertEquals("abcdef…7890", BuddyTransitions.shortAppId("abcdef1234567890"))
    }
}
