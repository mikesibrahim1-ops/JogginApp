package com.example.joggingapp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Pure, unit-testable permission-decision logic for the Buddy Link enable flow, plus a
 * reusable "open app settings" deep-link helper.
 *
 * The enable flow must request foreground location first (Android Q+), then background,
 * and only enable once both are held. [nextStep] encodes that sequencing as a pure
 * function of the current permission state so it can be tested without Robolectric — it
 * takes [sdkInt] rather than reading [Build.VERSION.SDK_INT] directly.
 */
object BuddyPermissionFlow {

    /** The next action the enable flow should take given the current permission state. */
    enum class EnableStep { REQUEST_FOREGROUND, REQUEST_BACKGROUND, ENABLE_NOW }

    /** How to satisfy the background-location requirement at the REQUEST_BACKGROUND step. */
    enum class BackgroundRequestMode { IN_APP_REQUEST, GO_TO_SETTINGS }

    /**
     * Decide the next enable step:
     *  - pre-Q: no background-location concept → enable immediately.
     *  - Q+: foreground (fine/coarse) first, then background, then enable.
     */
    fun nextStep(sdkInt: Int, hasForeground: Boolean, hasBackground: Boolean): EnableStep = when {
        sdkInt < Build.VERSION_CODES.Q -> EnableStep.ENABLE_NOW
        !hasForeground -> EnableStep.REQUEST_FOREGROUND
        !hasBackground -> EnableStep.REQUEST_BACKGROUND
        else -> EnableStep.ENABLE_NOW
    }

    /**
     * Decide, at the REQUEST_BACKGROUND step, whether to request background location
     * in-app (API 29, where the OS can show the "Allow all the time" dialog) or drive
     * the user to the app's system Settings (API 30+, where a runtime background request
     * is auto-denied and Settings is the only path to "Allow all the time"). Pure and
     * framework-free (takes [sdkInt]) so it is unit-testable like [nextStep].
     */
    fun backgroundRequestMode(sdkInt: Int): BackgroundRequestMode =
        if (sdkInt >= Build.VERSION_CODES.R) BackgroundRequestMode.GO_TO_SETTINGS
        else BackgroundRequestMode.IN_APP_REQUEST

    /**
     * Intent that opens this app's details page in system Settings, where the user can
     * grant "Allow all the time" background location. Kept separate from [nextStep] so
     * the decision logic stays framework-free.
     */
    fun appSettingsIntent(packageName: String): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
}
