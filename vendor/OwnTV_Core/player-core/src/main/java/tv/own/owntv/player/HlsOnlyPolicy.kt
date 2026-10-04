package tv.own.owntv.player

/** Only Xtream has a known sibling endpoint; arbitrary signed M3U/portal URLs stay intact. */
internal object HlsOnlyPolicy {
    fun directTsUrl(url: String, xtream: Boolean): String {
        if (!xtream) return url
        val split = url.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) url.length else it }
        val path = url.substring(0, split)
        return if (path.endsWith(".m3u8", ignoreCase = true)) path.dropLast(5) + ".ts" + url.substring(split) else url
    }

    fun url(url: String, xtream: Boolean): String {
        if (!xtream) return url
        val split = url.indexOfAny(charArrayOf('?', '#')).let { if (it < 0) url.length else it }
        val path = url.substring(0, split)
        return if (path.endsWith(".ts", ignoreCase = true)) path.dropLast(3) + ".m3u8" + url.substring(split) else url
    }
}
