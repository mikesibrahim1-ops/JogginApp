package com.example.joggingapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionHelperTest {
    @Test
    fun notificationPermissionIsRequiredOnlyOnAndroid13AndAbove() {
        assertTrue(PermissionHelper.shouldRequestNotificationPermission(33, false))
        assertFalse(PermissionHelper.shouldRequestNotificationPermission(33, true))
        assertFalse(PermissionHelper.shouldRequestNotificationPermission(32, false))
    }

    @Test
    fun backgroundLocationPermissionIsRequiredOnlyOnAndroid10AndAbove() {
        assertTrue(PermissionHelper.shouldRequestBackgroundLocationPermission(29, false))
        assertFalse(PermissionHelper.shouldRequestBackgroundLocationPermission(29, true))
        assertFalse(PermissionHelper.shouldRequestBackgroundLocationPermission(28, false))
    }
}
