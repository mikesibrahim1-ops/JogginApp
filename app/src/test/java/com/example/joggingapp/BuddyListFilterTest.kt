package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [BuddyListFilter] — the suppression/reinstatement logic behind the
 * reversible-block model. Proves that blocking merely partitions the (link-preserved)
 * accepted-buddy list into active vs blocked, and unblocking restores the buddy. No
 * Robolectric needed (the object is Android/Firebase-free), matching BuddyLinkDecisionsTest.
 */
class BuddyListFilterTest {

    private val meA = "0123456789abcdef0123456789abcdef"
    private val meB = "fedcba9876543210fedcba9876543210"

    private fun view(appId: String, name: String): BuddyView =
        BuddyView(user = BuddyUser(appId = appId, displayName = name))

    @Test
    fun activeBuddies_excludesBlocked() {
        val all = listOf(view(meA, "Alice"), view(meB, "Bob"))
        val active = BuddyListFilter.activeBuddies(all, setOf(meA))
        assertEquals(1, active.size)
        assertEquals(meB, active.first().user.appId)
    }

    @Test
    fun blockedBuddies_includesOnlyBlocked_withName() {
        val all = listOf(view(meA, "Alice"), view(meB, "Bob"))
        val blocked = BuddyListFilter.blockedBuddies(all, setOf(meA))
        assertEquals(1, blocked.size)
        assertEquals(meA, blocked.first().user.appId)
        // The display name survives because the link (and thus the BuddyView) survives.
        assertEquals("Alice", blocked.first().user.displayName)
    }

    @Test
    fun unblock_reinstates() {
        val all = listOf(view(meA, "Alice"), view(meB, "Bob"))
        // Empty blocked set == unblocked: the previously-blocked buddy is active again.
        val active = BuddyListFilter.activeBuddies(all, emptySet())
        val blocked = BuddyListFilter.blockedBuddies(all, emptySet())
        assertTrue(active.any { it.user.appId == meA })
        assertTrue(blocked.isEmpty())
    }

    @Test
    fun emptyBlocked_allActive() {
        val all = listOf(view(meA, "Alice"), view(meB, "Bob"))
        assertEquals(all, BuddyListFilter.activeBuddies(all, emptySet()))
        assertTrue(BuddyListFilter.blockedBuddies(all, emptySet()).isEmpty())
    }

    @Test
    fun allBlocked_noneActive() {
        val all = listOf(view(meA, "Alice"), view(meB, "Bob"))
        val blockedSet = setOf(meA, meB)
        assertTrue(BuddyListFilter.activeBuddies(all, blockedSet).isEmpty())
        assertEquals(2, BuddyListFilter.blockedBuddies(all, blockedSet).size)
    }

    @Test
    fun activeBuddies_and_blockedBuddies_arePartition() {
        val all = listOf(view(meA, "Alice"), view(meB, "Bob"))
        val blockedSet = setOf(meA)
        val active = BuddyListFilter.activeBuddies(all, blockedSet)
        val blocked = BuddyListFilter.blockedBuddies(all, blockedSet)
        // Disjoint...
        val activeIds = active.map { it.user.appId }.toSet()
        val blockedIds = blocked.map { it.user.appId }.toSet()
        assertFalse(activeIds.any { it in blockedIds })
        // ...and together cover the full accepted list.
        assertEquals(all.map { it.user.appId }.toSet(), activeIds + blockedIds)
    }
}
