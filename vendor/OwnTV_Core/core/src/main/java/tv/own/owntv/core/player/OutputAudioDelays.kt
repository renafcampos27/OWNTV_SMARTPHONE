package tv.own.owntv.core.player

/** Classes only: no Bluetooth address, device name or hardware identifier is persisted. */
enum class AudioOutputKind { UNKNOWN, SPEAKER, HEADPHONES, BLUETOOTH, HDMI, ARC, USB, OTHER }

data class OutputAudioDelays(private val values: Map<AudioOutputKind, Int> = emptyMap()) {
    operator fun get(kind: AudioOutputKind): Int = if (kind == AudioOutputKind.UNKNOWN) 0 else values[kind] ?: 0
    fun with(kind: AudioOutputKind, ms: Int) = OutputAudioDelays(values + (kind to ms.coerceIn(-5000, 5000)))
    fun encode(): String = AudioOutputKind.entries.filter { it != AudioOutputKind.UNKNOWN && get(it) != 0 }
        .joinToString(",") { "${it.name}=${get(it)}" }
    companion object {
        fun parse(raw: String?): OutputAudioDelays = OutputAudioDelays(buildMap {
            raw?.take(512)?.split(',')?.forEach { entry ->
                val parts = entry.split('=')
                val kind = AudioOutputKind.entries.firstOrNull { it.name == parts.firstOrNull() }
                val ms = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size == 2 && kind != null && kind != AudioOutputKind.UNKNOWN && ms != null)
                    put(kind, ms.coerceIn(-5000, 5000))
            }
        })
        fun effective(baseMs: Int, outputMs: Int): Int =
            (baseMs.toLong() + outputMs).coerceIn(-5000, 5000).toInt()
    }
}
