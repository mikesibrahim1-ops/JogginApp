package com.example.joggingapp

import java.util.concurrent.atomic.AtomicInteger

/**
 * Tiny, dependency-free foreground tracker. [MainActivity] increments on `onStart` and
 * decrements on `onStop`, so [isForeground] is true while any activity is visible.
 *
 * Used to de-duplicate Buddy Link notifications: while the app is in the foreground the
 * in-app path ([BuddyLinkController] diffing Firestore flows) already surfaces link
 * events, so the FCM push path ([BuddyFirebaseMessagingService]) suppresses its own
 * notification to avoid a double alert. When the app is backgrounded/killed, only the
 * push path runs.
 */
object AppForeground {
    private val startedCount = AtomicInteger(0)

    /** True while at least one activity is between onStart and onStop. */
    val isForeground: Boolean get() = startedCount.get() > 0

    fun onActivityStarted() { startedCount.incrementAndGet() }

    fun onActivityStopped() { startedCount.updateAndGet { if (it > 0) it - 1 else 0 } }
}
