package com.qing.hachimi.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private const val TAG = "Hachimi"
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "app.log"
    private const val CRASH_FILE_NAME = "crash.log"
    private const val MAX_FILE_SIZE = 1024 * 1024L
    private const val LOGCAT_CHUNK_LEN = 3500
    private const val MODULE_WIDTH = 18

    private var logDir: File? = null
    private var logFile: File? = null
    private var crashFile: File? = null
    private var initialized = false
    private var appContext: Context? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val lock = Any()

    /** Current navigation context – set by each screen for richer logs. */
    var currentScreen: String = "unknown"
        set(value) {
            if (value != field) {
                field = value
                debug("[NAV] Screen: $value")
            }
        }

    /** Timing helper: call [begin] before an operation, then [end] with the result. */
    private val timings = mutableMapOf<String, Long>()

    private data class ParsedMessage(
        val module: String,
        val message: String,
    )

    // ── Public API ──

    fun getLogFilePath(): String = logFile?.absolutePath ?: "(not initialized)"
    fun getCrashFilePath(): String = crashFile?.absolutePath ?: "(not initialized)"
    fun isInitialized(): Boolean = initialized

    fun debug(msg: String) = write("D", msg, null)
    fun info(msg: String) = write("I", msg, null)
    fun warn(msg: String, e: Throwable? = null) = write("W", msg, e)
    fun error(msg: String, e: Throwable? = null) = write("E", msg, e)

    /** Start a named timer. Call [endTimer] to log duration. */
    fun beginTimer(name: String) { synchronized(timings) { timings[name] = System.currentTimeMillis() } }

    /** Log the elapsed time since [beginTimer] was called. */
    fun endTimer(name: String, module: String = "PERF") {
        synchronized(timings) {
            val start = timings.remove(name) ?: return
            val elapsed = System.currentTimeMillis() - start
            write("D", "[$module] $name took ${elapsed}ms", null)
        }
    }

    fun clearLogs() {
        logDir?.listFiles()?.forEach { it.delete() }
        logFile = File(logDir, LOG_FILE_NAME)
        crashFile = File(logDir, CRASH_FILE_NAME)
    }

    fun readLogs(): String {
        val file = logFile ?: return "not initialized"
        return try { if (file.exists()) file.readText() else "(empty)" }
        catch (e: Exception) { "Read failed: ${e.message}" }
    }

    // ── Initialization ──

    fun init(context: Context) {
        if (initialized) return
        appContext = context

        logDir = File(context.filesDir, LOG_DIR_NAME).also { it.mkdirs() }
        logFile = File(logDir, LOG_FILE_NAME)
        crashFile = File(logDir, CRASH_FILE_NAME)
        initialized = true

        rotateIfNeeded()
        installCrashHandler()
        writeToFile(formatSessionHeader())
        info("[SYS] Device: ${Build.MANUFACTURER} ${Build.MODEL}, SDK ${Build.VERSION.SDK_INT}, ${Build.BRAND}")
    }

    // ── Crash Handler ──

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            pw.flush()
            val stackTrace = sw.toString()

            val crashReport = buildString {
                appendLine("=".repeat(88))
                appendLine("UNCAUGHT EXCEPTION")
                appendLine("time    : ${dateFormat.format(Date())}")
                appendLine("screen  : $currentScreen")
                appendLine("thread  : ${thread.name}")
                appendLine("type    : ${throwable::class.java.name}")
                appendLine("message : ${throwable.message ?: "(no message)"}")
                appendLine("stack   :")
                appendLine(stackTrace.trimEnd())
                appendLine("=".repeat(88))
            }

            synchronized(lock) {
                try {
                    crashFile?.appendText(crashReport + "\n")
                } catch (_: Exception) {}
            }
            Log.e(TAG, crashReport)

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    // ── Internal ──

    private fun isDebuggable(): Boolean {
        val ctx = appContext ?: return true
        return ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private fun write(level: String, msg: String, e: Throwable?) {
        if (level == "D" && initialized && !isDebuggable()) return

        val screenTag = if (currentScreen != "unknown") "[$currentScreen] " else ""
        val line = formatLine(level, "$screenTag$msg")

        if (line.length > LOGCAT_CHUNK_LEN) {
            var remaining = line
            var chunkIdx = 0
            while (remaining.isNotEmpty()) {
                val chunk = remaining.take(LOGCAT_CHUNK_LEN)
                remaining = remaining.drop(LOGCAT_CHUNK_LEN)
                logToLogcat(level, if (chunkIdx == 0) TAG else "$TAG[$chunkIdx]", chunk, e)
                chunkIdx++
            }
        } else {
            logToLogcat(level, TAG, line, e)
        }

        if (initialized) {
            writeToFile(line)
            e?.let { writeToFile(formatExceptionBlock(it)) }
        }
    }

    private fun formatSessionHeader(): String {
        val appCtx = appContext
        val versionName = try { appCtx?.packageManager?.getPackageInfo(appCtx.packageName, 0)?.versionName ?: "?" }
        catch (_: Exception) { "?" }
        val sep = "=".repeat(88)
        return buildString {
            appendLine(sep)
            appendLine("Hachimi Log Session")
            appendLine("started : ${dateFormat.format(Date())}")
            appendLine("version : $versionName")
            appendLine("device  : ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("file    : ${logFile?.absolutePath ?: "(not initialized)"}")
            appendLine("crash   : ${crashFile?.absolutePath ?: "(not initialized)"}")
            appendLine("format  : time | level | screen | module | message")
            append(sep)
        }
    }

    private fun formatLine(level: String, msg: String): String {
        val parsed = parseMessage(msg)
        val levelLabel = when (level) {
            "D" -> "DEBUG"
            "I" -> "INFO"
            "W" -> "WARN"
            "E" -> "ERROR"
            else -> level
        }
        return listOf(
            dateFormat.format(Date()),
            levelLabel.padEnd(5),
            parsed.module.take(MODULE_WIDTH).padEnd(MODULE_WIDTH),
            parsed.message
        ).joinToString(" | ")
    }

    private fun parseMessage(msg: String): ParsedMessage {
        Regex("^\\[([^]]+)]\\s*(.*)$").find(msg)?.let { match ->
            return ParsedMessage(
                module = normalizeModule(match.groupValues[1]),
                message = match.groupValues[2].ifBlank { msg }
            )
        }
        Regex("^([A-Za-z][A-Za-z0-9_ ]{1,32}):\\s*(.*)$").find(msg)?.let { match ->
            return ParsedMessage(
                module = normalizeModule(match.groupValues[1]),
                message = match.groupValues[2].ifBlank { msg }
            )
        }
        return ParsedMessage("APP", msg)
    }

    private fun normalizeModule(module: String): String = module
        .trim()
        .replace(Regex("\\s+"), "_")
        .uppercase(Locale.ROOT)

    private fun formatExceptionBlock(e: Throwable): String {
        val sep = "-".repeat(88)
        return buildString {
            appendLine(sep)
            appendLine("Exception")
            appendLine("type    : ${e::class.java.name}")
            appendLine("message : ${e.message ?: "(no message)"}")
            appendLine("stack   :")
            appendLine(Log.getStackTraceString(e).trimEnd())
            append(sep)
        }
    }

    private fun logToLogcat(level: String, tag: String, msg: String, e: Throwable?) {
        when (level) {
            "D" -> Log.d(tag, msg)
            "I" -> Log.i(tag, msg)
            "W" -> { Log.w(tag, msg); e?.let { Log.w(tag, it) } }
            "E" -> { Log.e(tag, msg); e?.let { Log.e(tag, msg, it) } }
        }
    }

    private fun writeToFile(text: String) {
        synchronized(lock) {
            rotateIfNeeded()
            try { FileWriter(logFile, true).use { it.appendLine(text) } }
            catch (_: Exception) { }
        }
    }

    private fun rotateIfNeeded() {
        val f = logFile ?: return
        if (f.exists() && f.length() > MAX_FILE_SIZE) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            f.renameTo(File(logDir, "app_$stamp.log"))
        }
    }
}
