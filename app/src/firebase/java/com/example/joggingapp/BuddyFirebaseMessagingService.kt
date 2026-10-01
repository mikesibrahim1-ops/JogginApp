package com.example.joggingapp

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives FCM messages for Buddy Link (task 7.3, design §12) and turns them into local
 * notifications via [BuddyNotifier], so events (request / accepted / unlinked) reach the
 * device even when the app process is killed — unlike the in-app [BuddyLinkController]
 * diffing, which only runs while the app is alive.
 *
 * IMPORTANT — this class lives in the gated `src/firebase/java` source root and is only
 * compiled + declared in the manifest when `app/google-services.json` is present (see
 * app/build.gradle: the `firebase` manifest overlay is swapped in only then). Without
 * Firebase configured, this class does not exist and nothing references it.
 *
 * The Cloud Function sends DATA-only messages (see firebase-hosting/functions/index.js):
 *   data.type      = "request" | "accepted" | "unlinked"
 *   data.actorName = the other party's display name (may be empty)
 * Data-only messages are always delivered to this service (foreground or background),
 * letting the client fully control presentation to match the in-app copy.
 */
class BuddyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // The registration token rotated — persist the new one for future sends.
        // BuddyRepository.refreshFcmToken() re-reads and stores the current token.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                BuddyRepositoryProvider.get(applicationContext).refreshFcmToken()
            } catch (_: Exception) {
                // Non-fatal: token will also be refreshed next time the app observes.
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"] ?: return
        val actorName = message.data["actorName"].orEmpty()
        // De-dupe: while the app is foregrounded, the in-app BuddyLinkController diffing
        // already surfaces these events, so skip the push-driven notification to avoid a
        // double alert. When backgrounded/killed, this is the only path and it runs.
        if (AppForeground.isForeground) return
        when (type) {
            "request" -> BuddyNotifier.notifyRequestReceived(applicationContext)
            "accepted" -> BuddyNotifier.notifyRequestAccepted(applicationContext, actorName)
            "unlinked" -> BuddyNotifier.notifyUnlinked(applicationContext, actorName)
            else -> { /* unknown type — ignore */ }
        }
    }
}
