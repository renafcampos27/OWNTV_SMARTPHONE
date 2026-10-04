package tv.own.owntv.core.live

import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/** A transient provider failure must not be cached as a valid empty programme list. */
class ProviderGuideUnavailableException(
    val lastKnownRows: List<EpgProgrammeEntity>,
    cause: Exception,
) : Exception(cause)
