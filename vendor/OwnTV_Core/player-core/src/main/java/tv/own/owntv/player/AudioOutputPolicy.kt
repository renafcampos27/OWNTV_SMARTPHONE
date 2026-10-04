package tv.own.owntv.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import tv.own.owntv.core.player.SurroundMode

/**
 * Session-wide state for the audio-output safety net, shared by **every** engine (mpv, the Live
 * preview/fullscreen ExoPlayer, the VOD ExoPlayer).
 *
 * Why global and not per-engine: the fault being guarded against lives in the device's audio HAL or
 * in the HDMI/ARC sink, not in a stream. Once one engine has proved that this TV cannot actually
 * play what it claims to support, every other engine must inherit that lesson immediately —
 * otherwise the user zaps to the next channel, lands on the other engine, and loses sound again.
 *
 * In-memory only, deliberately. A latch is a statement about "this output, right now" (an HDMI
 * handshake, a soundbar that woke up in the wrong mode); persisting it would silently downgrade a
 * genuinely capable receiver forever. Changing the setting also clears it — that is the user asking
 * for a fresh attempt.
 */
object AudioOutputPolicy {

    /** Never advance audio for this long while playing with an audio track = the output is dead. */
    const val NO_AUDIO_GRACE_MS = 6_000L

    /** Underruns within [UNDERRUN_WINDOW_MS] that mean the sink is starving, not hiccuping. */
    const val UNDERRUN_LIMIT = 4
    const val UNDERRUN_WINDOW_MS = 10_000L

    @Volatile private var latched = false
    @Volatile var latchReason: String? = null
        private set

    /** True once the output has been caught failing; forces stereo PCM everywhere for this session. */
    val stereoLatched: Boolean get() = latched

    /**
     * True when this mode + the current latch permit anything other than plain stereo PCM.
     * [SurroundMode.STEREO] and a tripped latch are the same answer for different reasons.
     */
    fun allowsMultichannel(mode: SurroundMode): Boolean = mode != SurroundMode.STEREO && !latched

    /** Trip the latch. Idempotent — the first reason is the interesting one. */
    fun latchStereo(reason: String) {
        if (latched) return
        latched = true
        latchReason = reason
        android.util.Log.w("AudioOutputPolicy", "forcing stereo for this session: $reason")
    }

    /** The user changed the surround setting: give the output another chance. */
    fun clearLatch() {
        latched = false
        latchReason = null
    }
}

/**
 * A [DefaultRenderersFactory] that can be pinned to plain stereo PCM.
 *
 * When [forceStereo] is set the audio sink is built with [AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES]
 * — Media3's "minimum capabilities supported by all devices": 16-bit stereo PCM, **no encoded
 * passthrough**. `MediaCodecAudioRenderer` asks the sink what it supports before choosing between
 * passthrough and in-app decoding, so capping the sink is what actually removes the bitstream path;
 * a track-selection constraint alone would not (the selector still picks a 5.1 track when it is the
 * only one).
 *
 * The no-argument [DefaultAudioSink.Builder] is deprecated precisely *because* passing a `Context`
 * makes the sink query the real device capabilities and ignore anything set here — which is the one
 * thing we must not let it do. PCM is mixed explicitly using the decoded positional layout;
 * unsupported layouts fail rather than silently discarding channels.
 */
