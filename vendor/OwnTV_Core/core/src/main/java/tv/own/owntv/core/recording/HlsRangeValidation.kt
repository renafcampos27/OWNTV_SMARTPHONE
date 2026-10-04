package tv.own.owntv.core.recording

/** A 206 body must describe precisely the resource interval requested, not just valid media bytes. */
internal object HlsRangeValidation {
    private val contentRange = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)
    fun accepts(code: Int, header: String?, range: HlsMediaPlaylist.ByteRange): Boolean {
        if (code != 206) return false
        if (range.length <= 0L || range.offset < 0L || range.offset > Long.MAX_VALUE - range.length + 1L) return false
        val fields = contentRange.matchEntire(header?.trim().orEmpty())?.groupValues ?: return false
        val first = fields[1].toLongOrNull() ?: return false
        val last = fields[2].toLongOrNull() ?: return false
        if (first != range.offset || last != range.offset + range.length - 1) return false
        return fields[3] == "*" || fields[3].toLongOrNull()?.let { it > last } == true
    }
}
