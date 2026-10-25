package com.example.joggingapp

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for [BuddyPermissionFlow.nextStep] — the foreground-first, then
 * background, then enable sequencing that fixes the enable-toggle bug (a one-shot
 * background request on Android 10+ was auto-denied and stranded the toggle OFF).
 * `nextStep` takes sdkInt directly, so no Robolectric is needed.
 */
class BuddyPermissionFlowTest {

    private val q = Build.VERSION_CODES.Q
    private val preQ = Build.VERSION_CODES.P

    @Test
    fun preQ_alwaysEnablesImmediately() {
        assertEquals(
            BuddyPermissionFlow.EnableStep.ENABLE_NOW,
            BuddyPermissionFlow.nextStep(preQ, hasForeground = false, hasBackground = false)
        )
        assertEquals(
            BuddyPermissionFlow.EnableStep.ENABLE_NOW,
            BuddyPermissionFlow.nextStep(preQ, hasForeground = true, hasBackground = true)
        )
    }

    @Test
    fun qPlus_noForeground_requestsForegroundFirst() {
        assertEquals(
            BuddyPermissionFlow.EnableStep.REQUEST_FOREGROUND,
            BuddyPermissionFlow.nextStep(q, hasForeground = false, hasBackground = false)
        )
        // Even if background somehow reads granted, foreground is still requested first.
        assertEquals(
            BuddyPermissionFlow.EnableStep.REQUEST_FOREGROUND,
            BuddyPermissionFlow.nextStep(q, hasForeground = false, hasBackground = true)
        )
    }

    @Test
    fun qPlus_foregroundButNoBackground_requestsBackground() {
        assertEquals(
            BuddyPermissionFlow.EnableStep.REQUEST_BACKGROUND,
            BuddyPermissionFlow.nextStep(q, hasForeground = true, hasBackground = false)
        )
    }

    @Test
    fun qPlus_bothGranted_enablesNow() {
        assertEquals(
            BuddyPermissionFlow.EnableStep.ENABLE_NOW,
            BuddyPermissionFlow.nextStep(q, hasForeground = true, hasBackground = true)
        )
    }
}
