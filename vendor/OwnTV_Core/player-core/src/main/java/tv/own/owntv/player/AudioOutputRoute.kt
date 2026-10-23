package tv.own.owntv.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.media.MediaRouter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tv.own.owntv.core.player.AudioOutputKind

data class AudioOutputRoute(val kind: AudioOutputKind = AudioOutputKind.UNKNOWN, val confirmed: Boolean = false)

/** Process lifetime, event driven. Connected devices alone never establish a playback route. */
object AudioOutputRoutes {
    private val mutable = MutableStateFlow(AudioOutputRoute())
    val current = mutable.asStateFlow()
    private val handler = Handler(Looper.getMainLooper())
    private var manager: AudioManager? = null
    private var owner: Any? = null

    @Synchronized fun start(context: Context) {
        if (manager != null) return
        val app = context.applicationContext
        manager = app.getSystemService(AudioManager::class.java)
        manager?.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
        }, handler)
        app.getSystemService(MediaRouter::class.java)?.addCallback(MediaRouter.ROUTE_TYPE_LIVE_AUDIO,
            object : MediaRouter.SimpleCallback() {
                override fun onRouteSelected(router: MediaRouter, type: Int, info: MediaRouter.RouteInfo) = refresh()
                override fun onRouteChanged(router: MediaRouter, info: MediaRouter.RouteInfo) = refresh()
            })
        (app as? Application)?.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = refresh()
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        refresh()
    }

    @Synchronized fun refresh() {
        if (owner != null) return // A playing AudioTrack is stronger evidence than an anticipated route.
        val devices = if (Build.VERSION.SDK_INT >= 33) runCatching {
            manager?.getAudioDevicesForAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE).build())
        }.getOrNull().orEmpty() else emptyList()
        val kinds = devices.map { kind(it.type) }.distinct()
        mutable.value = AudioOutputRoute(kinds.singleOrNull() ?: AudioOutputKind.UNKNOWN)
    }

    @Synchronized internal fun observed(source: Any, device: AudioDeviceInfo?) {
        owner = source
        mutable.value = AudioOutputRoute(device?.let { kind(it.type) } ?: AudioOutputKind.UNKNOWN, device != null)
    }
    @Synchronized internal fun release(source: Any) {
        if (owner !== source) return
        owner = null
        refresh()
    }
    internal fun kind(type: Int): AudioOutputKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioOutputKind.SPEAKER
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioOutputKind.HEADPHONES
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST -> AudioOutputKind.BLUETOOTH
        AudioDeviceInfo.TYPE_HDMI -> AudioOutputKind.HDMI
        AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> AudioOutputKind.ARC
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_USB_HEADSET -> AudioOutputKind.USB
        else -> AudioOutputKind.OTHER
    }
}

/** The provider delegates creation unchanged. The sink owns registration and teardown, not a timer.
 * Media3 1.11 keeps this bridge; its newer AudioOutput API does not expose the actual AudioTrack.
 * Retain the public bridge for routing evidence instead of reflection or a connected-device guess. */
@Suppress("DEPRECATION")
internal class RoutedAudioOutput : DefaultAudioSink.AudioTrackProvider {
    private val handler = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var playing = false
    private val routing = AudioRouting.OnRoutingChangedListener { output ->
        synchronized(this) { if (playing && output === track) AudioOutputRoutes.observed(this, track?.routedDevice) }
    }
    @Synchronized override fun getAudioTrack(config: AudioSink.AudioTrackConfig,
        attributes: androidx.media3.common.AudioAttributes, sessionId: Int, context: Context?): AudioTrack {
        detach()
        return DefaultAudioSink.AudioTrackProvider.DEFAULT.getAudioTrack(config, attributes, sessionId, context).also {
            track = it
            it.addOnRoutingChangedListener(routing, handler)
        }
    }
    @Synchronized fun play() {
        playing = true
        AudioOutputRoutes.observed(this, track?.routedDevice)
    }
    @Synchronized fun pause() { playing = false; AudioOutputRoutes.release(this) }
    @Synchronized fun detach() {
        track?.removeOnRoutingChangedListener(routing)
        track = null
        AudioOutputRoutes.release(this)
    }
    fun wrap(sink: AudioSink): AudioSink = object : ForwardingAudioSink(sink) {
        override fun play() { super.play(); this@RoutedAudioOutput.play() }
        override fun pause() { this@RoutedAudioOutput.pause(); super.pause() }
        override fun reset() { this@RoutedAudioOutput.pause(); detach(); super.reset() }
        override fun release() { this@RoutedAudioOutput.pause(); detach(); super.release() }
        override fun flush() { detach(); super.flush() }
    }
}
