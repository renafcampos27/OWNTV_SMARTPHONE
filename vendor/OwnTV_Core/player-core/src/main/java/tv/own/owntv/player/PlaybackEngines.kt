package tv.own.owntv.player

import android.content.ComponentCallbacks2
import android.app.ActivityManager
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Host lifecycle dispatch; mobile only forwards real memory pressure. */
class PlaybackEngines(private val player: OwnTVPlayer, private val livePreview: LivePreviewEngine, private val pool: LiveEnginePool, private val heroPreview: HeroPreviewEngine? = null) {
    enum class Pressure { NONE, LOW, CRITICAL }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        if (Build.VERSION.SDK_INT >= 34) scope.launch {
            val main = combine(player.isPlaying, player.buffering, player.playbackRequested) { playing, buffering, requested ->
                requested && (playing || buffering)
            }
            val preview = combine(livePreview.isPlaying, livePreview.buffering, livePreview.playbackRequested) { playing, buffering, requested ->
                requested && (playing || buffering)
            }
            val manager = player.appContext.getSystemService(ActivityManager::class.java) ?: return@launch
            val info = ActivityManager.MemoryInfo()
            monitorMemoryPressure(combine(main, preview, pool.playbackActivity) { a, b, c -> a || b || c }, sample = {
                manager.getMemoryInfo(info)
                pressureOf(info.lowMemory, info.availMem, info.threshold)
            }, apply = ::applyPressure)
        }
    }

    fun onTrimMemory(level: Int) = applyPressure(pressureOf(level))
    private fun applyPressure(pressure: Pressure) {
        if (pressure == Pressure.NONE) return
        player.onTrimMemory()
        if (pressure == Pressure.CRITICAL) { livePreview.onMemoryPressure(); pool.onMemoryPressure() }
    }
    fun onAppBackgrounded() { player.onAppBackgrounded(); livePreview.onAppBackgrounded(); pool.onAppBackgrounded(); heroPreview?.stop() }
    fun onAppForegrounded() { player.onAppForegrounded(); livePreview.onAppForegrounded(); pool.onAppForegrounded() }
    companion object {
        fun pressureOf(lowMemory: Boolean, availMem: Long, threshold: Long): Pressure = when {
            lowMemory -> Pressure.CRITICAL
            threshold <= 0 || availMem < 0 -> Pressure.NONE
            availMem <= threshold -> Pressure.CRITICAL
            availMem - threshold <= threshold -> Pressure.LOW
            else -> Pressure.NONE
        }
        @Suppress("DEPRECATION")
        fun pressureOf(level: Int) = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> Pressure.CRITICAL
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> Pressure.LOW
            else -> Pressure.NONE
        }
    }
}

/** Cancellation of relevance stops the timer, including while a stream is paused or has ended. */
internal suspend fun monitorMemoryPressure(
    relevant: Flow<Boolean>, sample: () -> PlaybackEngines.Pressure, apply: (PlaybackEngines.Pressure) -> Unit,
) {
    val episode = MemoryPressureEpisode()
    relevant.distinctUntilChanged().collectLatest { active ->
        if (active) while (currentCoroutineContext().isActive) {
            episode.observe(sample())?.let(apply)
            delay(5_000L)
        }
    }
}

/** Keep a lower level from retrimming repeatedly until pressure has actually cleared. */
internal class MemoryPressureEpisode {
    private var applied = PlaybackEngines.Pressure.NONE
    fun observe(pressure: PlaybackEngines.Pressure): PlaybackEngines.Pressure? {
        if (pressure == PlaybackEngines.Pressure.NONE) { applied = pressure; return null }
        return pressure.takeIf { it > applied }?.also { applied = it }
    }
}
