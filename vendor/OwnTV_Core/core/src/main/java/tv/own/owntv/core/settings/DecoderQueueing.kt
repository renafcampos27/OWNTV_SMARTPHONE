package tv.own.owntv.core.settings

/** Renderer construction policy; changing it takes effect on the next playback opening. */
enum class DecoderQueueing {
    AUTO, ASYNCHRONOUS, SYNCHRONOUS;

    companion object {
        fun fromStored(value: String?): DecoderQueueing =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}
