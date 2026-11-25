package com.example.joggingapp

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom
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
    // Recovery key (2026-11-24): a random secret that proves ownership of the App ID when a
    // backup is restored onto a new install. Only its SHA-256 goes to Firestore
    // (users/{appId}/private/self.recoveryHash). jog_prefs is excluded from Android
    // backup, so the key only leaves the phone inside the user's own Joggin backup file.
    private const val KEY_RECOVERY = "buddy_recovery_key"
    // Next key during a restore, kept until the server has accepted its hash.
    private const val KEY_RECOVERY_PENDING = "buddy_recovery_key_pending"
    // Set by adoptId: this ID came from a backup and hasn't been proven on this install yet.
    private const val KEY_RESTORE_PENDING = "buddy_restore_pending"

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
     * original identity and links). Overwrites any existing local ID. [recoveryKey] is the
     * backup's proof of ownership; without it the server refuses the ID and the next
     * session falls back to a fresh one ([replaceAfterFailedRestore]).
     */
    fun adoptId(context: Context, appId: String, recoveryKey: String? = null) {
        if (appId.isBlank()) return
        val editor = prefs(context).edit()
            .putString(KEY_APP_ID, appId.trim())
            .remove(KEY_RECOVERY_PENDING)
            .putBoolean(KEY_RESTORE_PENDING, true)
        if (isValidKey(recoveryKey)) editor.putString(KEY_RECOVERY, recoveryKey) else editor.remove(KEY_RECOVERY)
        editor.commit()
        AppLogger.log(context, LogCategory.PROFILE,
            "Buddy Link identity adopted from backup (recovery key ${if (isValidKey(recoveryKey)) "present" else "missing"})")
    }

    /** Clears the identity (destructive — used by "delete my Buddy Link data"). Req 8.4. */
    fun reset(context: Context) {
        prefs(context).edit().remove(KEY_APP_ID).remove(KEY_RECOVERY).remove(KEY_RECOVERY_PENDING)
            .remove(KEY_RESTORE_PENDING).commit()
        AppLogger.log(context, LogCategory.PROFILE, "Buddy Link identity reset")
    }

    // ── Recovery key ─────────────────────────────────────────────────────────────

    fun getRecoveryKey(context: Context): String? =
        prefs(context).getString(KEY_RECOVERY, null)?.takeIf { isValidKey(it) }

    /** The current recovery key, creating one if this install has none yet. */
    fun ensureRecoveryKey(context: Context): String {
        getRecoveryKey(context)?.let { return it }
        val key = generateKey()
        prefs(context).edit().putString(KEY_RECOVERY, key).commit()
        return key
    }

    fun pendingRecoveryKey(context: Context): String? =
        prefs(context).getString(KEY_RECOVERY_PENDING, null)?.takeIf { isValidKey(it) }

    /** Creates and stores (as pending) the key a restore rotates to. */
    fun newPendingRecoveryKey(context: Context): String {
        val key = generateKey()
        prefs(context).edit().putString(KEY_RECOVERY_PENDING, key).commit()
        return key
    }

    /** The server accepted the pending key's hash: make it the current key. */
    fun promotePendingRecoveryKey(context: Context) {
        val pending = pendingRecoveryKey(context) ?: return
        prefs(context).edit().putString(KEY_RECOVERY, pending).remove(KEY_RECOVERY_PENDING).commit()
    }

    fun isRestorePending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RESTORE_PENDING, false)

    fun clearRestorePending(context: Context) {
        prefs(context).edit().remove(KEY_RESTORE_PENDING).commit()
    }

    /**
     * A restored ID could not be proven (old backup without a key, or a key already used).
     * Drop it and start a fresh identity; the user re-adds their buddies.
     */
    fun replaceAfterFailedRestore(context: Context): String {
        val id = generateId()
        prefs(context).edit().putString(KEY_APP_ID, id).remove(KEY_RECOVERY).remove(KEY_RECOVERY_PENDING)
            .remove(KEY_RESTORE_PENDING).commit()
        AppLogger.log(context, LogCategory.PROFILE, "Buddy Link restored identity refused; new identity created")
        return id
    }

    /** Lowercase hex SHA-256, matching the Firestore rules' hashing.sha256().toHexString().lower(). */
    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun isValidKey(key: String?): Boolean =
        key != null && key.length == 64 && key.all { it in '0'..'9' || it in 'a'..'f' }

    /** 256 random bits as 64 lowercase hex chars. */
    private fun generateKey(): String =
        ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    /** A compact, URL-safe unique id (UUID without dashes). */
    private fun generateId(): String = UUID.randomUUID().toString().replace("-", "")
}
