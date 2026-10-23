package tv.own.owntv.player

/** A reordered ordinal alone cannot identify the language/commentary the user selected. */
internal object AudioTrackRetention {
    fun match(retained: TrackOption, tracks: List<TrackOption>): TrackOption? {
        val language = retained.lang?.trim()?.takeIf(String::isNotEmpty)
        val label = retained.label.trim().takeIf(String::isNotEmpty)
        fun matches(track: TrackOption) =
            (language == null || explicitTrackLanguageMatches(language, track.lang)) &&
                (label == null || label == track.label.trim())
        tracks.firstOrNull { it.typeIndex == retained.typeIndex && retained.typeIndex >= 0 }
            ?.takeIf(::matches)?.let { return it }
        if (language == null && label == null) return null
        return tracks.filter(::matches).singleOrNull()
    }
}
