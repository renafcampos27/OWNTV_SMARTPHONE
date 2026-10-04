package tv.own.owntv.mobile.ui.screens.live

import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.player.LiveStartupGrace
import tv.own.owntv.player.LiveStartupProgress

/** Only trailing transport/quality labels are removed. Country, number and programme stay intact. */
internal object ChannelAlternatives {
    private val number = Regex("^\\s*\\d+\\.\\s*")
    private val quality = Regex("\\s+\\(?(full\\s*hd|fhd|hd|sd|hq|low|hevc|hvec|h[. ]?26[45]|1080p|720p|4k|uhd)\\)?$", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("\\s+")
    private val colonSpacing = Regex("\\s*:\\s*")
    fun key(name: String): String {
        var value = name.replace(number, "").trim().lowercase(Locale.ROOT)
            .replace(whitespace, " ").replace(colonSpacing, ":")
        while (quality.containsMatchIn(value)) value = value.replace(quality, "").trim()
        // Explicitly confirmed by the user; do not infer other channel aliases.
        return when (value) {
            "pt:canal historia" -> "pt:historia"
            "pt:discovery channel" -> "pt:discovery"
            else -> value
        }
    }
    fun searchTerm(name: String): String = key(name).substringAfter(':').substringBefore(' ')
    fun ordered(original: ChannelEntity, candidates: List<ChannelEntity>): List<ChannelEntity> =
        candidates.filter { it.sourceId == original.sourceId && key(it.name) == key(original.name) }
            .sortedWith(compareBy<ChannelEntity> { rank(it.name) }.thenBy { it.sortOrder }.thenBy { it.id })
            .let { listOf(original) + it }
            .distinctBy { it.id }
            .distinctBy { it.streamUrl }

    private fun rank(name: String): Int {
        val suffix = quality.find(name)?.groupValues?.get(1)?.lowercase(Locale.ROOT)?.replace(" ", "")
        return when (suffix) {
            "fullhd", "fhd", "1080p", "4k", "uhd" -> 0
            "hd", "720p" -> 1
            "hq" -> 2
            "hevc", "hvec", "h265", "h.265", "h264", "h.264" -> 4
            "low", "sd" -> 5
            else -> 3
        }
    }
}

/** Each attempt owns its timeout. Cancellation propagates, so a remote action always wins. */
internal suspend fun <T> tryChannelAlternatives(
    initial: T,
    alternatives: suspend () -> List<T>,
    timeoutMs: Long = 3_000,
    attempt: suspend (T) -> Boolean,
    failed: () -> Unit,
    switching: (T) -> Unit,
    isBackingOff: (() -> Boolean)? = null,
    backoffRemainingMs: (() -> Long)? = null,
    shouldSkipAlternative: ((T) -> Boolean)? = null,
    startupProgress: (() -> LiveStartupProgress?)? = null,
): Boolean {
    suspend fun tryOne(item: T): Boolean {
        val result = if (isBackingOff != null || backoffRemainingMs != null || startupProgress != null) {
            runWithBackoffAwareTimeout(timeoutMs, isBackingOff, backoffRemainingMs, startupProgress = startupProgress) { attempt(item) }
        } else {
            withTimeoutOrNull(timeoutMs) { attempt(item) } == true
        }
        if (!result) failed()
        return result
    }
    if (tryOne(initial)) return true
    for (item in alternatives()) {
        if (isBackingOff?.invoke() == true || shouldSkipAlternative?.invoke(item) == true) {
            // A06: Do not tune alternatives from the refusing host during mandatory server backoff window
            continue
        }
        switching(item)
        if (tryOne(item)) return true
    }
    return false
}

/**
 * Runs [block] with a timeout of [timeoutMs] of active tuning time.
 * If the engine enters mandatory provider backoff ([isBackingOff] == true), the timeout countdown is paused
 * so the player is allowed to wait out the server's Retry-After deadline rather than prematurely switching.
 * Cancellation (e.g. user changing channels) cancels immediately.
 */
internal suspend fun runWithBackoffAwareTimeout(
    timeoutMs: Long,
    isBackingOff: (() -> Boolean)?,
    backoffRemainingMs: (() -> Long)?,
    maxWaitCeilingMs: Long = 60_000L,
    startupProgress: (() -> LiveStartupProgress?)? = null,
    block: suspend () -> Boolean,
): Boolean = kotlinx.coroutines.coroutineScope {
    val attemptJob = async { block() }
    val pollStepMs = 50L
    var elapsedActiveMs = 0L
    var totalElapsedMs = 0L
    val startupGrace = LiveStartupGrace()
    var grantedGraceMs = 0L
    var previousTickNs = System.nanoTime()
    while (attemptJob.isActive) {
        val tickNs = System.nanoTime()
        val elapsedMs = ((tickNs - previousTickNs) / 1_000_000L).coerceAtLeast(0L)
        previousTickNs = tickNs
        grantedGraceMs += startupGrace.observe(startupProgress?.invoke())
        val backingOff = isBackingOff?.invoke() == true || (backoffRemainingMs?.invoke() ?: 0L) > 0L
        if (!backingOff) {
            elapsedActiveMs += elapsedMs
            if (elapsedActiveMs >= timeoutMs + grantedGraceMs) {
                attemptJob.cancel()
                return@coroutineScope false
            }
        }
        totalElapsedMs += elapsedMs
        if (totalElapsedMs >= maxWaitCeilingMs) {
            attemptJob.cancel()
            return@coroutineScope false
        }
        kotlinx.coroutines.delay(pollStepMs)
    }
    try {
        attemptJob.await()
    } catch (_: kotlinx.coroutines.CancellationException) {
        false
    }
}
