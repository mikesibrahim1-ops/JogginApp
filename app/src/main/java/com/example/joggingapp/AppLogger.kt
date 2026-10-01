package com.example.joggingapp

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

// ── Log categories ────────────────────────────────────────────────────────────

enum class LogCategory(val label: String, val emoji: String) {
    GPS     ("GPS",      "📍"),
    TRACKING("Tracking", "🏃"),
    SERVICE ("Service",  "⚙️"),
    SAVE    ("Save",     "💾"),
    NOTIF   ("Notif",    "🔔"),
    UI      ("UI",       "📱"),
    BACKUP  ("Backup",   "📦"),
    PROFILE ("Profile",  "👤"),
    ERROR   ("Error",    "❌"),
    INFO    ("Info",     "ℹ️"),
}

// ── Log entry ─────────────────────────────────────────────────────────────────

data class LogEntry(
    val timestamp: Long,
    val category: LogCategory,
    val message: String,
) {
    val formatted: String get() {
        val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
        return "[$ts] ${category.emoji} ${category.label}: $message"
    }
}

// ── AppLogger singleton ───────────────────────────────────────────────────────

object AppLogger {

    private const val MAX_ENTRIES    = 500   // in-memory ring buffer size
    private const val LOG_FILE_NAME  = "app_log.txt"
    private const val MAX_FILE_LINES = 2000  // trim file if it exceeds this

    // Thread-safe list — GPS callbacks and the main thread both write here
    private val entries = CopyOnWriteArrayList<LogEntry>()

    // ── Write ─────────────────────────────────────────────────────────────────

    fun log(context: Context, category: LogCategory, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), category, message)

        // Keep in-memory buffer capped at MAX_ENTRIES
        entries.add(entry)
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)

        // Append to file on a background thread so we never block the caller
        Thread {
            try {
                val file = File(context.filesDir, LOG_FILE_NAME)
                file.appendText(entry.formatted + "\n")

                // Trim file if it has grown too large
                val lines = file.readLines()
                if (lines.size > MAX_FILE_LINES) {
                    file.writeText(lines.takeLast(MAX_FILE_LINES).joinToString("\n") + "\n")
                }
            } catch (_: Exception) {}
        }.start()
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /** Returns the last [limit] in-memory entries, newest last. */
    fun getEntries(limit: Int = MAX_ENTRIES): List<LogEntry> {
        val all = entries.toList()
        return if (all.size <= limit) all else all.takeLast(limit)
    }

    /** Reads the full persisted log file as text. */
    fun readFile(context: Context): String {
        return try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) file.readText() else "(no log yet)"
        } catch (_: Exception) { "(failed to read log)" }
    }

    // ── Clear ─────────────────────────────────────────────────────────────────

    fun clear(context: Context) {
        entries.clear()
        try { File(context.filesDir, LOG_FILE_NAME).delete() } catch (_: Exception) {}
    }

    // ── Share ─────────────────────────────────────────────────────────────────

    /**
     * Copies the log file to a FileProvider-accessible location and fires
     * an ACTION_SEND intent so the user can send it via any app.
     */
    fun share(context: Context) {
        try {
            val src = File(context.filesDir, LOG_FILE_NAME)
            if (!src.exists()) {
                android.widget.Toast.makeText(context, "No log to share yet", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            // Copy to cache so FileProvider can serve it
            val dest = File(context.cacheDir, "joggin_log.txt")
            src.copyTo(dest, overwrite = true)

            val uri = FileProvider.getUriForFile(
                context, "com.example.joggingapp.fileprovider", dest)

            val deviceInfo = buildString {
                appendLine("=== Device Info ===")
                appendLine("Model   : ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("Android : ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine("App ver : ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}")
                appendLine("Log date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("==================")
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Joggin App Log")
                putExtra(Intent.EXTRA_TEXT, deviceInfo)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "Share log via…")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "Failed to share log: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
