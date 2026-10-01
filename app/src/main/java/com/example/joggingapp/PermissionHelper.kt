package com.example.joggingapp

import android.os.Build

/**
 * Pure, side-effect-free decisions about whether a runtime permission needs to be
 * requested on the current OS version. Kept free of Android framework calls (takes the
 * SDK level as a parameter) so the logic is unit-testable on the JVM.
 *
 * The "already granted" flag lets callers short-circuit: if the permission is already
 * held, there is nothing to request.
 */
object PermissionHelper {

    /**
     * POST_NOTIFICATIONS is a runtime permission only from Android 13 (API 33, TIRAMISU).
     * On earlier versions notifications are granted implicitly, so no request is needed.
     */
    fun shouldRequestNotificationPermission(sdkInt: Int, alreadyGranted: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.TIRAMISU && !alreadyGranted

    /**
     * ACCESS_BACKGROUND_LOCATION is a separate runtime permission only from Android 10
     * (API 29, Q). Before that, foreground location permission covers background use.
     */
    fun shouldRequestBackgroundLocationPermission(sdkInt: Int, alreadyGranted: Boolean): Boolean =
        sdkInt >= Build.VERSION_CODES.Q && !alreadyGranted
}