@UnstableApi
class OwnTVRenderersFactory(
    context: Context,
    private val forceStereo: Boolean,
    private val audioDelay: AudioDelayClock? = null,
    private val preferFfmpegAudio: Boolean = false,
) : DefaultRenderersFactory(context) {

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: android.os.Handler,
        eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
        out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>,
    ) {
        val firstAudio = out.size
        // Register only the audio extension. Global extension mode can also enable software video.
        super.buildAudioRenderers(context, EXTENSION_RENDERER_MODE_OFF, mediaCodecSelector,
            enableDecoderFallback, audioSink, eventHandler, eventListener, out)
        val available = androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()
        LiveDiagnosticsLog.event("ffmpeg_audio available=$available preferred=$preferFfmpegAudio")
        if (available) {
            val renderer = androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer(eventHandler, eventListener, audioSink)
            out.add(if (preferFfmpegAudio) firstAudio else out.size, renderer)
        }
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink? {
        val sink = if (!forceStereo) super.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams)
        else {
            val mixer = StereoPcmAudioProcessor()
            @Suppress("DEPRECATION")
            val pcmSink = DefaultAudioSink.Builder()
                .setAudioCapabilities(AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                // Mixing is opt-in here only. Float output skips the PCM16 processing chain.
                .setEnableFloatOutput(false)
                .setAudioProcessors(arrayOf(mixer))
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .build()
            object : androidx.media3.exoplayer.audio.ForwardingAudioSink(pcmSink) {
                // Encoded offload bypasses processors and cannot guarantee the selected stereo mix.
                override fun getFormatOffloadSupport(format: Format): androidx.media3.exoplayer.audio.AudioOffloadSupport =
                    androidx.media3.exoplayer.audio.AudioOffloadSupport.DEFAULT_UNSUPPORTED

                override fun configure(config: AudioSink.AudioSinkConfig) {
                    val format = config.format
                    val mask = format.channelMask.takeIf { it != Format.NO_VALUE }
                    // A custom two-channel mask must not survive as an output speaker mask.
                    if (format.channelCount == 2 && mask != null && mask != 12) {
                        throw AudioSink.ConfigurationException("Unsupported two-channel PCM mask: $mask", format)
                    }
                    try { mixer.setChannelLayout(mask, config.outputChannelMapping?.toArray(), format.channelCount) }
                    catch (error: IllegalArgumentException) { throw AudioSink.ConfigurationException(error, format) }
                    catch (error: IndexOutOfBoundsException) { throw AudioSink.ConfigurationException(error, format) }
                    super.configure(config)
                }
            }
        }
        return if (sink != null && audioDelay != null) DelayedClockAudioSink(sink, audioDelay) else sink
    }
}

/**
 * Watches an ExoPlayer's audio output and reports the two failures a user experiences as "picture
 * but no sound" and "sound keeps cutting out".
 *
 * This is a listener plus a [poll] that the owning engine calls from the health tick it already
 * runs — it deliberately owns no timer of its own, so it cannot keep an engine alive after release.
 *
 * Detection, in order of confidence:
 *
 *  1. **`onAudioSinkError`** — the sink told us outright. Immediate. Excludes
 *     `UnexpectedDiscontinuityException`, which reports a gap in the *stream's* timestamps and is
 *     self-healing; see [onAudioSinkError].
 *  2. **Armed but never advancing.** The initial format / a new AudioTrack proves audio was selected
 *     and handed to a decoder; `onAudioPositionAdvancing` fires when the AudioTrack's playback head
 *     actually starts moving, i.e. when sound genuinely leaves the device. If the first happens and
 *     the second does not within [AudioOutputPolicy.NO_AUDIO_GRACE_MS] of *playing* time, the output
 *     accepted the format and produced silence. A reused output preserves its advancement evidence. Playing time, not wall
 *     clock: a channel that spends eight seconds buffering has not failed at anything.
 *  3. **Repeated underruns.** One is a hiccup; [AudioOutputPolicy.UNDERRUN_LIMIT] inside
 *     [AudioOutputPolicy.UNDERRUN_WINDOW_MS] is a sink that cannot keep up with the format it said
 *     it could play.
 *
 * A hit calls back once per player instance; the owner is expected to latch stereo and rebuild.
 */
