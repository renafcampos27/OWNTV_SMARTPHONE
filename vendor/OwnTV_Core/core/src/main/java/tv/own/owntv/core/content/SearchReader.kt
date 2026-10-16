package tv.own.owntv.core.content

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ensureActive
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ChannelSearchResult
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources

/** Combined results of a global query (each list bounded). */
@Immutable
data class SearchResults(
    val channels: List<ChannelSearchResult> = emptyList(),
    val movies: List<MovieEntity> = emptyList(),
    val series: List<SeriesEntity> = emptyList(),
    val programmes: List<ProgrammeSearchResult> = emptyList(),
) {
    val isEmpty: Boolean get() = channels.isEmpty() && movies.isEmpty() && series.isEmpty() && programmes.isEmpty()
}

@Immutable
data class ProgrammeSearchResult(
    val programme: tv.own.owntv.core.database.entity.EpgProgrammeEntity,
    val channel: tv.own.owntv.core.database.entity.ChannelEntity,
)

/** The curated lists offered when nothing has been typed yet. */
enum class SearchIntent {
    CONTINUE,
    UNWATCHED,
    CHANNELS,
}

/**
 * Searching a profile's channels, films and shows at once.
 *
 * The queries themselves are the DAOs'; what lives here is everything that decides *which* of their
 * rows a user may see — the FTS expression, the hidden items, the hidden categories, a kids
 * profile's adult filter, and the renames a user made in Customize. Two apps searching the same
 * database have to agree on all five, so it is written once rather than in each app's search screen.
 */
