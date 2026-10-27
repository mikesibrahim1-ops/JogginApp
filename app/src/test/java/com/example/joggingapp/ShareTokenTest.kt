package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [ShareToken.generate] (S2): 22-char URL-safe-Base64 charset and
 * that two generated tokens differ.
 */
class ShareTokenTest {

    private val urlSafeBase64 = Regex("^[A-Za-z0-9_-]+$")

    @Test
    fun generated_is22Chars() {
        assertEquals(22, ShareToken.generate().length)
    }

    @Test
    fun generated_matchesUrlSafeCharset() {
        assertTrue(urlSafeBase64.matches(ShareToken.generate()))
    }

    @Test
    fun twoGenerations_differ() {
        assertNotEquals(ShareToken.generate(), ShareToken.generate())
    }
}
