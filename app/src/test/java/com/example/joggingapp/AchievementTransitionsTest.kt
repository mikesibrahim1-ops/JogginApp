package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [AchievementTransitions] — the "achievement unlocked" transition
 * decision logic. No Android/Firebase/Robolectric needed. Mirrors [BuddyTransitionsTest].
 */
class AchievementTransitionsTest {

    private fun ach(id: String) = ALL_ACHIEVEMENTS.first { it.id == id }

    private fun earned(id: String) =
        AchievementProgress(ach(id), progress = 1f, earnedDate = "Jan 01, 2024")

    private fun notEarned(id: String, progress: Float = 0.9f) =
        AchievementProgress(ach(id), progress = progress, earnedDate = null)

    @Test
    fun firstEmission_neverAlerts() {
        // prev == null is the baseline: even with earned items, nothing fires on launch.
        val events = AchievementTransitions.newlyEarned(
            prev = null, now = listOf(earned("walk_first"), earned("run_first"))
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun alreadyEarnedAtBaseline_noEvent() {
        val events = AchievementTransitions.newlyEarned(
            prev = setOf("walk_first"), now = listOf(earned("walk_first"))
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun notEarnedToEarned_emitsExactlyOne() {
        val events = AchievementTransitions.newlyEarned(
            prev = emptySet(), now = listOf(earned("walk_first"))
        )
        assertEquals(1, events.size)
        assertEquals("walk_first", events[0].achievement.id)
    }

    @Test
    fun nearCompleteButNotEarned_doesNotEmit() {
        val events = AchievementTransitions.newlyEarned(
            prev = emptySet(), now = listOf(notEarned("walk_first", progress = 0.99f))
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun staysEarnedAcrossEmissions_firesOnce() {
        // Emission 1: not-earned -> earned fires once; carry snapshot forward.
        val e1 = AchievementTransitions.newlyEarned(emptySet(), listOf(earned("walk_first")))
        assertEquals(1, e1.size)
        val snap1 = AchievementTransitions.earnedSnapshot(listOf(earned("walk_first")))
        // Emission 2: still earned, carried-forward snapshot -> no repeat alert.
        val e2 = AchievementTransitions.newlyEarned(snap1, listOf(earned("walk_first")))
        assertTrue(e2.isEmpty())
    }

    @Test
    fun snapshot_capturesOnlyEarnedIds() {
        val snap = AchievementTransitions.earnedSnapshot(
            listOf(earned("walk_first"), notEarned("run_first"), earned("streak_2"))
        )
        assertEquals(setOf("walk_first", "streak_2"), snap)
    }
}
