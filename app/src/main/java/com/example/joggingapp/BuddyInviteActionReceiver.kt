package com.example.joggingapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the Accept / Ignore actions on the "<name> wants to buddy up" shade
 * notification (V1.48 code-based invite flow). The notification is raised by
 * [BuddyNotifier.notifyRequestReceived] with two explicit-intent broadcast actions
 * wired here.
 *
 * All Firestore work runs OFF the main thread: we take [goAsync] so the receiver stays
 * alive past onReceive, run acceptLink/declineLink on [Dispatchers.IO], cancel the
 * notification, and always finish the pending result.
 *
 * Declared `android:exported="false"` in BOTH manifests (main + firebase overlay) and
 * only ever targeted by explicit intents created in-process.
 */
class BuddyInviteActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val linkId = intent.getStringExtra(EXTRA_LINK_ID)
        if (linkId.isNullOrBlank() || (action != ACTION_ACCEPT && action != ACTION_IGNORE)) {
            // Nothing actionable — don't start async work. (No early return inside a
            // Compose lambda here; this is a plain receiver, so a guard return is fine.)
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val repo = BuddyRepositoryProvider.get(appContext)
                when (action) {
                    ACTION_ACCEPT -> repo.acceptLink(linkId)
                    ACTION_IGNORE -> repo.declineLink(linkId)
                }
                AppLogger.log(
                    appContext, LogCategory.PROFILE,
                    "Buddy invite shade action: ${if (action == ACTION_ACCEPT) "accept" else "ignore"}"
                )
            } catch (t: Throwable) {
                AppLogger.log(appContext, LogCategory.ERROR, "Buddy invite action failed: ${t.message}")
            } finally {
                runCatching {
                    NotificationManagerCompat.from(appContext).cancel(BuddyNotifier.NOTIF_ID_REQUEST)
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.example.joggingapp.BUDDY_ACCEPT"
        const val ACTION_IGNORE = "com.example.joggingapp.BUDDY_IGNORE"
        const val EXTRA_LINK_ID = "com.example.joggingapp.extra.LINK_ID"
    }
}
