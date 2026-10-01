package com.example.joggingapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Posts one-shot local notifications for Buddy Link social events — a link request
 * received, a request accepted, and a buddy who unlinked / stopped sharing (Req 7.1–7.4).
 *
 * This is the v1 in-app delivery path (design open item B7): [BuddyLinkController]
 * observes the Firestore link flows while the app process is alive and calls these
 * methods when it detects a change. It does NOT require Cloud Functions or a
 * FirebaseMessagingService; server-side push for events that arrive while the app is
 * killed is a future enhancement (see design §12 / tasks 7.3).
 *
 * Distinct from the ongoing "broadcast on" service notification: that lives on the
 * low-importance `joggin_buddy` channel; these transient social alerts use a separate
 * default-importance channel so they actually surface, and are dismissible.
 *
 * All calls are safe no-ops when POST_NOTIFICATIONS is not granted (API 33+), so the
 * caller never needs to guard.
 */
object BuddyNotifier {

    private const val CHANNEL_ID = "joggin_buddy_events"
    private const val CHANNEL_ID_LOW = "joggin_buddy_events_low"
    // Distinct id space from the broadcast service (NOTIF_ID = 2) and workout notifs.
    private const val NOTIF_ID_REQUEST = 3001
    private const val NOTIF_ID_ACCEPTED = 3002
    private const val NOTIF_ID_UNLINKED = 3003
    private const val NOTIF_ID_LOCATION_SHARED = 3004

    /** A link request was received from someone. */
    fun notifyRequestReceived(context: Context) {
        val s = stringsFor(loadLanguage(context))
        post(context, NOTIF_ID_REQUEST, s.buddyNotifRequestTitle, s.buddyNotifRequestBody)
    }

    /** A buddy accepted my link request. */
    fun notifyRequestAccepted(context: Context, buddyName: String) {
        val s = stringsFor(loadLanguage(context))
        post(context, NOTIF_ID_ACCEPTED, s.buddyNotifAcceptedTitle, s.buddyNotifAcceptedBody(buddyName))
    }

    /** A buddy unlinked / stopped sharing (Req 4.5, 7.4). */
    fun notifyUnlinked(context: Context, buddyName: String) {
        val s = stringsFor(loadLanguage(context))
        post(context, NOTIF_ID_UNLINKED, s.buddyNotifUnlinkedTitle, s.buddyUnlinkedWarning(buddyName))
    }

    /**
     * A buddy pulled my location on demand (G6 decision: notify, not silent). Kept
     * low-key on a separate low-importance channel so it's transparent without being a
     * nuisance — the located party sees that someone actively requested their position.
     */
    fun notifyLocationShared(context: Context) {
        val s = stringsFor(loadLanguage(context))
        post(
            context, NOTIF_ID_LOCATION_SHARED,
            s.buddyNotifLocationSharedTitle, s.buddyNotifLocationSharedBody,
            channelId = createLowChannel(context), lowKey = true
        )
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun post(
        context: Context, id: Int, title: String, body: String,
        channelId: String = createChannel(context), lowKey: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) return
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, id, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(if (lowKey) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(lowKey)
            .build()
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(id, notif)
        } catch (_: Exception) {
            // Never let a notification failure affect feature logic (Req 10).
        }
    }

    private fun createChannel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val s = stringsFor(loadLanguage(context))
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID, s.buddyEventsChannelName, NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }
        return CHANNEL_ID
    }

    /** Low-importance channel for transparent, non-nuisance alerts (on-demand share). */
    private fun createLowChannel(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val s = stringsFor(loadLanguage(context))
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID_LOW, s.buddyEventsLowChannelName, NotificationManager.IMPORTANCE_LOW
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
        return CHANNEL_ID_LOW
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        else true
}
