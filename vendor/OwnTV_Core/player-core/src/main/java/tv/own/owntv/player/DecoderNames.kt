package tv.own.owntv.player

import android.media.MediaCodecList
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

/**
 * Whether a `MediaCodec` decoder name belongs to a hardware decoder — answered from what the device
 * actually offers, never from what the user asked for.
 *
 * The distinction is load-bearing because [ownTVRenderers] enables Media3's decoder fallback: when the
 * vendor decoder throws while configuring, playback continues on the *next* decoder the device offers,
 * which is normally a software one. That rescue is deliberate and worth having — but it is silent, and
 * the only symptom is a picture that degrades. Any readout that infers the decoder kind from the
 * **setting** then reports "hardware" while software is what is running, which is precisely the state a
 * support report has to be able to name.
 *
 * The answer never changes for a given name, so results are cached.
 */
object DecoderNames {

    private val cache = ConcurrentHashMap<String, Boolean>()

    /** Android's own software decoders, whatever the vendor-prefix rule below would otherwise say. */
    private val SOFTWARE_PREFIXES = listOf("omx.google.", "c2.android.", "c2.google.", "omx.ffmpeg.", "arc.")

    /** Older-platform naming heuristic; a vendor prefix is not a hardware measurement. */
    private val VENDOR_PREFIXES = listOf("omx.", "c2.")

    /**
     * `true` = hardware, `false` = software, `null` = the name says nothing we can trust.
     *
     * Unknown is reported as unknown on purpose: a confidently wrong "software" in a diagnostic sends
     * the next reader down the wrong path, which costs more than saying nothing.
     */
    fun isHardware(name: String): Boolean? {
        if (name.isBlank()) return null
        val key = name.lowercase()
        cache[key]?.let { return it }
        val resolved = classify(key) ?: return null
        cache[key] = resolved
        return resolved
    }

    /** Manufacturer reports are diagnostic evidence, not a measurement of hardware usage. */
    fun reportedFlags(name: String): String = platformReports[name.lowercase()] ?: "platformFlags=unavailable"

    /**
     * Prefer platform metadata on API 29+. A vendor prefix does not prove hardware acceleration,
     * especially for audio. Older platforms fall back to the known name conventions.
     */
    private fun classify(lower: String): Boolean? {
        if (platformCodecs.containsKey(lower)) return platformCodecs[lower]
        if (SOFTWARE_PREFIXES.any { lower.startsWith(it) }) return false
        if (lower.contains(".sw.")) return false // e.g. OMX.SEC.avc.sw.dec
        if (VENDOR_PREFIXES.any { lower.startsWith(it) }) return true
        return platformCodecs[lower]
    }

    private val platformReports = ConcurrentHashMap<String, String>()

    /** Manufacturer metadata, available from API 29; older platforms use name conventions. */
    private val platformCodecs: Map<String, Boolean?> by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@lazy emptyMap()
        runCatching {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
                .filter { !it.isEncoder }
                .associate {
                    platformReports[it.name.lowercase()] = "reportedHardware=${it.isHardwareAccelerated} reportedSoftware=${it.isSoftwareOnly} vendor=${it.isVendor} canonical=${it.canonicalName}"
                    it.name.lowercase() to when {
                    it.isHardwareAccelerated -> true
                    it.isSoftwareOnly -> false
                    else -> null
                } }
        }.getOrDefault(emptyMap())
    }
}
