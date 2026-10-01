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
            writeReportFile(context)
            originalHandler?.uncaughtException(thread, throwable)
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
