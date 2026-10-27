package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [ShareLinkValidity.decide] (S2). Verifies the gate ordering
 * (token → revoked → expired → OK), the nowMs == expiresAtMs boundary (EXPIRED), and
 * that revoked beats expired.
 */
class ShareLinkValidityTest {

    private val expiresAt = 10_000L

    @Test
    fun ok_whenTokenPresentNotRevokedNotExpired() {
        val d = ShareLinkValidity.decide(hasToken = true, expiresAtMs = expiresAt, revoked = false, nowMs = 5_000L)
        assertTrue(d.readable)
        assertEquals(ShareLinkValidity.Reason.OK, d.reason)
    }

    @Test
    fun noToken_isNotReadable() {
        val d = ShareLinkValidity.decide(hasToken = false, expiresAtMs = expiresAt, revoked = false, nowMs = 5_000L)
        assertFalse(d.readable)
        assertEquals(ShareLinkValidity.Reason.NO_TOKEN, d.reason)
    }

    @Test
    fun revoked_isNotReadable() {
        val d = ShareLinkValidity.decide(hasToken = true, expiresAtMs = expiresAt, revoked = true, nowMs = 5_000L)
        assertFalse(d.readable)
        assertEquals(ShareLinkValidity.Reason.REVOKED, d.reason)
    }

    @Test
    fun expired_isNotReadable() {
        val d = ShareLinkValidity.decide(hasToken = true, expiresAtMs = expiresAt, revoked = false, nowMs = 10_001L)
        assertFalse(d.readable)
        assertEquals(ShareLinkValidity.Reason.EXPIRED, d.reason)
    }

    @Test
    fun boundary_nowEqualsExpiresAt_isExpired() {
        val d = ShareLinkValidity.decide(hasToken = true, expiresAtMs = expiresAt, revoked = false, nowMs = expiresAt)
        assertFalse(d.readable)
        assertEquals(ShareLinkValidity.Reason.EXPIRED, d.reason)
    }

    @Test
    fun revoked_beatsExpired() {
        // Both revoked AND past expiry → REVOKED wins (checked first).
        val d = ShareLinkValidity.decide(hasToken = true, expiresAtMs = expiresAt, revoked = true, nowMs = 20_000L)
        assertFalse(d.readable)
        assertEquals(ShareLinkValidity.Reason.REVOKED, d.reason)
    }
}
