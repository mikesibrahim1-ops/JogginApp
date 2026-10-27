package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for [ShareLifetime] (S2): expiresAt adds the lifetime constant and
 * remainingMs clamps at zero.
 */
class ShareLifetimeTest {

    @Test
    fun expiresAt_addsLifetimeConstant() {
        val createdAt = 1_000L
        assertEquals(
            createdAt + BuddyConstants.SHARE_LIFETIME_MS,
            ShareLifetime.expiresAt(createdAt)
        )
    }

    @Test
    fun remainingMs_normalCase() {
        assertEquals(400L, ShareLifetime.remainingMs(expiresAtMs = 1_000L, nowMs = 600L))
    }

    @Test
    fun remainingMs_clampsAtZeroWhenPastExpiry() {
        assertEquals(0L, ShareLifetime.remainingMs(expiresAtMs = 1_000L, nowMs = 5_000L))
    }
}
