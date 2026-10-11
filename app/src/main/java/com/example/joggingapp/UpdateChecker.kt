package com.example.joggingapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update check (v1.47). JogginApp is sideloaded (not on the Play Store), so we
 * check the GitHub Releases API for a newer version and, if found, point the user at the
 * direct APK download. No silent/in-app install — just an ACTION_VIEW to the download.
 *
 * Design notes:
 *  - [isNewerVersion] is a PURE function (no Android deps) so it is unit-tested on the JVM.
 *  - [checkLatest] does the network call OFF the main thread (Dispatchers.IO), uses a plain
 *    HttpURLConnection (no new dependency), short timeouts, and fails gracefully: callers
 *    distinguish "up to date", "update available", and "could not check".
 *  - Auto-check is throttled to ~once/day via a timestamp in SharedPreferences "jog_prefs".
 */
object UpdateChecker {

    private const val PREFS = "jog_prefs"
    private const val KEY_LAST_CHECK = "update_last_check_ms"
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    private const val RELEASES_API =
        "https://api.github.com/repos/mikesibrahim1-ops/JogginApp/releases/latest"
    /** One-tap direct APK download (resolves to the latest release asset). */
    const val APK_URL = "https://github.com/mikesibrahim1-ops/JogginApp/releases/latest/download/joggin.apk"
    /** Install page fallback if the APK URL cannot be opened. */
    const val INSTALL_PAGE_URL = "https://joggin-a69a7.web.app/get"

    /** Result of a check. [latestVersion] is set only when [updateAvailable] is true. */
    data class Result(
        val updateAvailable: Boolean,
        val latestVersion: String? = null,
        val failed: Boolean = false
    )

    /**
     * Semantic-version comparison. Returns true when [latest] is strictly newer than
     * [current]. Both may carry a leading "V"/"v". Compares dot-separated numeric parts
     * (so "1.10" > "1.9", "2.0" > "1.99"). Malformed input yields false (never throws).
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val l = parseParts(latest) ?: return false
        val c = parseParts(current) ?: return false
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    /** Splits a version string into numeric parts, or null if it has no usable digits. */
    private fun parseParts(raw: String): List<Int>? {
        val trimmed = raw.trim().removePrefix("V").removePrefix("v").trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(".")
        val nums = ArrayList<Int>(parts.size)
        for (p in parts) {
            val n = p.trim().toIntOrNull() ?: return null
            nums.add(n)
        }
        return nums.ifEmpty { null }
    }

    /** True if an auto-check is due (never checked, or more than ~a day ago). */
    fun isAutoCheckDue(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        return nowMs - last >= DAY_MS
    }

    private fun recordCheck(context: Context, nowMs: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_CHECK, nowMs).apply()
    }

    /**
     * Queries the GitHub Releases API and compares against [currentVersion]
     * (BuildConfig.VERSION_NAME). Runs on IO. [persistTimestamp] records the check time
     * (set false for a manual check so it doesn't disturb the auto-check throttle).
     * Never throws — network/parse/rate-limit errors return a failed Result.
     */
    suspend fun checkLatest(
        context: Context,
        currentVersion: String,
        persistTimestamp: Boolean = true
    ): Result = withContext(Dispatchers.IO) {
        if (persistTimestamp) recordCheck(context)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 6000
                readTimeout = 7000
            }
            val code = conn.responseCode
            // 403 is the unauthenticated rate-limit case → treat as a silent try-later.
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext Result(updateAvailable = false, failed = true)
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank { json.optString("name") }
            if (tag.isBlank()) return@withContext Result(updateAvailable = false, failed = true)
            val newer = isNewerVersion(tag, currentVersion)
            Result(updateAvailable = newer, latestVersion = tag.trim().removePrefix("V").removePrefix("v"))
        } catch (_: Exception) {
            Result(updateAvailable = false, failed = true)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Opens the direct APK download. Falls back to the install page if that can't be
     * launched. Never attempts a silent install.
     */
    fun openUpdate(context: Context) {
        if (!openUrl(context, APK_URL)) {
            openUrl(context, INSTALL_PAGE_URL)
        }
    }

    private fun openUrl(context: Context, url: String): Boolean = try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }
}
