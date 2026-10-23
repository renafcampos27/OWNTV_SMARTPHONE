package tv.own.owntv.player

/** Immutable capture: renewing a signed URL cannot discard this archive's request identity. */
internal data class ArchiveRetryContext(val isArchive: Boolean, val alternatives: List<String>, val manifestTypes: Map<String, String>) {
    companion object {
        fun capture(item: ItemState, isLive: Boolean): ArchiveRetryContext =
            if (!isLive && item.archiveThisItem) ArchiveRetryContext(true, item.archiveAlternatives.toList(), item.archiveManifestTypes.toMap())
            else ArchiveRetryContext(false, emptyList(), emptyMap())
    }
}
