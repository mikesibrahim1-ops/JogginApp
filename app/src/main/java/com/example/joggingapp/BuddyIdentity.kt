package com.example.joggingapp

import android.content.Context
import java.util.UUID

/**
 * The device's stable, app-owned unique Buddy Link ID ("App ID").
 *
 * Design (D4, Req 2):
 *  - Generated the FIRST time Buddy Link is enabled (NOT on first app launch), so the
 *    app stays offline-first and the existing first-launch flow is untouched.
 *  - Persists for the life of the install and does not change unless explicitly reset.
 *  - Is an app-generated UUID (not the Firebase anonymous uid), so it can be backed up
 *    and restored to a new device via the existing Backup/Restore file.
 */
object BuddyIdentity {

    private const val PREFS = "jog_prefs"
    private const val KEY_APP_ID = "buddy_app_id"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Returns the existing App ID, or null if one has never been provisioned. */
    fun getId(context: Context): String? = prefs(context).getString(KEY_APP_ID, null)

    /** True if this install already has a Buddy Link identity. */
    fun hasId(context: Context): Boolean = getId(context) != null

    /**
     * Ensures an App ID exists, generating one on first call. Call this at the moment
     * Buddy Link is first enabled. Returns the (possibly newly created) App ID.
     */
    fun ensureId(context: Context): String {
        getId(context)?.let { return it }
        val id = generateId()
        prefs(context).edit().putString(KEY_APP_ID, id).apply()
        AppLogger.log(context, LogCategory.PROFILE, "Buddy Link identity provisioned")
        return id
    }

    /**
     * Adopts a specific App ID (used by Backup/Restore so a restored install keeps its
     * original identity and links). Overwrites any existing local ID.
     */
    fun adoptId(context: Context, appId: String) {
        if (appId.isBlank()) return
        prefs(context).edit().putString(KEY_APP_ID, appId.trim()).apply()
        AppLogger.log(context, LogCategory.PROFILE, "Buddy Link identity adopted from backup")
    }

    /** Clears the identity (destructive — used by "delete my Buddy Link data"). Req 8.4. */
    fun reset(context: Context) {
        prefs(context).edit().remove(KEY_APP_ID).apply()
        AppLogger.log(context, LogCategory.PROFILE, "Buddy Link identity reset")
    }

    /** A compact, URL-safe unique id (UUID without dashes). */
    private fun generateId(): String = UUID.randomUUID().toString().replace("-", "")
}
