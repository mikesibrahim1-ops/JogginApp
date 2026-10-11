package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the V1.48 Buddy code helpers (format / normalize / validate).
 * The "code" is the raw appId: a UUID with dashes removed → 32 lowercase hex chars.
 */
class BuddyCodeTest {

    private val validId = "0123456789abcdef0123456789abcdef" // 32 hex chars

    @Test
    fun format_groupsIntoFourCharChunks() {
        assertEquals("0123 4567 89ab cdef 0123 4567 89ab cdef", formatBuddyCode(validId))
    }

    @Test
    fun normalize_stripsSpacesAndDashes_andLowercases() {
        assertEquals(validId, normalizeBuddyCode("0123 4567 89AB CDEF 0123 4567 89AB CDEF"))
        assertEquals(validId, normalizeBuddyCode("0123-4567-89ab-cdef-0123-4567-89ab-cdef"))
    }

    @Test
    fun valid_acceptsThirtyTwoHex() {
        assertTrue(isValidBuddyCode(validId))
    }

    @Test
    fun valid_rejectsWrongLength() {
        assertFalse(isValidBuddyCode("abc"))
        assertFalse(isValidBuddyCode(validId + "ab"))
    }

    @Test
    fun valid_rejectsNonHexChars() {
        // 'g' and 'z' are not hex.
        val bad = "g123456789abcdef0123456789abcdez"
        assertEquals(32, bad.length)
        assertFalse(isValidBuddyCode(bad))
    }

    @Test
    fun normalizeThenValidate_roundTripsFromDisplayForm() {
        val display = formatBuddyCode(validId)
        assertTrue(isValidBuddyCode(normalizeBuddyCode(display)))
    }
}
