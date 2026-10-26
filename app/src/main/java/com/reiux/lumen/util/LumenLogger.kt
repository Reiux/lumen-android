package com.reiux.lumen.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import com.reiux.lumen.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

enum class LogLevel(val priority: Int, val shortCode: String) {
    VERBOSE(2, "V"),
    DEBUG(3, "D"),
    INFO(4, "I"),
    WARN(5, "W"),
    ERROR(6, "E")
}

enum class LogCategory(val displayName: String) {
    UI("UI"),
    ANIM("Anim"),
    PERF("Perf"),
    INTERACTION("Interaction"),
    NETWORK("Network"),
    UPLOAD("Upload"),
    LIFECYCLE("Lifecycle"),
    STORAGE("Storage"),
    WATCHER("Watcher"),
    OTHER("Other")
}

enum class LogKind {
    GENERAL,
    RUN_START,
    FILE_RESULT,
    RUN_SUMMARY,
    BLOCKED,
    ACCOUNT,
    ERROR,
    SYSTEM
}

enum class UploadOutcome {
    NONE,
    UPLOADED,
    SKIPPED_DUPLICATE,
    FAILED
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val threadName: String,
    val opId: String? = null,
    val category: LogCategory = LogCategory.OTHER,
    val message: String,
    val throwableText: String? = null,
    val kind: LogKind = LogKind.GENERAL,
    val runId: String? = null,
    val fileName: String? = null,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val outcome: UploadOutcome = UploadOutcome.NONE,
    val reasonCode: String? = null,
    val userMessage: String? = null,
    val suggestedAction: String? = null,
    val repeatCount: Int = 1,
    val isMock: Boolean = false
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))

    val opString: String
        get() = if (!opId.isNullOrEmpty()) {
            if (opId.startsWith("op#")) opId else "op#$opId"
        } else {
            "-"
        }

    val fullFormattedText: String
        get() {
            val repeatStr = if (repeatCount > 1) " (x$repeatCount)" else ""
            val base = "[$formattedTime] [${level.name}/$tag] [$threadName] [$opString] $message$repeatStr"
            return if (!throwableText.isNullOrEmpty()) "$base\n$throwableText" else base
        }
}

object LumenLogger {
    private const val MAX_IN_MEMORY_LOGS = 3000
    private const val ROTATING_FILE_COUNT = 5
    private const val MAX_FILE_SIZE_BYTES = 2L * 1024L * 1024L // 2 MB
    private const val BATCH_EMIT_DELAY_MS = 250L

    private var nextId = 0L
    private val droppedCounter = AtomicLong(0L)

    private val memoryLogBuffer = ArrayDeque<LogEntry>()
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _droppedEvents = MutableStateFlow(0L)
    val droppedEvents: StateFlow<Long> = _droppedEvents.asStateFlow()

    var isVerboseEnabled: Boolean = BuildConfig.DEBUG
        private set

    var devDiagnosticsEnabled: Boolean = BuildConfig.DEBUG
        private set

    val activeCategories = ConcurrentHashMap<LogCategory, Boolean>().apply {
        LogCategory.entries.forEach { put(it, true) }
    }

    private var isFileLoggingEnabled = false
    private var logDir: File? = null
    private var currentFileIndex = 0
    private val scope = CoroutineScope(Dispatchers.IO)
    private var snapshotJob: Job? = null
    private var batchEmitJob: Job? = null
    private var appContext: Context? = null

    private var lastSnapshotMemMb = -1L
    private var lastSnapshotThreads = -1

