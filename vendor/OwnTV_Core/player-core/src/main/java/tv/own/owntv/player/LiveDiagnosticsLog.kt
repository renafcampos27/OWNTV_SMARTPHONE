package tv.own.owntv.player

import android.content.Context
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import tv.own.owntv.core.player.PlayerBudget

/**
 * Bounded ring buffer + rolling file for Live TV (ExoPlayer) playback diagnostics. A hang can happen at any
 * time, and this device's vendor PQ/AI logging floods logcat fast enough that OwnTV's own lines don't survive
 * to a bugreport — so this keeps its own bounded record independent of Logcat.
 *
 * Disabled until the user explicitly enables detailed diagnostics, including in debug builds. Callers must redact URLs/credentials
 * before passing text in — this class does not inspect or strip anything itself.
 */
object LiveDiagnosticsLog {
    const val TAG = "OwnTV-LivePreviewEngine"

    @Volatile var enabled: Boolean = false

    private const val MAX_EVENTS = 1000

    /**
     * Rolling-file cap, device-tiered like [PlayerBudget]'s memory budget.
     *
     * All file operations run on one bounded background writer. Rotation reads only a bounded tail.
     * The in-memory ring remains the authoritative, immediate export snapshot.
     */
    private const val MAX_FILE_BYTES = 256 * 1024L
    private const val MAX_FILE_BYTES_LOW_SPEC = 128 * 1024L

    @Volatile private var maxFileBytes = MAX_FILE_BYTES
    private val diskLock = Any()
    private val ring = ArrayDeque<String>()
    private val lock = Any()
    private val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS", Locale.US).withZone(ZoneId.systemDefault())
    @Volatile private var logFile: File? = null
    private val writer = DiagnosticWriter { lines ->
        if (enabled) {
            lines.forEach { android.util.Log.i(TAG, it) }
            synchronized(diskLock) { logFile?.let { appendDiagnosticBatch(it, lines, maxFileBytes.toInt()) } }
        }
    }

    /** Call once with an app [Context] so events can be flushed to [file]. Safe to call repeatedly. */
    fun init(context: Context) {
        if (logFile != null) return
        maxFileBytes = if (PlayerBudget.of(context).lowSpec) MAX_FILE_BYTES_LOW_SPEC else MAX_FILE_BYTES
        runCatching {
            val dir = File(context.filesDir, "diagnostics")
            logFile = File(dir, "live_diagnostics.log")
        }
    }

    /** Record one diagnostic line. Always kept in the in-memory ring; also written to Logcat + the rolling
     *  file when [enabled]. [message] must already be redacted of URLs/credentials by the caller. */
    fun event(message: String) {
        val line = "${timeFmt.format(Instant.now())} ${message.take(2048)}"
        synchronized(lock) {
            ring.addLast(line)
            while (ring.size > MAX_EVENTS) ring.pollFirst()
            if (enabled) writer.offer(line)
        }
    }

    /** Oldest-first snapshot of the in-memory ring buffer (e.g. for an in-app export action). */
    fun snapshot(): String = synchronized(lock) {
        "diagnostics_enabled=$enabled events=${ring.size} diagnostic_queue_dropped=${writer.dropped.get()} write_failures=${writer.failures.get()}\n" + ring.joinToString("\n")
    }

    fun eventCount(): Int = synchronized(lock) { ring.size }

    /** Called by export on IO; the ring covers events still queued for disk. */
    fun persistedSnapshot(): String = synchronized(diskLock) {
        runCatching { logFile?.let { readDiagnosticTail(it, maxFileBytes.toInt()) }.orEmpty() }
            .getOrElse { "diagnostic_file_read_failed=${it.javaClass.simpleName}" }
    }

    /** Path of the rolling diagnostic file on disk, once [init] has run. */
    fun file(): File? = logFile
}