@UnstableApi
class AudioWatchdog(
    private val accepts: (AnalyticsListener.EventTime) -> Boolean = { true },
    private val canRecoverUnderrun: () -> Boolean = { false },
    private val context: () -> String = { "" },
    private val onAdvancing: () -> Unit = {},
    private val nowMs: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) : AnalyticsListener {

    /** Set when a failure is detected; consumed by [poll]. Cleared on [reset]. */
    @Volatile private var pendingReason: String? = null
    @Volatile private var fired = false

    @Volatile private var armed = false
    @Volatile private var advancing = false
    /** Accumulated *playing* milliseconds since the audio format was accepted. */
    @Volatile private var playingSinceArmMs = 0L
    @Volatile private var lastTickMs = 0L

    private val underrunTimes = ArrayDeque<Long>()

    /** The audio format currently feeding the sink, for the stream-info readout. */
    @Volatile var audioFormat: Format? = null
    @Volatile private var actualDecoderName: String? = null
        private set
    /** True when the renderer chose passthrough (the TV/receiver decodes) rather than in-app decode. */
    @Volatile var passthrough = false
        private set
    private var outputConfig: AudioSink.AudioTrackConfig? = null
    private data class OutputRecord(val id: Long, val config: AudioSink.AudioTrackConfig)
    private val outputRecords = ArrayDeque<OutputRecord>()
    private var nextOutputId = 0L
    private var currentOutputId: Long? = null
    val hasAdvanced: Boolean get() = advancing
    private fun diagnostic(message: String) = LiveDiagnosticsLog.event("$message ${context()}")

    /** Call when a new load starts. */
    fun reset() {
        actualDecoderName = null
        pendingReason = null; fired = false
        armed = false; advancing = false
        playingSinceArmMs = 0L; lastTickMs = 0L
        synchronized(underrunTimes) { underrunTimes.clear() }
        audioFormat = null; passthrough = false; outputConfig = null; currentOutputId = null
    }

    override fun onAudioInputFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        format: Format,
        decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?,
    ) {
        if (!accepts(eventTime)) return
        // The format actually reaching the sink, including the one chosen at startup — without this a
        // log only ever showed what the user switched *to*, never what they switched *from*, so a
        // stereo→5.1 change was indistinguishable from a change between two identical formats.
        android.util.Log.i(
            "AudioOutputPolicy",
            "audio format -> ${format.sampleMimeType} ${format.channelCount}ch ${format.sampleRate}Hz " +
                "lang=${format.language} reuse=${decoderReuseEvaluation?.result}",
        )
        audioFormat = format
        diagnostic("audio_format mime=${format.sampleMimeType} channels=${format.channelCount} rate=${format.sampleRate} reuse=${decoderReuseEvaluation?.result} outputActive=${outputConfig != null} advancing=$advancing")
        // Format notification is not output creation. Reused AudioTracks need not announce advance again.
        if (!armed && !advancing) {
            armed = true
            playingSinceArmMs = 0L
            lastTickMs = 0L
        }
    }

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) {
        if (!accepts(eventTime)) return
        // AudioTrack encoding, when known, is authoritative; absence of a decoder proves nothing.
        actualDecoderName = decoderName
        diagnostic("audio_decoder name=$decoderName initMs=$initializationDurationMs outputEncoding=${outputConfig?.encoding}")
        // Whether the TV is decoding the bitstream or we are is the single most useful fact about an
        // audio problem, and it was only ever visible in the stream-info overlay, never in a log.
        android.util.Log.i(
            "AudioOutputPolicy",
            "audio decoder: $decoderName (init ${initializationDurationMs}ms, passthrough=$passthrough)",
        )
    }

    override fun onAudioPositionAdvancing(
        eventTime: AnalyticsListener.EventTime,
        playoutStartSystemTimeMs: Long,
    ) {
        if (!accepts(eventTime)) return
        advancing = true
        diagnostic("audio_advancing passthrough=$passthrough")
        onAdvancing()
    }

    override fun onAudioTrackInitialized(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        if (!accepts(eventTime)) return
        outputConfig = audioTrackConfig
        currentOutputId = ++nextOutputId
        outputRecords.addLast(OutputRecord(nextOutputId, audioTrackConfig))
        while (outputRecords.size > 8) outputRecords.removeFirst()
        passthrough = !androidx.media3.common.util.Util.isEncodingLinearPcm(audioTrackConfig.encoding)
        armed = true
        advancing = false
        playingSinceArmMs = 0L
        lastTickMs = 0L
        synchronized(underrunTimes) { underrunTimes.clear() }
        diagnostic("audio_output_created encoding=${audioTrackConfig.encoding} rate=${audioTrackConfig.sampleRate} channelsMask=${audioTrackConfig.channelConfig} bufferBytes=${audioTrackConfig.bufferSize} offload=${audioTrackConfig.offload} passthrough=$passthrough")
    }

    override fun onAudioTrackReleased(eventTime: AnalyticsListener.EventTime, audioTrackConfig: AudioSink.AudioTrackConfig) {
        if (!accepts(eventTime)) return
        // Media3 reconstructs the config object on release; reference identity is not retained.
        // Releases may arrive after a replacement was created. Match the oldest equal configuration.
        val record = outputRecords.firstOrNull { sameOutputConfig(it.config, audioTrackConfig) }
        if (record != null) outputRecords.remove(record)
        diagnostic("audio_output_released encoding=${audioTrackConfig.encoding} outputId=${record?.id} currentId=$currentOutputId")
        if (record == null || record.id != currentOutputId) return
        currentOutputId = null
        outputConfig = null
        armed = false
        advancing = false
        passthrough = false
        lastTickMs = 0L
    }

    private fun sameOutputConfig(a: AudioSink.AudioTrackConfig, b: AudioSink.AudioTrackConfig): Boolean =
        a.encoding == b.encoding && a.sampleRate == b.sampleRate && a.channelConfig == b.channelConfig &&
            a.tunneling == b.tunneling && a.offload == b.offload && a.bufferSize == b.bufferSize

    override fun onPlaybackParametersChanged(eventTime: AnalyticsListener.EventTime, playbackParameters: androidx.media3.common.PlaybackParameters) {
        if (!accepts(eventTime)) return
        diagnostic("audio_playback_speed speed=${playbackParameters.speed} pitch=${playbackParameters.pitch}")
    }

    override fun onAudioUnderrun(
        eventTime: AnalyticsListener.EventTime,
        bufferSize: Int,
        bufferSizeMs: Long,
        elapsedSinceLastFeedMs: Long,
    ) {
        if (!accepts(eventTime)) return
        val now = nowMs()
        // Individual underruns were silent until the limit tripped, which made "sound keeps cutting
        // out" impossible to confirm from a log: below the limit there was no evidence at all, and at
        // the limit only a verdict. One line each is cheap — healthy playback produces none.
        android.util.Log.w(
            "AudioOutputPolicy",
            "audio underrun: buffer=${bufferSize}B/${bufferSizeMs}ms, ${elapsedSinceLastFeedMs}ms since last feed",
        )
        diagnostic("audio_underrun bufferBytes=$bufferSize bufferMs=$bufferSizeMs elapsedSinceFeedMs=$elapsedSinceLastFeedMs passthrough=$passthrough recoveryEligible=${canRecoverUnderrun()}")
        // A starved media buffer or decoded PCM underrun does not establish broken passthrough.
        if (!passthrough || !canRecoverUnderrun()) {
            synchronized(underrunTimes) { underrunTimes.clear() }
            return
        }
        val hit = synchronized(underrunTimes) {
            underrunTimes.addLast(now)
            while (underrunTimes.isNotEmpty() && now - underrunTimes.first() > AudioOutputPolicy.UNDERRUN_WINDOW_MS) {
                underrunTimes.removeFirst()
            }
            underrunTimes.size >= AudioOutputPolicy.UNDERRUN_LIMIT
        }
        if (hit) raise("audio output underran ${AudioOutputPolicy.UNDERRUN_LIMIT}× in ${AudioOutputPolicy.UNDERRUN_WINDOW_MS / 1000}s")
    }

    override fun onAudioSinkError(eventTime: AnalyticsListener.EventTime, audioSinkError: Exception) {
        if (!accepts(eventTime)) return
        // A timestamp discontinuity is a statement about the *stream*, not about the output. Media3
        // raises it whenever a buffer's presentation time lands more than 200ms from where the running
        // frame count says it should (DefaultAudioSink), then re-anchors its own clock and carries on
        // playing — a routine event on files whose container timestamps jump. Treating it as sink
        // failure latched a whole session to stereo over one imperfect file, and since forcing a stereo
        // sink cannot repair a gap that lives in the file, the rebuilt player hit the same spot and
        // tripped again: repeated mid-film restarts on a device whose audio was never in trouble.
        // If the output really is dead, the no-advance tier catches it seconds later anyway.
        if (audioSinkError is AudioSink.UnexpectedDiscontinuityException) {
            diagnostic("audio_timestamp_discontinuity gapUs=${audioSinkError.actualPresentationTimeUs - audioSinkError.expectedPresentationTimeUs}")
            android.util.Log.i(
                "AudioOutputPolicy",
                "audio timestamp discontinuity: ${audioSinkError.actualPresentationTimeUs - audioSinkError.expectedPresentationTimeUs}us " +
                    "off expected — the sink resyncs itself, not treating this as an output failure",
            )
            return
        }
        diagnostic("audio_sink_error type=${audioSinkError.javaClass.simpleName}")
        raise("audio sink error: ${audioSinkError.message ?: audioSinkError.javaClass.simpleName}")
    }

    private fun raise(reason: String) {
        if (fired) return
        fired = true
        pendingReason = reason
    }

    /**
     * Advance the "playing time since armed" clock and return a failure reason once, or null.
     * [isPlaying] must be the player's real playing state — paused time must not count.
     */
    fun poll(isPlaying: Boolean): String? {
        pendingReason?.let { pendingReason = null; return it }
        if (!armed || advancing) { lastTickMs = 0L; return null }
        val now = nowMs()
        if (!isPlaying) { lastTickMs = 0L; return null }
        if (lastTickMs != 0L) playingSinceArmMs += (now - lastTickMs).coerceAtMost(2_000L)
        lastTickMs = now
        if (playingSinceArmMs < AudioOutputPolicy.NO_AUDIO_GRACE_MS) return null
        if (fired) return null
        fired = true
        val what = audioFormat?.let { MimeTypes.normalizeMimeType(it.sampleMimeType ?: "") } ?: "audio"
        return "no sound from the audio output after ${AudioOutputPolicy.NO_AUDIO_GRACE_MS / 1000}s ($what)"
    }

    fun decoderInfo(softwareRequested: Boolean): StreamInfoRow? {
        val name = actualDecoderName ?: return null
        if (passthrough || outputConfig == null) return null
        val hardware = if (name.startsWith("ffmpeg")) false else DecoderNames.isHardware(name)
        return StreamInfoRow(StreamInfoLabel.AUDIO_DECODER, StreamInfoValue.Decoder(
            kind = DecoderKind.NAMED, name = name,
            hardware = hardware == true, software = hardware == false,
            softwareFallback = softwareRequested && hardware == true,
        ))
    }

    /** Human-readable audio line for the stream-info overlay, or null when nothing is known yet. */
    fun describe(): String? {
        val f = audioFormat ?: return null
        val codec = f.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: "?"
        val channels = if (f.channelCount != Format.NO_VALUE) "${f.channelCount}ch" else null
        val rate = if (f.sampleRate != Format.NO_VALUE) "${f.sampleRate / 1000}kHz" else null
        val path = if (passthrough) "passthrough" else "decoded"
        return listOfNotNull(codec, channels, rate, path).joinToString(" · ")
    }
}

/** Media3 channel-count cap that matches [mode]; [C.INDEX_UNSET] semantics are not used here. */
fun maxAudioChannelsFor(mode: SurroundMode): Int =
    if (AudioOutputPolicy.allowsMultichannel(mode)) Int.MAX_VALUE else 2
