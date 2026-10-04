package tv.own.owntv.mobile.ui.screens.live

import org.json.JSONArray
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.entity.ChannelEntity

/** Group identity uses provider names, never display renames, and cannot cross sources. */
internal object ChannelVersionPolicy {
    fun groupKey(ch: ChannelEntity) = "${ch.sourceId}:${ChannelAlternatives.key(ch.name)}"
    fun groupKey(ch: ChannelEntity, settings: SectionCustomizations): String =
        settings.channelVersionGroups[CustomizeKeys.channel(ch)]
            ?.takeIf { it.startsWith("${ch.sourceId}:") && it.substringAfter(':').isNotBlank() }
            ?: groupKey(ch)

    fun versionKey(ch: ChannelEntity) = CustomizeKeys.tailOf(CustomizeKeys.channel(ch))
    private val suffix = Regex("\\s+\\(?(full\\s*hd|fhd|1080p|4k|uhd|hevc|hvec|h[. ]?265|hd|720p|sd|low|hq|h[. ]?264)\\)?$", RegexOption.IGNORE_CASE)

    fun rank(name: String): Int {
        var value = name.trim()
        var rank = 3
        while (true) {
            val match = suffix.find(value) ?: break
            val token = match.groupValues[1].lowercase(java.util.Locale.ROOT).replace(" ", "").replace(".", "")
            val candidate = when (token) {
                "fullhd", "fhd", "1080p", "4k", "uhd" -> 0
                "hevc", "hvec", "h265" -> 1
                "hd", "720p", "hq" -> 2
                "low", "sd" -> 4
                else -> 3
            }
            // Low is a real suffix; an absent suffix alone means normal.
            rank = if (value == name.trim()) candidate else minOf(rank, candidate)
            value = value.substring(0, match.range.first).trimEnd()
        }
        return rank
    }

    fun manualOrder(ch: ChannelEntity, settings: SectionCustomizations): List<String> =
        settings.channelVersionOrders[groupKey(ch, settings)]?.let { raw ->
            runCatching { JSONArray(raw).let { array -> (0 until array.length()).map { array.getString(it) }.distinct() } }.getOrNull()
        }.orEmpty()

    fun ordered(rows: List<ChannelEntity>, settings: SectionCustomizations, includeHidden: Boolean = false): List<ChannelEntity> {
        val visible = if (includeHidden) rows else rows.filter { CustomizeKeys.channel(it) !in settings.hiddenItems }
        if (visible.isEmpty() || !settings.prioritizeChannelVersions) return visible
        val manual = manualOrder(visible.first(), settings).withIndex().associate { it.value to it.index }
        return visible.sortedWith(compareBy<ChannelEntity> { manual[versionKey(it)] ?: Int.MAX_VALUE }
            .thenBy { rank(it.name) }.thenBy { it.sortOrder }.thenBy { versionKey(it) })
    }

    fun grouped(rows: List<ChannelEntity>, settings: SectionCustomizations): List<ChannelEntity> =
        if (!settings.groupChannelVersions) rows else rows.filter { CustomizeKeys.channel(it) !in settings.hiddenItems }.groupBy { groupKey(it, settings) }.values.map { ordered(it, settings).first() }
}
