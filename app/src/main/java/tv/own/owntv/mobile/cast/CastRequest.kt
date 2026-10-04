package tv.own.owntv.mobile.cast

/**
 * One stream, described the only way a Chromecast can be told about it: a URL and some labels.
 *
 * Deliberately small. The receiver decodes the stream itself, so none of what the local engine is
 * given — the fallback ladder, the pinned engine, the reconnect provider, the pre-buffer override —
 * has anywhere to go. [httpHeaders] is carried only so [castable] can refuse: a default receiver
 * cannot send per-request headers, and pretending otherwise would produce a stall instead of a
 * message.
 */
data class CastRequest(
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val logoUrl: String? = null,
    val isLive: Boolean,
    val startPositionMs: Long = 0,
    /** Per-item `Key: Value` headers (M3U `#EXTHTTP`/`#EXTVLCOPT`); non-blank means "cannot cast". */
    val httpHeaders: String? = null,
    /** Widevine/ClearKey protected (#115). A default receiver has no licence handling, so true also
     *  means "cannot cast". */
    val drm: Boolean = false,
)

/**
 * Whether a receiver has any chance with this stream, judged before anything is sent.
 *
 * Three refusals only, and all three are certainties rather than guesses:
 * - **per-request headers**, which no default receiver can be made to send;
 * - **a `.ts` URL**, raw MPEG-TS, which no Chromecast decodes;
 * - **a protected item**, because a licence needs a receiver of one's own to fetch it.
 *
 * Everything else is attempted, because the alternative is refusing streams that would have worked.
 * A `User-Agent` is dropped silently: most playlists carry one whether the provider insists on it or
 * not, so refusing on its presence would refuse nearly the whole catalogue — and a browser cannot
 * override that header anyway, on any receiver, paid or free. A provider that does insist answers
 * with an error, and the receiver's refusal becomes the same message.
 */
internal fun CastRequest.castable(): Boolean =
    !drm && httpHeaders.isNullOrBlank() && !url.substringBefore('?').endsWith(".ts", ignoreCase = true)

/** The best guess at a MIME type from the URL, which is all the receiver gets to sniff with. */
internal fun CastRequest.contentType(): String {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".m3u8") -> "application/x-mpegurl"
        path.endsWith(".mpd") -> "application/dash+xml"
        path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
        path.endsWith(".webm") -> "video/webm"
        path.endsWith(".mkv") -> "video/x-matroska"
        path.endsWith(".mp3") -> "audio/mpeg"
        path.endsWith(".aac") || path.endsWith(".m4a") -> "audio/mp4"
        // Extension-less is the Xtream shape, and on a live path it is nearly always HLS or TS. HLS
        // is the half a receiver can play, so that is the guess worth making.
        isLive -> "application/x-mpegurl"
        else -> "video/mp4"
    }
}
