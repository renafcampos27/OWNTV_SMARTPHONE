package tv.own.owntv.core.epg

import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.entity.ChannelEntity

/** A guide association changes programme lookup only, never the destination channel or account. */
object GuideInheritance {
    fun primary(channel: ChannelEntity, custom: SectionCustomizations): String? =
        normalize(custom.epgMatchResolver.epgIdFor(channel) ?: channel.epgChannelId)

    fun fallback(channel: ChannelEntity, custom: SectionCustomizations): String? =
        normalize(custom.epgFallbackFor(channel))?.takeUnless { it == primary(channel, custom) }

    suspend fun key(channel: ChannelEntity, custom: SectionCustomizations, dao: EpgDao, from: Long, to: Long): String? =
        choose(primary(channel, custom), fallback(channel, custom)) { dao.hasProgrammeInWindow(it, from, to) }

    internal suspend fun choose(primary: String?, fallback: String?, available: suspend (String) -> Boolean): String? {
        if (fallback == null || fallback == primary) return primary
        if (primary != null && available(primary)) return primary
        return if (available(fallback)) fallback else primary
    }

    private fun normalize(value: String?): String? = value?.trim()?.lowercase(java.util.Locale.ROOT)?.takeIf { it.isNotEmpty() }
}
