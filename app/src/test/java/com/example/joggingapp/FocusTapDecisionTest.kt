package com.example.joggingapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the focus (GPS crosshair) button decision logic extracted from
 * JogMap. Covers the tap-to-centre branch selection and the button-colour GPS state.
 */
class FocusTapDecisionTest {

    // ── decide(): branch selection ─────────────────────────────────────────────

    @Test
    fun activeTrail_centresOnTrail() {
        // A live trail point always wins, even when a fix is also available.
        assertEquals(
            FocusTapDecision.Action.CENTRE_ON_TRAIL,
            FocusTapDecision.decide(hasTrailPoint = true, hasFix = true, hasPermission = true)
        )
    }

    @Test
    fun noTrailButFix_centresOnFix() {
        assertEquals(
            FocusTapDecision.Action.CENTRE_ON_FIX,
            FocusTapDecision.decide(hasTrailPoint = false, hasFix = true, hasPermission = true)
        )
    }

    @Test
    fun noTrailNoFixNoPermission_reportsNoPermission() {
        // Must not be a silent no-op when location is denied.
        assertEquals(
            FocusTapDecision.Action.NO_PERMISSION,
            FocusTapDecision.decide(hasTrailPoint = false, hasFix = false, hasPermission = false)
        )
    }

    @Test
    fun noTrailNoFixWithPermission_awaitsFix() {
        // The core bug fix: with permission but no fix yet, arm a wait for the next fix
        // instead of doing nothing.
        assertEquals(
            FocusTapDecision.Action.AWAIT_FIX,
            FocusTapDecision.decide(hasTrailPoint = false, hasFix = false, hasPermission = true)
        )
    }

    @Test
    fun trailWinsOverMissingPermission() {
        // An active run can still centre on its trail regardless of permission state.
        assertEquals(
            FocusTapDecision.Action.CENTRE_ON_TRAIL,
            FocusTapDecision.decide(hasTrailPoint = true, hasFix = false, hasPermission = false)
        )
    }

    // ── gpsAvailable(): button colour state ────────────────────────────────────

    @Test
    fun gpsAvailable_falseWhenNothingKnown() {
        assertFalse(
            FocusTapDecision.gpsAvailable(gotFirstFix = false, hasFix = false, hasGpsSignalHint = false)
        )
    }

    @Test
    fun gpsAvailable_trueOnFirstFix() {
        assertTrue(
            FocusTapDecision.gpsAvailable(gotFirstFix = true, hasFix = false, hasGpsSignalHint = false)
        )
    }

    @Test
    fun gpsAvailable_trueWhenOverlayHasFix() {
        assertTrue(
            FocusTapDecision.gpsAvailable(gotFirstFix = false, hasFix = true, hasGpsSignalHint = false)
        )
    }

    @Test
    fun gpsAvailable_trueWhenTrackingHint() {
        // hasGpsSignalHint mirrors MainActivity passing path.isNotEmpty().
        assertTrue(
            FocusTapDecision.gpsAvailable(gotFirstFix = false, hasFix = false, hasGpsSignalHint = true)
        )
    }
}