class SearchReader(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val profileDao: ProfileDao,
    private val customize: CustomizationStore,
    private val epgDao: tv.own.owntv.core.database.dao.EpgDao,
    private val settings: tv.own.owntv.core.settings.SettingsRepository,
) {

    /**
     * Results for a typed query, already filtered and renamed for [profileId].
     *
     * [sources] is the profile's active playlists, per section, so a section switched Off never
     * surfaces. Returns nothing at all when the profile has no sources — there is nothing to search.
     */
    suspend fun search(
        profileId: Long,
        sources: ActiveProfileSources,
        query: String,
        limit: Int = LIMIT,
        includeProgrammes: Boolean = true,
    ): SearchResults {
        if (profileId < 0 || !sources.hasAny) return SearchResults()
        val fts = ftsQuery(query)
        val custLive = customize.observe(profileId, MediaType.LIVE).first()
        val custMovie = customize.observe(profileId, MediaType.MOVIE).first()
        val custSeries = customize.observe(profileId, MediaType.SERIES).first()
        val isKids = profileDao.getById(profileId)?.isKids == true
        val hiddenLiveCats = hiddenCategoryIds(sources.liveSourceIds, MediaType.LIVE, custLive, isKids)
        val hiddenMovieCats = hiddenCategoryIds(sources.movieSourceIds, MediaType.MOVIE, custMovie, isKids)
        val hiddenSeriesCats = hiddenCategoryIds(sources.seriesSourceIds, MediaType.SERIES, custSeries, isKids)
        return SearchResults(
            programmes = if (includeProgrammes) programmes(query, sources.liveSourceIds, custLive, hiddenLiveCats) else emptyList(),
            channels = if (sources.liveSourceIds.isEmpty()) emptyList() else
                (
                    if (fts != null) channelDao.searchListDetailedFts(fts, sources.liveSourceIds, limit)
                    else channelDao.searchListDetailed(query, sources.liveSourceIds, limit)
                    )
                    .filter {
                        CustomizeKeys.channel(it.channel) !in custLive.hiddenItems &&
                            (it.channel.categoryId == null || it.channel.categoryId !in hiddenLiveCats)
                    }
                    .map { row ->
                        custLive.itemNames[CustomizeKeys.channel(row.channel)]
                            ?.let { row.copy(channel = row.channel.copy(name = it)) } ?: row
                    },
            movies = if (sources.movieSourceIds.isEmpty()) emptyList() else
                (
                    if (fts != null) movieDao.searchListFts(fts, sources.movieSourceIds, limit)
                    else movieDao.searchList(query, sources.movieSourceIds, limit)
                    )
                    .filter {
                        CustomizeKeys.movie(it) !in custMovie.hiddenItems &&
                            (it.categoryId == null || it.categoryId !in hiddenMovieCats)
                    }
                    .map { m -> custMovie.itemNames[CustomizeKeys.movie(m)]?.let { m.copy(name = it) } ?: m },
            series = if (sources.seriesSourceIds.isEmpty()) emptyList() else
                (
                    if (fts != null) seriesDao.searchListFts(fts, sources.seriesSourceIds, limit)
                    else seriesDao.searchList(query, sources.seriesSourceIds, limit)
                    )
                    .filter {
                        CustomizeKeys.series(it) !in custSeries.hiddenItems &&
                            (it.categoryId == null || it.categoryId !in hiddenSeriesCats)
                    }
                    .map { s -> custSeries.itemNames[CustomizeKeys.series(s)]?.let { s.copy(name = it) } ?: s },
        )
    }

    private suspend fun programmes(query: String, sourceIds: List<Long>, cust: SectionCustomizations,
        hiddenCats: Set<Long>): List<ProgrammeSearchResult> {
        if (sourceIds.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val horizon = now + 12 * 60 * 60_000L
        val globalShift = settings.epgOffsetMinutes.first()
        val matchedKeys = cust.epgMatches.values.map { it.trim().lowercase() }.distinct().take(500)
        val slack = 24 * 60 * 60_000L
        val found = mutableListOf<ProgrammeSearchResult>()
        val seen = mutableSetOf<Triple<String, Long, String>>()
        val pattern = ProgrammeSearchPolicy.literalPattern(query)
        var offset = 0
        // Bounded pages allow hidden results to be skipped without unrelated feeds consuming the cap.
        while (offset < 2_000 && found.size < 40) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val rows = epgDao.searchProgrammeTitles(pattern, now-slack, horizon+slack, sourceIds, matchedKeys, 200, offset)
            if (rows.isEmpty()) break
            val byKey = rows.groupBy { it.epgChannelId }
            val tails = cust.epgMatches.filterValues { it.trim().lowercase() in byKey }.keys
                .map { CustomizeKeys.tailOf(it) }.filter { it.isNotEmpty() }.distinct()
            val candidates = channelDao.byEpgKeys(sourceIds, byKey.keys.toList()) +
                tails.chunked(200).flatMap { channelDao.byRemoteIdsOrNames(sourceIds, it) }
            candidates.distinctBy { it.id }.sortedWith(compareBy({sourceIds.indexOf(it.sourceId)}, {it.sortOrder})).forEach { ch ->
                if (CustomizeKeys.channel(ch) in cust.hiddenItems || ch.categoryId in hiddenCats) return@forEach
                val key = (cust.epgMatchResolver.epgIdFor(ch) ?: ch.epgChannelId)?.trim()?.lowercase() ?: return@forEach
                val shift = tv.own.owntv.core.epg.EpgShift.minutesFor(cust, ch, globalShift)
                val named = cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name=it) } ?: ch
                val programmes = tv.own.owntv.core.epg.EpgShift.apply(tv.own.owntv.core.epg.EpgDedupe.collapse(byKey[key].orEmpty()), shift)
                programmes.filter { it.stopMs > now && it.startMs < horizon }.forEach { programme ->
                    if (seen.add(Triple(key, programme.startMs, programme.title))) found += ProgrammeSearchResult(programme, named)
                }
            }
            offset += rows.size
            if (rows.size < 200) break
        }
        return found.sortedWith(compareBy({ it.programme.startMs > now }, { it.programme.startMs })).take(40)
    }

    /** One of the curated lists shown before anything is typed. Bounded, and already source-filtered. */
    suspend fun curated(
        profileId: Long,
        sources: ActiveProfileSources,
        intent: SearchIntent,
        limit: Int = LIMIT,
    ): SearchResults {
        if (profileId < 0 || !sources.hasAny) return SearchResults()
        return when (intent) {
            SearchIntent.CONTINUE -> SearchResults(
                channels = channelDao.recentlyWatched(profileId, limit).first()
                    .filter { it.sourceId in sources.liveSourceIds }
                    .map { ChannelSearchResult(it, null) },
                movies = if (sources.movieSourceIds.isEmpty()) emptyList()
                else movieDao.recentlyWatchedSnapshot(profileId, sources.movieSourceIds, limit),
                series = if (sources.seriesSourceIds.isEmpty()) emptyList()
                else seriesDao.recentlyWatchedSnapshot(profileId, sources.seriesSourceIds, limit),
            )
            SearchIntent.UNWATCHED -> SearchResults(
                movies = if (sources.movieSourceIds.isEmpty()) emptyList()
                else movieDao.unwatchedFavorites(profileId, sources.movieSourceIds, limit),
                series = if (sources.seriesSourceIds.isEmpty()) emptyList()
                else seriesDao.unwatchedFavorites(profileId, sources.seriesSourceIds, limit),
            )
            SearchIntent.CHANNELS -> SearchResults(
                channels = channelDao.favoritesListAlpha(profileId).first()
                    .filter { it.sourceId in sources.liveSourceIds }
                    .take(limit)
                    .map { ChannelSearchResult(it, null) },
            )
        }
    }

    /** DB ids of this profile's hidden categories for [type] (so hidden groups drop out of search too). */
    private suspend fun hiddenCategoryIds(
        sourceIds: List<Long>,
        type: MediaType,
        cust: SectionCustomizations,
        isKidsProfile: Boolean,
    ): Set<Long> {
        if (cust.hiddenCategories.isEmpty() && !isKidsProfile) return emptySet()
        return AdultCategoryClassifier.hiddenCategoryIds(
            categoryDao.observe(sourceIds, type).first(),
            cust.hiddenCategories,
            isKidsProfile,
        )
    }

    companion object {
        /** How many rows of each kind a search returns. */
        const val LIMIT = 40

        /**
         * A sanitized FTS4 MATCH expression: punctuation separates words; each word becomes a prefix term ("harry pot" → "harry* pot*", implicit AND).
         * Null when nothing tokenizable remains (symbols-only input) — the caller then falls back to
         * the substring LIKE queries. Prefix terms match word starts rather than mid-word substrings,
         * which is the accepted trade-off for an index-served search over ~220k rows per keystroke.
         */
        fun ftsQuery(query: String): String? {
            val tokens = query.split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}

/** True when the profile has at least one playlist a search could look in. */
private val ActiveProfileSources.hasAny: Boolean
    get() = liveSourceIds.isNotEmpty() || movieSourceIds.isNotEmpty() || seriesSourceIds.isNotEmpty()
