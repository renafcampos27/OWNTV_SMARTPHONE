package tv.own.owntv.player

import android.content.ComponentCallbacks2

/** Host lifecycle dispatch; mobile only forwards real memory pressure. */
class PlaybackEngines(private val player: OwnTVPlayer, private val livePreview: LivePreviewEngine, private val pool: LiveEnginePool, private val heroPreview: HeroPreviewEngine? = null) {
    enum class Pressure { NONE, LOW, CRITICAL }
    fun onTrimMemory(level: Int) {
        when (pressureOf(level)) {
            Pressure.NONE -> Unit
            Pressure.LOW -> player.onTrimMemory()
            Pressure.CRITICAL -> { player.onTrimMemory(); livePreview.onMemoryPressure(); pool.onMemoryPressure() }
        }
    }
    fun onAppBackgrounded() { player.onAppBackgrounded(); livePreview.onAppBackgrounded(); pool.onAppBackgrounded(); heroPreview?.stop() }
    fun onAppForegrounded() { player.onAppForegrounded(); livePreview.onAppForegrounded(); pool.onAppForegrounded() }
    companion object {
        @Suppress("DEPRECATION")
        fun pressureOf(level: Int) = when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> Pressure.CRITICAL
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> Pressure.LOW
            else -> Pressure.NONE
        }
    }
}
