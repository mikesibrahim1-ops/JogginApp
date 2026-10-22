package com.example.joggingapp

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogger {
    private const val LOG_FILE_NAME = "crash_log.txt"
    private const val REPORT_FILE_NAME = "crash_report.txt"
    private const val REPORT_DIR_NAME = "CrashReports"

    fun install(context: Context) {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log(context, throwable, thread)
            logToAppLog(context, throwable, thread)
            writeReportFile(context)
            originalHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * V1.59: also surface the crash inline in the MAIN in-app log (app_log.txt via
     * [AppLogger]) so a future tail shows it next to the lifecycle breadcrumbs. Concise
     * one-liner: exception class + message + top stack frame. The existing crash_log.txt
     * behavior is kept unchanged. Best-effort — never throws from the crash handler.
     */
    private fun logToAppLog(context: Context, throwable: Throwable, thread: Thread?) {
        try {
            val top = throwable.stackTrace.firstOrNull()?.let {
                " at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})"
            } ?: ""
            AppLogger.log(context, LogCategory.ERROR,
                "CRASH on ${thread?.name ?: "unknown"}: " +
                    "${throwable.javaClass.name}: ${throwable.message}$top")
        } catch (_: Exception) {
        }
    }

    fun log(context: Context, throwable: Throwable, thread: Thread? = null) {
        try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            pw.flush()
            val content = buildString {
                appendLine("[$timestamp]")
                appendLine("Thread: ${thread?.name ?: "unknown"}")
                appendLine(sw.toString())
                appendLine("---")
            }
            logFile.appendText(content)
        } catch (_: Exception) {
        }
    }

    fun readLog(context: Context): String? {
        return try {
            val logFile = File(context.filesDir, LOG_FILE_NAME)
            if (logFile.exists()) logFile.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    fun writeReportFile(context: Context): File? {
        return try {
            val log = readLog(context) ?: "No crash log available."
            val dir = File(context.getExternalFilesDir(null), REPORT_DIR_NAME)
            if (!dir.exists()) dir.mkdirs()
            val reportFile = File(dir, REPORT_FILE_NAME)
            reportFile.writeText(log)
            reportFile
        } catch (_: Exception) {
            null
        }
    }

}
