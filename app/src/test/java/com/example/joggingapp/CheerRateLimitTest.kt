package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for the S1 Live Cheer rate-limit ([CheerRateLimit.decide]). Mirrors
 * the [BuddyLocateTest] / [BuddyLinkDecisionsTest] style: build the per-buddy maps
 * directly and inject a fixed `nowMs` so each decision is deterministic.
 *
 * Covers: first-ever cheer → OK; within-cooldown → COOLDOWN; the exact cooldown
 * boundary → OK; at/over the daily cap → DAILY_CAP; and that a different buddy is
 * unaffected by another buddy's cooldown/count.
 */
class CheerRateLimitTest {

    private val buddy = "0123456789abcdef0123456789abcdef"
    private val other = "fedcba9876543210fedcba9876543210"
    private val now = 1_000_000_000_000L

    @Test
    fun firstCheer_isAllowed() {
        val d = CheerRateLimit.decide(emptyMap(), emptyMap(), buddy, now)
        assertEquals(CheerRateLimit.Decision(true, CheerRateLimit.Reason.OK), d)
    }

    @Test
    fun withinCooldown_isDeniedWithCooldownReason() {
        val last = mapOf(buddy to now - (BuddyConstants.CHEER_COOLDOWN_MS - 1))
        val d = CheerRateLimit.decide(last, emptyMap(), buddy, now)
        assertEquals(CheerRateLimit.Decision(false, CheerRateLimit.Reason.COOLDOWN), d)
    }

    @Test
    fun exactlyAtCooldownBoundary_isAllowed() {
        // Spacing == CHEER_COOLDOWN_MS is allowed (the guard uses `<`).
        val last = mapOf(buddy to now - BuddyConstants.CHEER_COOLDOWN_MS)
        val d = CheerRateLimit.decide(last, emptyMap(), buddy, now)
        assertEquals(CheerRateLimit.Decision(true, CheerRateLimit.Reason.OK), d)
    }

    @Test
    fun atDailyCap_isDeniedWithDailyCapReason() {
        // Past the cooldown, but the buddy has already hit the cap today.
        val last = mapOf(buddy to now - BuddyConstants.CHEER_COOLDOWN_MS)
        val count = mapOf(buddy to BuddyConstants.CHEER_DAILY_CAP)
        val d = CheerRateLimit.decide(last, count, buddy, now)
        assertEquals(CheerRateLimit.Decision(false, CheerRateLimit.Reason.DAILY_CAP), d)
    }

    @Test
    fun overDailyCap_isDeniedWithDailyCapReason() {
        val last = mapOf(buddy to now - BuddyConstants.CHEER_COOLDOWN_MS)
        val count = mapOf(buddy to BuddyConstants.CHEER_DAILY_CAP + 5)
        val d = CheerRateLimit.decide(last, count, buddy, now)
        assertEquals(CheerRateLimit.Decision(false, CheerRateLimit.Reason.DAILY_CAP), d)
    }

    @Test
    fun cooldownCheckedBeforeDailyCap() {
        // Within cooldown AND over cap → COOLDOWN wins (checked first).
        val last = mapOf(buddy to now - (BuddyConstants.CHEER_COOLDOWN_MS - 1))
        val count = mapOf(buddy to BuddyConstants.CHEER_DAILY_CAP + 1)
        val d = CheerRateLimit.decide(last, count, buddy, now)
        assertEquals(CheerRateLimit.Decision(false, CheerRateLimit.Reason.COOLDOWN), d)
    }

    @Test
    fun otherBuddyUnaffectedByThisBuddysCooldownAndCount() {
        // `buddy` is both within cooldown and at the cap; `other` is untouched → OK.
        val last = mapOf(buddy to now)
        val count = mapOf(buddy to BuddyConstants.CHEER_DAILY_CAP)
        val d = CheerRateLimit.decide(last, count, other, now)
        assertEquals(CheerRateLimit.Decision(true, CheerRateLimit.Reason.OK), d)
    }
}
