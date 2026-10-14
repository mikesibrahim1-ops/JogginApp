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
    // `internal` so BuddyInviteActionReceiver can cancel the request notification.
    internal const val NOTIF_ID_REQUEST = 3001
    private const val NOTIF_ID_ACCEPTED = 3002
    private const val NOTIF_ID_UNLINKED = 3003
    private const val NOTIF_ID_LOCATION_SHARED = 3004
    private const val NOTIF_ID_ONLINE = 3005

    /** Intent extra carrying the appId of the buddy to focus on the map when a
     *  "buddy online" notification is tapped. Read by MainActivity.handleIntent. */
    const val EXTRA_FOCUS_BUDDY_ID = "com.example.joggingapp.extra.FOCUS_BUDDY_ID"

    /**
     * A link request was received. When a [linkId] is supplied the notification carries
     * Accept / Ignore action buttons wired to [BuddyInviteActionReceiver]; the title uses
     * the sender's [fromName] ("<name> wants to buddy up"), falling back to a short code
     * form of the linkId when the name is blank.
     */
    fun notifyRequestReceived(context: Context, linkId: String? = null, fromName: String? = null) {
        val s = stringsFor(loadLanguage(context))
        val name = fromName?.ifBlank { null }
        val title = if (name != null || linkId != null) {
            s.buddyNotifRequestFrom(name ?: buddyShortId(linkId ?: ""))
        } else {
            s.buddyNotifRequestTitle
        }
        val actions = if (linkId != null) {
            listOf(
                action(context, BuddyInviteActionReceiver.ACTION_ACCEPT, linkId, s.buddyInviteAccept),
                action(context, BuddyInviteActionReceiver.ACTION_IGNORE, linkId, s.buddyInviteIgnore)
            )
        } else emptyList()
        post(context, NOTIF_ID_REQUEST, title, s.buddyNotifRequestBody, actions = actions)
    }

    /** Build an explicit-intent broadcast action for the Accept/Ignore buttons. */
    private fun action(
        context: Context, intentAction: String, linkId: String, label: String
    ): NotificationCompat.Action {
        val intent = Intent(context, BuddyInviteActionReceiver::class.java).apply {
            action = intentAction
            putExtra(BuddyInviteActionReceiver.EXTRA_LINK_ID, linkId)
        }
        // Distinct request codes per (action, link) so Accept and Ignore don't collide.
        val requestCode = (intentAction.hashCode() * 31) xor linkId.hashCode()
        val pending = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(0, label, pending).build()
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

    /**
     * A buddy came online (started sharing a live fix). Reuses the default-importance
     * [CHANNEL_ID] (same as request/accepted/unlinked). Tapping it opens the app and
     * routes to the map focused on this buddy via [EXTRA_FOCUS_BUDDY_ID] (handled in
     * MainActivity.onNewIntent/handleIntent — same mechanism as the invite deep-link).
     */
    fun notifyBuddyOnline(context: Context, buddyName: String, buddyAppId: String? = null) {
        val s = stringsFor(loadLanguage(context))
        post(
            context, NOTIF_ID_ONLINE,
            s.buddyNotifOnlineTitle, s.buddyNotifOnlineBody(buddyName),
            focusBuddyId = buddyAppId
        )
    }

    // ── Internals ───────────────────────────────────────────────────────────────

    private fun post(
        context: Context, id: Int, title: String, body: String,
        channelId: String = createChannel(context), lowKey: Boolean = false,
        actions: List<NotificationCompat.Action> = emptyList(),
        focusBuddyId: String? = null
    ) {
        if (!hasNotificationPermission(context)) return
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (focusBuddyId != null) putExtra(EXTRA_FOCUS_BUDDY_ID, focusBuddyId)
        }
        val pending = PendingIntent.getActivity(
            context, id, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, channelId)
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
        actions.forEach { builder.addAction(it) }
        val notif = builder.build()
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
