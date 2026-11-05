package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure JVM tests for [BuddyShareMode] wire parsing. No Android/Firebase needed. */
class BuddyShareModeTest {

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