    // Redaction patterns
    private val emailPattern = Pattern.compile("(?i)[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")
    private val bearerPattern = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9_\\-\\.]+")
    private val sensitiveParamPattern = Pattern.compile("(?i)(token|masterToken|credentialString|upload_token|password|auth|secret)=([^&\\s]+)")

    fun init(context: Context, saveLogsEnabled: Boolean, isDevDiagnostics: Boolean = BuildConfig.DEBUG) {
        appContext = context.applicationContext
        logDir = File(context.cacheDir, "logs").apply { mkdirs() }
        devDiagnosticsEnabled = isDevDiagnostics
        isVerboseEnabled = isDevDiagnostics || BuildConfig.DEBUG
        isFileLoggingEnabled = saveLogsEnabled || BuildConfig.DEBUG

        installCrashHandler()
        startPeriodicSnapshot()
        startBatchEmitter()

        // Append Session Start Marker to file log
        writeSessionStartMarker()

        i("LumenLogger") {
            "LumenLogger initialized (verbose=$isVerboseEnabled, fileLogging=$isFileLoggingEnabled, devDiagnostics=$devDiagnosticsEnabled, cacheDir=${logDir?.absolutePath})"
        }
    }

    private fun writeSessionStartMarker() {
        val dir = logDir ?: return
        try {
            val file = File(dir, "lumen_$currentFileIndex.log")
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            FileWriter(file, true).use { writer ->
                writer.appendLine("=== SESSION STARTED: $dateStr ===")
            }
        } catch (_: Exception) {}
    }

    private fun startBatchEmitter() {
        batchEmitJob?.cancel()
        batchEmitJob = scope.launch {
            while (isActive) {
                delay(BATCH_EMIT_DELAY_MS)
                flushMemoryBufferToStateFlow()
            }
        }
    }

    private fun flushMemoryBufferToStateFlow() {
        synchronized(memoryLogBuffer) {
            _logs.value = memoryLogBuffer.toList()
        }
    }

    fun setDevDiagnostics(enabled: Boolean, context: Context? = null) {
        devDiagnosticsEnabled = enabled
        isVerboseEnabled = enabled || BuildConfig.DEBUG
        isFileLoggingEnabled = enabled || isFileLoggingEnabled
        context?.let { appContext = it.applicationContext }
        i("LumenLogger") { "devDiagnosticsEnabled set to: $enabled (isVerboseEnabled=$isVerboseEnabled, isFileLoggingEnabled=$isFileLoggingEnabled)" }
    }

    fun setFileLogging(enabled: Boolean, context: Context) {
        appContext = context.applicationContext
        isFileLoggingEnabled = enabled || BuildConfig.DEBUG
        if (logDir == null) {
            logDir = File(context.cacheDir, "logs").apply { mkdirs() }
        }
        i("LumenLogger") { "File logging changed: $enabled (dir=${logDir?.absolutePath})" }
    }

    fun setCategoryEnabled(category: LogCategory, enabled: Boolean) {
        activeCategories[category] = enabled
    }

    fun isCategoryEnabled(category: LogCategory): Boolean = activeCategories[category] ?: true

    // --- Redaction ---
    fun redact(text: String): String {
        var result = text
        val emailMatcher = emailPattern.matcher(result)
        val emailSb = StringBuffer()
        while (emailMatcher.find()) {
            val fullEmail = emailMatcher.group()
            val atIdx = fullEmail.indexOf('@')
            val masked = if (atIdx > 1) {
                fullEmail.substring(0, 1) + "***@" + fullEmail.substring(atIdx + 1)
            } else {
                "***@" + fullEmail.substring(atIdx + 1)
            }
            emailMatcher.appendReplacement(emailSb, java.util.regex.Matcher.quoteReplacement(masked))
        }
        emailMatcher.appendTail(emailSb)
        result = emailSb.toString()

        result = bearerPattern.matcher(result).replaceAll("$1[REDACTED]")
        result = sensitiveParamPattern.matcher(result).replaceAll("$1=[REDACTED]")

        return result
    }

    // --- Category Resolver ---
    private fun inferCategory(tag: String): LogCategory {
        return when (tag.lowercase()) {
            "ui", "aboutui", "backupui", "foldersui", "accountsui", "settingsui", "nav", "navbar", "navrail" -> LogCategory.UI
            "anim", "animation" -> LogCategory.ANIM
            "perf", "performance", "recomp", "jank" -> LogCategory.PERF
            "interaction", "gesture", "click", "touch" -> LogCategory.INTERACTION
            "network", "googlephotos", "googlephotosclient", "auth", "googleauth", "googleauthclient", "http" -> LogCategory.NETWORK
            "upload", "uploadengine", "uploadrepository", "uploader", "backup" -> LogCategory.UPLOAD
            "lifecycle", "applifecycle", "layout" -> LogCategory.LIFECYCLE
            "storage", "datastore", "db", "hashcache" -> LogCategory.STORAGE
            "watcher", "folderwatcher", "folderwatchermanager" -> LogCategory.WATCHER
            else -> LogCategory.OTHER
        }
    }

    private fun shouldLog(level: LogLevel, tag: String): Boolean {
        // Demoted categories in non-dev release builds
        val category = inferCategory(tag)
        if (!isVerboseEnabled && !devDiagnosticsEnabled) {
            if (category == LogCategory.ANIM || category == LogCategory.INTERACTION || category == LogCategory.STORAGE) {
                return false
            }
            if (level.priority < LogLevel.INFO.priority) {
                return false
            }
        }
        return true
    }

    fun log(
        level: LogLevel,
        tag: String,
        opId: String? = null,
        tr: Throwable? = null,
        kind: LogKind = LogKind.GENERAL,
        runId: String? = null,
        fileName: String? = null,
        sizeBytes: Long? = null,
        durationMs: Long? = null,
        outcome: UploadOutcome = UploadOutcome.NONE,
        reasonCode: String? = null,
        userMessage: String? = null,
        suggestedAction: String? = null,
        isMock: Boolean = false,
        messageProvider: () -> String
    ) {
        if (!shouldLog(level, tag)) return

        val rawMessage = try { messageProvider() } catch (e: Exception) { "<Log evaluation failed: ${e.message}>" }
        val redactedMessage = redact(rawMessage)
        val threadName = Thread.currentThread().name
        val category = inferCategory(tag)

        emitLogEntry(
            level = level,
            tag = tag,
            threadName = threadName,
            opId = opId,
            category = category,
            message = redactedMessage,
            throwable = tr,
            kind = kind,
            runId = runId,
            fileName = fileName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            outcome = outcome,
            reasonCode = reasonCode,
            userMessage = userMessage,
            suggestedAction = suggestedAction,
            isMock = isMock
        )
    }

    private fun emitLogEntry(
        level: LogLevel,
        tag: String,
        threadName: String,
        opId: String?,
        category: LogCategory,
        message: String,
        throwable: Throwable?,
        kind: LogKind = LogKind.GENERAL,
        runId: String? = null,
        fileName: String? = null,
        sizeBytes: Long? = null,
        durationMs: Long? = null,
        outcome: UploadOutcome = UploadOutcome.NONE,
        reasonCode: String? = null,
        userMessage: String? = null,
        suggestedAction: String? = null,
        isMock: Boolean = false
    ) {
        val throwableText = throwable?.let { redact(buildStackTraceString(it)) }

        synchronized(memoryLogBuffer) {
            // Collapse consecutive identical lines
            val lastEntry = memoryLogBuffer.peekLast()
            if (lastEntry != null &&
                lastEntry.tag == tag &&
                lastEntry.level == level &&
                lastEntry.message == message &&
                lastEntry.kind == LogKind.GENERAL
            ) {
                val updatedLast = lastEntry.copy(
                    repeatCount = lastEntry.repeatCount + 1,
                    timestamp = System.currentTimeMillis()
                )
                memoryLogBuffer.removeLast()
                memoryLogBuffer.addLast(updatedLast)
                _logs.value = memoryLogBuffer.toList()
                return
            }

            val entry = LogEntry(
                id = ++nextId,
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                threadName = threadName,
                opId = opId,
                category = category,
                message = message,
                throwableText = throwableText,
                kind = kind,
                runId = runId,
                fileName = fileName,
                sizeBytes = sizeBytes,
                durationMs = durationMs,
                outcome = outcome,
                reasonCode = reasonCode,
                userMessage = userMessage,
                suggestedAction = suggestedAction,
                repeatCount = 1,
                isMock = isMock
            )

            // Mirror to logcat
            try {
                when (level) {
                    LogLevel.VERBOSE -> Log.v(tag, entry.fullFormattedText)
                    LogLevel.DEBUG -> Log.d(tag, entry.fullFormattedText)
                    LogLevel.INFO -> Log.i(tag, entry.fullFormattedText)
                    LogLevel.WARN -> Log.w(tag, entry.fullFormattedText, throwable)
                    LogLevel.ERROR -> Log.e(tag, entry.fullFormattedText, throwable)
                }
            } catch (_: Throwable) {}

            // Buffer handling
            if (memoryLogBuffer.size >= MAX_IN_MEMORY_LOGS) {
                memoryLogBuffer.pollFirst()
                _droppedEvents.value = droppedCounter.incrementAndGet()
            }
            memoryLogBuffer.addLast(entry)
            _logs.value = memoryLogBuffer.toList()

            // File logging (exclude mock entries from real disk files)
            if (isFileLoggingEnabled && logDir != null && !isMock) {
                scope.launch {
                    appendToFileRotating(entry)
                }
            }
        }
    }

    private fun appendToFileRotating(entry: LogEntry) {
        try {
            val dir = logDir ?: return
            val file = File(dir, "lumen_$currentFileIndex.log")

            if (file.exists() && file.length() >= MAX_FILE_SIZE_BYTES) {
                currentFileIndex = (currentFileIndex + 1) % ROTATING_FILE_COUNT
                val nextFile = File(dir, "lumen_$currentFileIndex.log")
                if (nextFile.exists()) {
                    nextFile.delete()
                }
            }

            val targetFile = File(dir, "lumen_$currentFileIndex.log")
            FileWriter(targetFile, true).use { writer ->
                writer.appendLine(entry.fullFormattedText)
            }
        } catch (e: Exception) {
            Log.w("LumenLogger", "appendToFileRotating error: ${e.message}")
        }
    }

    // --- Structured Logging Helpers ---
    fun logRunStart(
        runId: String,
        queuedCount: Int,
        wifiOnly: Boolean,
        chargingOnly: Boolean,
        storageSaver: Boolean,
        useQuota: Boolean,
        threads: Int,
        isMock: Boolean = false
    ) {
        val msg = "Backup run '$runId' started: $queuedCount files queued (wifiOnly=$wifiOnly, chargingOnly=$chargingOnly, storageSaver=$storageSaver, useQuota=$useQuota, threads=$threads)"
        log(
            level = LogLevel.INFO,
            tag = "UploadEngine",
            kind = LogKind.RUN_START,
            runId = runId,
            isMock = isMock
        ) { msg }
    }

    fun logFileResult(
        runId: String,
        fileName: String,
        sizeBytes: Long,
        durationMs: Long,
        outcome: UploadOutcome,
        reasonCode: String? = null,
        userMessage: String? = null,
        suggestedAction: String? = null,
        rawMessage: String,
        isMock: Boolean = false
    ) {
        val level = when (outcome) {
            UploadOutcome.FAILED -> LogLevel.ERROR
            UploadOutcome.SKIPPED_DUPLICATE -> LogLevel.INFO
            UploadOutcome.UPLOADED -> LogLevel.INFO
            UploadOutcome.NONE -> LogLevel.DEBUG
        }
        log(
            level = level,
            tag = "UploadEngine",
            kind = LogKind.FILE_RESULT,
            runId = runId,
            fileName = fileName,
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            outcome = outcome,
            reasonCode = reasonCode,
            userMessage = userMessage,
            suggestedAction = suggestedAction,
            isMock = isMock
        ) { rawMessage }
    }

    fun logRunSummary(
        runId: String,
        uploadedCount: Int,
        skippedCount: Int,
        failedCount: Int,
        totalBytes: Long,
        durationMs: Long,
        avgSpeedBps: Double,
        isMock: Boolean = false
    ) {
        val speedStr = if (avgSpeedBps >= 1024 * 1024) {
            String.format(Locale.US, "%.1f MB/s", avgSpeedBps / (1024 * 1024))
        } else {
            String.format(Locale.US, "%.1f KB/s", avgSpeedBps / 1024)
        }
        val sizeStr = String.format(Locale.US, "%.1f MB", totalBytes.toDouble() / (1024 * 1024))
        val msg = "Backup run '$runId' summary: $uploadedCount uploaded, $skippedCount skipped, $failedCount failed ($sizeStr total, duration ${durationMs / 1000}s, avg $speedStr)"

        log(
            level = LogLevel.INFO,
            tag = "UploadEngine",
            kind = LogKind.RUN_SUMMARY,
            runId = runId,
            durationMs = durationMs,
            sizeBytes = totalBytes,
            isMock = isMock
        ) { msg }
    }

    fun logBlocked(
        runId: String,
        reasonCode: String,
        userMessage: String,
        suggestedAction: String,
        isMock: Boolean = false
    ) {
        log(
            level = LogLevel.WARN,
            tag = "UploadEngine",
            kind = LogKind.BLOCKED,
            runId = runId,
            reasonCode = reasonCode,
            userMessage = userMessage,
            suggestedAction = suggestedAction,
            isMock = isMock
        ) { "Backup run '$runId' BLOCKED: $userMessage ($suggestedAction)" }
    }

    // --- Standard Lazy Logging API ---
    inline fun v(tag: String, opId: String? = null, tr: Throwable? = null, noinline msg: () -> String) {
        log(LogLevel.VERBOSE, tag, opId, tr, messageProvider = msg)
    }

    inline fun d(tag: String, opId: String? = null, tr: Throwable? = null, noinline msg: () -> String) {
        log(LogLevel.DEBUG, tag, opId, tr, messageProvider = msg)
    }

    inline fun i(tag: String, opId: String? = null, tr: Throwable? = null, noinline msg: () -> String) {
        log(LogLevel.INFO, tag, opId, tr, messageProvider = msg)
    }

    inline fun w(tag: String, opId: String? = null, tr: Throwable? = null, noinline msg: () -> String) {
        log(LogLevel.WARN, tag, opId, tr, messageProvider = msg)
    }

    inline fun e(tag: String, opId: String? = null, tr: Throwable? = null, noinline msg: () -> String) {
        log(LogLevel.ERROR, tag, opId, tr, messageProvider = msg)
    }

    // String overloads
    fun v(tag: String, message: String, tr: Throwable?) = log(LogLevel.VERBOSE, tag, null, tr) { message }
    fun d(tag: String, message: String, tr: Throwable?) = log(LogLevel.DEBUG, tag, null, tr) { message }
    fun i(tag: String, message: String, tr: Throwable?) = log(LogLevel.INFO, tag, null, tr) { message }
    fun w(tag: String, message: String, tr: Throwable?) = log(LogLevel.WARN, tag, null, tr) { message }
    fun e(tag: String, message: String, tr: Throwable?) = log(LogLevel.ERROR, tag, null, tr) { message }

    fun v(tag: String, message: String, opId: String? = null, tr: Throwable? = null) {
        log(LogLevel.VERBOSE, tag, opId, tr) { message }
    }

    fun d(tag: String, message: String, opId: String? = null, tr: Throwable? = null) {
        log(LogLevel.DEBUG, tag, opId, tr) { message }
    }

    fun i(tag: String, message: String, opId: String? = null, tr: Throwable? = null) {
        log(LogLevel.INFO, tag, opId, tr) { message }
    }

    fun w(tag: String, message: String, opId: String? = null, tr: Throwable? = null) {
        log(LogLevel.WARN, tag, opId, tr) { message }
    }

    fun e(tag: String, message: String, opId: String? = null, tr: Throwable? = null) {
        log(LogLevel.ERROR, tag, opId, tr) { message }
    }

    // --- Timed Operation Helper ---
    inline fun <T> timed(tag: String, operation: String, opId: String? = null, block: () -> T): T {
        d(tag, opId = opId) { "[START] $operation" }
        val start = System.currentTimeMillis()
        return try {
            val result = block()
            val duration = System.currentTimeMillis() - start
            d(tag, opId = opId) { "[END] $operation completed in ${duration}ms" }
            result
        } catch (t: Throwable) {
            val duration = System.currentTimeMillis() - start
            e(tag, opId = opId, tr = t) { "[FAILED] $operation failed after ${duration}ms: ${t.message}" }
            throw t
        }
    }

    suspend inline fun <T> timedSuspend(tag: String, operation: String, opId: String? = null, crossinline block: suspend () -> T): T {
        d(tag, opId = opId) { "[START] $operation" }
        val start = System.currentTimeMillis()
        return try {
            val result = block()
            val duration = System.currentTimeMillis() - start
            d(tag, opId = opId) { "[END] $operation completed in ${duration}ms" }
            result
        } catch (t: Throwable) {
            val duration = System.currentTimeMillis() - start
            e(tag, opId = opId, tr = t) { "[FAILED] $operation failed after ${duration}ms: ${t.message}" }
            throw t
        }
    }

    // --- Animation Logging Helper ---
    fun animStart(
        name: String,
        target: String,
        property: String,
        from: Any?,
        to: Any?,
        spec: String,
        trigger: String,
        token: String? = null
    ) {
        v("Anim") {
            "[ANIM START] name='$name' target='$target' prop='$property' from='$from' -> to='$to' spec='$spec' trigger='$trigger'${token?.let { " token='$it'" } ?: ""}"
        }
    }

    fun animEnd(
        name: String,
        target: String,
        durationMs: Long,
        finalValue: Any?,
        completed: Boolean,
        interruptedBy: String? = null
    ) {
        v("Anim") {
            "[ANIM END] name='$name' target='$target' duration=${durationMs}ms finalValue='$finalValue' status=${if (completed) "COMPLETED" else "INTERRUPTED by '$interruptedBy'"}"
        }
    }

    // --- Global Uncaught Exception Handler ---
    private val isCrashHandlerInstalled = AtomicBoolean(false)
    fun installCrashHandler() {
        if (isCrashHandlerInstalled.compareAndSet(false, true)) {
            val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    e("Crash", opId = "CRASH", tr = throwable) {
                        "FATAL UNCAUGHT EXCEPTION on thread '${thread.name}' (id=${thread.id}): ${throwable.message}"
                    }
                    logDir?.let { dir ->
                        val crashFile = File(dir, "lumen_crash.log")
                        FileWriter(crashFile, true).use { writer ->
                            writer.appendLine("==========================================")
                            writer.appendLine("LUMEN CRASH: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
                            writer.appendLine("Thread: ${thread.name} (id=${thread.id})")
                            writer.appendLine("Message: ${throwable.message}")
                            writer.appendLine(buildStackTraceString(throwable))
                            writer.appendLine("==========================================")
                        }
                    }
                } catch (_: Exception) {}
                originalHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    // --- Periodic 30s Snapshot ---
    private fun startPeriodicSnapshot() {
        snapshotJob?.cancel()
        snapshotJob = scope.launch {
            while (isActive) {
                delay(30_000L)
                if (isVerboseEnabled) {
                    takeSystemSnapshot()
                }
            }
        }
    }

    private fun takeSystemSnapshot() {
        val runtime = Runtime.getRuntime()
        val usedMemMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val activeThreads = Thread.activeCount()

        // Only emit if memory or thread count changed meaningfully
        if (Math.abs(usedMemMb - lastSnapshotMemMb) >= 10 || activeThreads != lastSnapshotThreads) {
            lastSnapshotMemMb = usedMemMb
            lastSnapshotThreads = activeThreads
            v("Perf") {
                "System snapshot: usedMem=${usedMemMb}MB activeThreads=$activeThreads"
            }
        }
    }

    fun exportLogs(context: Context) {
        val dir = logDir ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val exportFile = File(context.cacheDir, "lumen_export_logs.txt")
                FileWriter(exportFile, false).use { writer ->
                    dir.listFiles()?.sortedBy { it.name }?.forEach { logFile ->
                        writer.appendLine("--- FILE: ${logFile.name} ---")
                        writer.append(logFile.readText())
                        writer.appendLine()
                    }
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", exportFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Export Lumen Logs")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                LumenLogger.e("LumenLogger", "Export logs failed: ${e.message}", e)
            }
        }
    }

    fun clearLogs(context: Context? = null) {
        synchronized(memoryLogBuffer) {
            memoryLogBuffer.clear()
            _logs.value = emptyList()
        }
        logDir?.listFiles()?.forEach { it.delete() }
        i("LumenLogger") { "Cleared all logs" }
    }

    private fun buildStackTraceString(t: Throwable): String {
        val sb = StringBuilder()
        sb.append(t.javaClass.name).append(": ").append(t.message).append("\n")
        for (element in t.stackTrace.take(15)) {
            sb.append("\tat ").append(element.toString()).append("\n")
        }
        t.cause?.let { cause ->
            sb.append("Caused by: ").append(buildStackTraceString(cause))
        }
        return sb.toString()
    }
}
