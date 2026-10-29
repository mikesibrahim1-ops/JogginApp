package com.example.joggingapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** jog_prefs keys for Options → Safety alert. */
const val PREF_SAFETY_ENABLED = "safety_alert_enabled"
const val PREF_SAFETY_NUMBER = "safety_contact_number"
const val PREF_SAFETY_NAME = "safety_contact_name"

/**
 * Pure "stopped moving" decision (JVM-tested in SafetyCheckTest). The tracking service
 * calls [next] on a timer; it returns what to do given the time since the last movement.
 *
 *  IDLE ──(still ≥ stopMs)──▶ PROMPTING ──(no response for promptMs)──▶ ALERTED
 *    ▲            │ movement / "I'm OK"                    │ movement
 *    └────────────┴─────────────────────────────────────────┘
 *
 * "I'm OK" snoozes: the stop timer restarts from that moment ([okAtMs]). ALERTED never
 * re-sends until the user moves again.
 */
object SafetyCheck {
    const val STOP_MS = 3 * 60_000L
    const val PROMPT_MS = 60_000L

    enum class Phase { IDLE, PROMPTING, ALERTED }
    enum class Action { NONE, SHOW_PROMPT, SEND_ALERT, CLEAR_PROMPT }

    data class State(val phase: Phase = Phase.IDLE, val promptAtMs: Long = 0L)

    fun next(state: State, nowMs: Long, lastMovementMs: Long, okAtMs: Long,
             stopMs: Long = STOP_MS, promptMs: Long = PROMPT_MS): Pair<State, Action> {
        val stillSince = maxOf(lastMovementMs, okAtMs)
        val stillFor = nowMs - stillSince
        return when (state.phase) {
            Phase.IDLE ->
                if (stillFor >= stopMs) State(Phase.PROMPTING, nowMs) to Action.SHOW_PROMPT
                else state to Action.NONE
            Phase.PROMPTING -> when {
                stillSince > state.promptAtMs -> State() to Action.CLEAR_PROMPT        // moved or "I'm OK"
                nowMs - state.promptAtMs >= promptMs -> State(Phase.ALERTED, state.promptAtMs) to Action.SEND_ALERT
                else -> state to Action.NONE
            }
            Phase.ALERTED ->
                if (lastMovementMs > state.promptAtMs) State() to Action.CLEAR_PROMPT  // moving again
                else state to Action.NONE
        }
    }

    /** The SMS body: who, that they stopped and didn't respond, and a map link. */
    fun smsText(S: AppStrings, userName: String, lat: Double?, lon: Double?): String {
        val link = if (lat != null && lon != null) "https://maps.google.com/?q=%.6f,%.6f".format(java.util.Locale.US, lat, lon) else null
        return S.safetySmsBody(userName.ifBlank { S.safetySomeone }, link ?: S.safetyNoLocation)
    }
}

/** Android side of the safety alert: the "Are you OK?" notification and sending the SMS. */
object SafetyAlerter {
    private const val CHANNEL_ID = "joggin_safety"
    private const val NOTIF_ID = 4242

    fun hasSmsPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Configured and allowed: switch on, a contact number saved, SMS permission granted. */
    fun isReady(context: Context): Boolean {
        val p = context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
        return p.getBoolean(PREF_SAFETY_ENABLED, false) &&
            !p.getString(PREF_SAFETY_NUMBER, null).isNullOrBlank() && hasSmsPermission(context)
    }

    /** Sends [text] to the saved contact. Returns true if handed to the SMS stack. */
    fun sendSms(context: Context, text: String): Boolean {
        val number = context.getSharedPreferences("jog_prefs", Context.MODE_PRIVATE)
            .getString(PREF_SAFETY_NUMBER, null)
        if (number.isNullOrBlank() || !hasSmsPermission(context)) return false
        return try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
            AppLogger.log(context, LogCategory.SERVICE, "Safety alert: SMS sent to saved contact")
            true
        } catch (e: Exception) {
            AppLogger.log(context, LogCategory.ERROR, "Safety alert: SMS failed: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** High-priority "Are you OK?" prompt with an "I'm OK" action that hits [okIntent]. */
    fun showPrompt(context: Context, S: AppStrings, okIntent: PendingIntent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, S.safetyChannelName,
                NotificationManager.IMPORTANCE_HIGH).apply { enableVibration(true) })
        }
        val open = PendingIntent.getActivity(context, 7, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(S.safetyPromptTitle)
            .setContentText(S.safetyPromptBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(S.safetyPromptBody))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVibrate(longArrayOf(0, 600, 300, 600, 300, 600))
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, S.safetyImOk, okIntent)
            .build()
        try { nm.notify(NOTIF_ID, n) } catch (e: SecurityException) {
            AppLogger.log(context, LogCategory.ERROR, "Safety alert: prompt notification blocked: ${e.message}")
        }
    }

    fun clearPrompt(context: Context) {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }
}
