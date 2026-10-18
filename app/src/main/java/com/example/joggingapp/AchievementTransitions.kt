package com.example.joggingapp

/**
 * Pure, side-effect-free decision logic for "achievement unlocked" notification
 * transitions. Mirrors [BuddyTransitions] (buddy came online): the composable owns the
 * previous snapshot and the effect (calling [BuddyNotifier]); this object only decides
 * WHICH achievements are newly earned given old + new state.
 *
 * Kept Android-free (operates on [AchievementProgress] only) so it is JVM-unit-testable
 * without Android/Firebase, exactly like [BuddyTransitions].
 */
object AchievementTransitions {

    /**
     * Decide newly-earned achievements from an evaluation transition. An achievement is
     * "earned" when its [AchievementProgress.earnedDate] is non-null (the canonical
     * unlocked signal from [evaluateAchievements]). Returns one entry per achievement
     * that is earned now but whose id was NOT in the previous earned set ([prev]).
     *
     * Returns empty when [prev] is null (the first emission establishes the baseline and
     * NEVER alerts — the required initial-snapshot suppression, so already-earned
     * achievements present at app launch do not notify). Because the caller carries the
     * [earnedSnapshot] forward each emission, an already-earned achievement does not
     * re-alert on recomposition or on subsequent evaluations.
     */
    fun newlyEarned(
        prev: Set<String>?,
        now: List<AchievementProgress>
    ): List<AchievementProgress> {
        if (prev == null) return emptyList()
        return now.filter { it.earnedDate != null && it.achievement.id !in prev }
    }

    /** The earned-achievement id set to carry forward after processing. */
    fun earnedSnapshot(now: List<AchievementProgress>): Set<String> =
        now.filter { it.earnedDate != null }.map { it.achievement.id }.toSet()
}
