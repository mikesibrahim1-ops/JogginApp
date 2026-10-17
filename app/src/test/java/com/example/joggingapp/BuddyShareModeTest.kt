package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [BuddyShareMode] and the [shouldAppendTrailPoint] write-gate
 * decision (design §5, §13). No Android/Firebase needed.
 */
class BuddyShareModeTest {

    @Test
    fun shouldAppendTrailPoint_pingIsCircleOnly() {
        assertFalse(shouldAppendTrailPoint(BuddyShareMode.PING))
    }

    @Test
    fun shouldAppendTrailPoint_routeAppendsTrail() {
        assertTrue(shouldAppendTrailPoint(BuddyShareMode.ROUTE))
    }

    @Test
    fun fromWire_knownValues() {
        assertEquals(BuddyShareMode.PING, BuddyShareMode.fromWire("ping"))
        assertEquals(BuddyShareMode.ROUTE, BuddyShareMode.fromWire("route"))
    }

    @Test
    fun fromWire_nullFallsBackToPing() {
        assertEquals(BuddyShareMode.PING, BuddyShareMode.fromWire(null))
    }

    @Test
    fun fromWire_garbageFallsBackToPing() {
        assertEquals(BuddyShareMode.PING, BuddyShareMode.fromWire("nonsense"))
        assertEquals(BuddyShareMode.PING, BuddyShareMode.fromWire(""))
        assertEquals(BuddyShareMode.PING, BuddyShareMode.fromWire("ROUTE"))
    }

    @Test
    fun default_isPing() {
        assertEquals(BuddyShareMode.PING, BuddyShareMode.DEFAULT)
    }

    @Test
    fun wire_valuesAreStable() {
        assertEquals("ping", BuddyShareMode.PING.wire)
        assertEquals("route", BuddyShareMode.ROUTE.wire)
    }
}
