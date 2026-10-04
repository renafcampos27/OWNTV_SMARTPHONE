package tv.own.owntv.mobile.ui.screens.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.customize.CategoryRailEditor
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.railCategories
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ContentOrderDao
import tv.own.owntv.core.database.dao.CustomCategoryDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.epg.EpgMatcher
import tv.own.owntv.core.epg.EpgShift
import tv.own.owntv.core.epg.EpgAutoMatcher
import tv.own.owntv.core.epg.GuideCandidate
import tv.own.owntv.core.epg.GuideCandidates
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.live.GuideReader
import tv.own.owntv.core.live.GuideSlot
import tv.own.owntv.core.live.LiveEpgReader
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.live.livePagingSource
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.EpgRepository
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.ui.screens.live.LiveCategory
import tv.own.owntv.mobile.ui.screens.live.LiveTuner
import java.util.Calendar

/** The stretch of time the guide is showing, on the clock the user reads. */
data class GuideWindow(val start: Long, val end: Long)

/** What the guide actually holds, for the line under the options sheet and the empty states. */
data class GuideStats(
    val guideChannels: Int,
    val programmes: Int,
    val catchupChannels: Int,
    val hasEpgSources: Boolean,
    /** The guide has data, but not one channel of yours carries an id that appears in it. */
    val mismatchedIds: Boolean,
)

/** A proposed guide channel for a channel the matcher was not confident enough to link on its own. */
data class EpgMatchSuggestion(
    val channel: ChannelEntity,
    val epgChannelId: String,
    val epgName: String?,
    val score: Double,
)

/** The one-line outcome of an auto-match run. */
sealed interface EpgMatchSummary {
    data object MatchedNoProgrammes : EpgMatchSummary
    data object AddPlaylist : EpgMatchSummary
    data object NoData : EpgMatchSummary
    data object AllMatched : EpgMatchSummary
    data class NoMatch(val channelName: String) : EpgMatchSummary
    data class AutoMatched(val applied: Int, val review: Int) : EpgMatchSummary
}

/**
 * The Guide, for a screen you hold.
 *
 * The data is the television's, down to the query: the same channels in the same order, hidden the
 * same way, shifted by the same per-channel offsets. What differs is how much of it is asked for at
 * once. A television grid loads a rolling window of every channel because a remote can fling through
 * hundreds of rows; a phone shows one day at a time and reads a row's programmes as that row comes
 * into view, so opening the Guide costs one query rather than the lineup.
 */
// `debounce` is still a preview API. Both uses below are the same idea — a search box and a
// settling guide should not re-query on every keystroke or every row that arrives.
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class GuideViewModel(
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val customCategoryDao: CustomCategoryDao,
    private val contentOrderDao: ContentOrderDao,
    private val favoriteDao: FavoriteDao,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val profileDao: ProfileDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val guide: GuideReader,
    private val epgReader: LiveEpgReader,
    private val epgDao: EpgDao,
    private val epgSourceStore: EpgSourceStore,
    private val epgRepository: EpgRepository,
    private val tuner: LiveTuner,
    private val recordings: tv.own.owntv.core.recording.RecordingManager,
    private val xtream: tv.own.owntv.core.parser.XtreamClient,
) : ViewModel() {

    /** The one candidate set the picker and auto-match read — filtered by no source. */
    private val guideCandidates = GuideCandidates(epgDao)

    /** The match scan itself — core's, so the phone and the television agree on every decision. */
    private val autoMatcher = EpgAutoMatcher(channelDao, guideCandidates)

    private val ctx: StateFlow<ActiveProfileSources> = activeProfileSources(settings, sourceDao)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveProfileSources(-1L, emptyList()))

    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.LIVE)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionCustomizations())

    private val epgOffset: StateFlow<Int> = settings.epgOffsetMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val sortMode: StateFlow<SettingsRepository.SortMode> = settings.sortLive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.SortMode.PLAYLIST)

    private val liveCategories = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyList())
            else categoryDao.observe(c.liveSourceIds, MediaType.LIVE)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Categories this profile does not see — hidden ones, and every adult one for a kids profile. */
    private val hiddenCategoryIds: StateFlow<Set<Long>> =
        combine(liveCategories, custom, ctx.flatMapLatest { profileDao.observeById(it.profileId) }) { cats, cust, profile ->
            AdultCategoryClassifier.hiddenCategoryIds(cats, cust.hiddenCategories, profile?.isKids == true)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val folderContextKeys: StateFlow<Map<Long, String>> = liveCategories
        .map { cats -> cats.associateBy({ it.id }, { CustomizeKeys.category(it) }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val orderedContexts: StateFlow<Set<String>> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(emptyList())
            else contentOrderDao.observeContextKeys(c.profileId, MediaType.LIVE)
        }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** All channels, favourites, then the profile's folders — the Guide filters the same lists Live
     *  does, minus History and Catch-up, which are about what was watched rather than what is on. */
    val categories: StateFlow<List<LiveCategory>> = combine(
        liveCategories,
        custom,
        sortMode,
        ctx.flatMapLatest { profileDao.observeById(it.profileId) },
    ) { cats, cust, sort, profile ->
        val folders = cats.railCategories(
            cust,
            kids = profile?.isKids == true,
            alphaRest = sort == SettingsRepository.SortMode.ALPHA,
        )
        buildList {
            add(LiveCategory(LiveKey.All, builtIn = LiveCategory.BuiltIn.ALL))
            add(LiveCategory(LiveKey.Favorites, builtIn = LiveCategory.BuiltIn.FAVORITES))
            folders.forEach { e ->
                add(
                    LiveCategory(
                        key = e.categoryId?.let { LiveKey.Folder(it) } ?: LiveKey.Custom(e.customId!!),
                        title = e.displayName,
                    ),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Hide and reorder a category from the strip, through the same editor Settings → Customize uses. */
    private val categoryEditor = CategoryRailEditor(customize, categoryDao, profileDao)

    /** Hide the long-pressed category. It comes back from Settings → Customize. */
    fun hideCategory(key: LiveKey) {
        viewModelScope.launch {
            val c = ctx.value
            if (c.profileId < 0) return@launch
            categoryEditor.hide(c.profileId, MediaType.LIVE, key)
            // The strip is about to lose this chip; leaving it selected would show a folder that is
            // no longer there.
            if (selected.value == key) select(LiveKey.All)
        }
    }

    /** Move the long-pressed category one step through the strip, and keep it there. */
    fun moveCategory(key: LiveKey, kind: MoveKind) {
        viewModelScope.launch {
            val c = ctx.value
            if (c.profileId < 0) return@launch
            categoryEditor.move(
                profileId = c.profileId,
                sourceIds = c.liveSourceIds,
                type = MediaType.LIVE,
                alphaRest = sortMode.value == SettingsRepository.SortMode.ALPHA,
                key = key,
                kind = kind,
            )
        }
    }

    private val _selected = MutableStateFlow<LiveKey>(LiveKey.All)
    val selected: StateFlow<LiveKey> = _selected

    fun select(key: LiveKey) { _selected.value = key }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    fun setQuery(q: String) { _query.value = q }

    /** Which day is on screen: 0 is today, 1 tomorrow, and so on to the end of what the guide holds. */
    /**
     * How many days the day strip offers — the SAME value that decides how much is stored.
     *
     * It was a hard-coded 7. That silently matched the default and nothing else: raising "Guide days
     * to keep" to a fortnight left the phone unable to reach the second week it had just downloaded,
     * and lowering it left the strip offering days that had been pruned away.
     */
    val guideDays: StateFlow<Int> = settings.guideDaysToKeep
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), tv.own.owntv.core.settings.GuideRetention.DEFAULT_DAYS)

    private val _day = MutableStateFlow(0)
    val day: StateFlow<Int> = _day

    fun selectDay(offset: Int) {
        if (_day.value == offset) return
        _day.value = offset.coerceIn(-tv.own.owntv.core.live.MAX_CATCHUP_DAYS, guideDays.value - 1)
        synchronized(rowCache) { rowCache.clear() }
    }

    /**
     * The window on screen. Today starts at the last half hour rather than at midnight — a guide
     * that opens on this morning's programmes is a guide the user has to scroll before it is useful.
     */
    val window: StateFlow<GuideWindow> = _day
        .map { offset ->
            val cal = Calendar.getInstance()
            if (offset == 0) {
                cal.set(Calendar.MINUTE, if (cal.get(Calendar.MINUTE) < 30) 0 else 30)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            } else {
                cal.add(Calendar.DAY_OF_YEAR, offset)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            GuideWindow(start, cal.timeInMillis)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, GuideWindow(0, 0))

    /** Grid, on-now list or one channel's schedule. Null until the user picks: the screen then
     *  decides from its own width, which is the only thing that knows whether a grid fits. */
    val viewMode: StateFlow<SettingsRepository.GuideView?> = settings.guideView
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setViewMode(view: SettingsRepository.GuideView) {
        viewModelScope.launch { settings.setGuideView(view) }
    }

    val densityPct: StateFlow<Int> = settings.guideDensityPct
        .stateIn(viewModelScope, SharingStarted.Eagerly, 100)

    fun setDensityPct(pct: Int) {
        viewModelScope.launch { settings.setGuideDensityPct(pct) }
    }

    val favoriteIds: StateFlow<Set<Long>> = ctx
        .flatMapLatest { favoriteDao.observeFavoriteIds(it.profileId, MediaType.LIVE) }
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _stats = MutableStateFlow<GuideStats?>(null)
    val stats: StateFlow<GuideStats?> = _stats

    /**
     * The guide's own order, kept apart from the Live list's — the television has the same pair.
     *
     * Catch-up is the odd one out: it is a *filter*, showing only channels with an archive. On a
     * playlist with none it can only empty the guide, and the screen then blamed a missing playlist —
     * which cost an evening to work out once. So a catch-up choice that cannot match anything is not
     * applied. The user's stored preference is left alone: add a playlist with an archive and their
     * choice comes back on its own.
     */
    val sortGuide: StateFlow<SettingsRepository.GuideSort> = settings.sortGuide
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.GuideSort.LIVE_TV)

    val replayOnly = settings.guideReplayOnly.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    fun setReplayOnly(enabled: Boolean) { viewModelScope.launch { settings.setGuideReplayOnly(enabled) } }

    fun setSortGuide(sort: SettingsRepository.GuideSort) {
        viewModelScope.launch { settings.setSortGuide(sort) }
    }

    private data class Args(
        val key: LiveKey,
        val ctx: ActiveProfileSources,
        val cust: SectionCustomizations,
        val hidden: Set<Long>,
        val query: String,
        val sort: SettingsRepository.GuideSort = SettingsRepository.GuideSort.LIVE_TV,
        val favorites: Set<Long> = emptySet(),
        val replayOnly: Boolean = true,
    )

    /** The rows of the guide: the profile's channels, in its own order, filtered by chip and search. */
    val channels: Flow<PagingData<ChannelEntity>> =
        combine(_selected, ctx, custom, hiddenCategoryIds, _query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged()) {
            key, c, cust, hidden, q ->
            Args(key, c, cust, hidden, q)
        }
            .combine(sortGuide) { args, sort -> args.copy(sort = sort) }
            .combine(favoriteIds) { args, favorites -> args.copy(favorites = favorites) }
            .combine(replayOnly) { args, replay -> args.copy(replayOnly = replay) }
            .combine(orderedContexts) { args, _ -> args }
            .flatMapLatest { (key, c, cust, hidden, q, sort, favorites, replay) ->
                if (c.profileId < 0) {
                    flowOf(PagingData.empty())
                } else {
                    // Placeholders off, and it is not a preference: a lineup of 50 000 channels
                    // reports every one of them as an item before a single page has loaded, and the
                    // row a not-yet-loaded channel draws is nothing at all. A list of items with no
                    // height never fills its viewport, so it composes the whole lineup looking for
                    // something to show and takes the heap with it.
                    Pager(
                        PagingConfig(
                            pageSize = PAGE_SIZE,
                            prefetchDistance = PAGE_SIZE / 2,
                            enablePlaceholders = false,
                        ),
                    ) {
                        livePagingSource(
                            key = key,
                            profileId = c.profileId,
                            sourceIds = c.liveSourceIds,
                            query = q,
                            sort = sort.querySort(),
                            channelDao = channelDao,
                            customCategoryDao = customCategoryDao,
                            contextKey = { folderContextKeys.value[it] },
                            hasManualOrder = { it in orderedContexts.value },
                        )
                    }.flow.map { paging ->
                        paging.applyCustomizations(key, cust, hidden).applyGuideSort(sort, favorites).filter { !replay || it.catchup }
                    }
                }
            }
            .cachedIn(viewModelScope)

    /** A–Z and Provider order the query itself; the other three keep the Live list's order. */
    private fun SettingsRepository.GuideSort.querySort(): SettingsRepository.SortMode = when (this) {
        SettingsRepository.GuideSort.ALPHA -> SettingsRepository.SortMode.ALPHA
        SettingsRepository.GuideSort.PROVIDER -> SettingsRepository.SortMode.PLAYLIST
        else -> sortMode.value
    }

    /**
     * Catch-up and Favorites narrow the guide instead of reordering it.
     *
     * The television floats archive channels to the top of a grid the remote flings through. A phone
     * shows a page at a time, so "at the top" is a promise it cannot keep — the channels the user
     * asked about are the ones shown.
     */
    private fun PagingData<ChannelEntity>.applyGuideSort(
        sort: SettingsRepository.GuideSort,
        favorites: Set<Long>,
    ): PagingData<ChannelEntity> = when (sort) {
        SettingsRepository.GuideSort.CATCHUP -> filter { it.catchup }
        SettingsRepository.GuideSort.FAVORITES -> filter { it.id in favorites }
        else -> this
    }

    /** Hidden channels and folders drop out and renames apply — the same rule as the Live list. */
    private fun PagingData<ChannelEntity>.applyCustomizations(
        key: LiveKey,
        cust: SectionCustomizations,
        hidden: Set<Long>,
    ): PagingData<ChannelEntity> {
        if (cust.hiddenItems.isEmpty() && cust.itemNames.isEmpty() && hidden.isEmpty() && cust.movedFromOrigin.isEmpty()) return this
        return filter { ch ->
            tv.own.owntv.core.live.isChannelVisible(ch, cust, hidden) &&
                (cust.movedFromOrigin[CustomizeKeys.channel(ch)]?.let { origin ->
                    key !is LiveKey.Folder || origin != folderContextKeys.value[key.id]
                } ?: true)
        }.map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
    }

    /**
     * One day of one channel, kept while that day is on screen so scrolling back up is instant —
     * newest-used first, and **hard-capped**.
     *
     * It was an unbounded map. The phone never had the television's whole-window preload, so it did
     * not share that crash, but it kept every row it had ever drawn: scrolling a 7,000-channel guide
     * accumulated all 7,000 of them, which is the same fault with a slower fuse. An LRU keeps what
     * the user is looking at and forgets the rest, so memory follows the screen, not the catalogue.
     */
    private val rowCache = object : LinkedHashMap<Long, List<EpgProgrammeEntity>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, List<EpgProgrammeEntity>>?) =
            size > MAX_CACHED_ROWS
    }

    /** Read straight from the cache, so a row scrolled back into view draws without a blank frame. */
    fun cachedRow(channelId: Long): List<EpgProgrammeEntity>? = synchronized(rowCache) { rowCache[channelId] }

    /** This channel's programmes for the selected day, read as the row comes into view. */
    suspend fun row(channel: ChannelEntity): List<EpgProgrammeEntity> {
        cachedRow(channel.id)?.let { return it }
        val w = window.value
        val rows = guide.row(channel, custom.value, epgOffset.value, w.start, w.end)
        synchronized(rowCache) { rowCache[channel.id] = rows }
        return rows
    }

    private val _onNow = MutableStateFlow<Map<Long, GuideSlot>>(emptyMap())

    /** What is on now, and next, for the rows the "On now" list can see. */
    val onNow: StateFlow<Map<Long, GuideSlot>> = _onNow

    /** Fill [onNow] for the rows on screen, in one query — channels already answered are skipped. */
    fun loadOnNow(visible: List<ChannelEntity>) {
        val missing = visible.filter { it.id !in _onNow.value }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val found = guide.onNow(
                channels = missing,
                cust = custom.value,
                globalShiftMinutes = epgOffset.value,
                atMs = System.currentTimeMillis(),
                lookAheadMs = ON_NOW_LOOK_AHEAD_MS,
            )
            // Channels with no guide are recorded as empty, or every scroll re-asks for them.
            _onNow.value = _onNow.value + missing.associate { it.id to (found[it.id] ?: GuideSlot(null, null)) }
        }
    }

    /** The synopsis, fetched when a programme is opened — the list queries leave it out. */
    suspend fun description(programmeId: Long): String? = guide.description(programmeId)

    /** Whether this programme can be replayed: an archive channel, already aired, still in window. */
    fun canCatchup(channel: ChannelEntity, programme: EpgProgrammeEntity): Boolean =
        tv.own.owntv.core.epg.GuideHistoryPolicy.canAttemptCatchup(channel.catchup, programme.startMs, System.currentTimeMillis())


    // --- Recording, from the guide (Plan D, Feature A). The television's own logic, phone-shaped. ---

    /** This profile's recordings, so a programme sheet knows whether it is already spoken for. */
    val recordingRows: StateFlow<List<tv.own.owntv.core.database.entity.RecordingEntity>> =
        settings.activeProfileId
            .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else recordings.observe(pid) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The recording already covering this programme, if there is one. */
    fun recordingFor(
        channel: ChannelEntity,
        programme: EpgProgrammeEntity,
    ): tv.own.owntv.core.database.entity.RecordingEntity? = recordingRows.value.firstOrNull {
        it.channelId == channel.id && it.programmeStartMs == programme.startMs &&
            it.status != tv.own.owntv.core.model.RecordingStatus.CANCELLED
    }

    /** Still to come, or already aired on a channel that keeps an archive. */
    fun canRecord(channel: ChannelEntity, programme: EpgProgrammeEntity): Boolean =
        programme.stopMs > System.currentTimeMillis() || canCatchup(channel, programme)

    /** Schedule it, or pull it from the archive when it has already been on. */
    fun record(channel: ChannelEntity, programme: EpgProgrammeEntity) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first().takeIf { it >= 0 } ?: return@launch
            val source = sourceDao.getById(channel.sourceId) ?: return@launch
            if (programme.stopMs <= System.currentTimeMillis()) {
                recordings.recordFromArchive(
                    profileId = pid,
                    channel = channel,
                    programme = programme,
                    source = source,
                    timeZone = settings.resolveCatchupTimeZone(),
                    xtream = xtream,
                )
                return@launch
            }
            val window = recordings.windowFor(programme.startMs, programme.stopMs)
            recordings.schedule(
                tv.own.owntv.core.database.entity.RecordingEntity(
                    profileId = pid,
                    sourceId = channel.sourceId,
                    channelId = channel.id,
                    channelName = channel.name,
                    channelIconUrl = channel.logoUrl,
                    epgChannelId = channel.epgChannelId,
                    streamUrl = channel.streamUrl,
                    httpHeaders = channel.httpHeaders,
                    title = programme.title,
                    description = programme.description,
                    programmeStartMs = programme.startMs,
                    programmeStopMs = programme.stopMs,
                    startMs = window.first,
                    stopMs = window.last,
                ),
            )
        }
    }

    fun stopRecording(recording: tv.own.owntv.core.database.entity.RecordingEntity) =
        recordings.stop(recording)

    fun cancelRecording(recording: tv.own.owntv.core.database.entity.RecordingEntity) =
        recordings.cancel(recording)

    /** The standing "record every showing" rule covering this programme, or null (D7). */
    suspend fun seriesRuleFor(
        channel: ChannelEntity,
        programme: EpgProgrammeEntity,
    ): tv.own.owntv.core.database.entity.RecordingRuleEntity? {
        val pid = settings.activeProfileId.first().takeIf { it >= 0 } ?: return null
        return recordings.ruleFor(pid, channel.id, programme.title)
    }

    /** Record every future showing of this title on this channel. */
    fun recordSeries(channel: ChannelEntity, programme: EpgProgrammeEntity) {
        viewModelScope.launch {
            val pid = settings.activeProfileId.first().takeIf { it >= 0 } ?: return@launch
            recordings.addSeriesRule(pid, channel, programme.title)
        }
    }

    /** Stop the standing rule, and drop the showings it had queued but not yet recorded. */
    fun stopSeries(rule: tv.own.owntv.core.database.entity.RecordingRuleEntity) {
        viewModelScope.launch { recordings.removeSeriesRule(rule) }
    }

    /** The title of a recording this one would contend with, shown before the user commits (D10). */
    suspend fun clashFor(channel: ChannelEntity, programme: EpgProgrammeEntity): String? {
        val window = recordings.windowFor(programme.startMs, programme.stopMs)
        val existing = recordingFor(channel, programme)
        return recordings
            .clashesWith(channel.sourceId, window.first, window.last, existing?.id ?: 0)
            .firstOrNull()
            ?.title
    }

    /** Watch the channel itself — the same tuner the Live screen and the mini player are showing. */
    fun watch(channel: ChannelEntity) = tuner.switchTo(channel)

    /** Replay a programme from the provider's archive. */
    fun playCatchup(channel: ChannelEntity, programme: EpgProgrammeEntity) =
        tuner.playCatchup(programme, channel)

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            if (channel.id in favoriteIds.value) userDataWriter.removeFavorite(pid, MediaType.LIVE, channel.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
        }
    }

    // ---- What the guide holds, and matching channels to it ----

    /** Bumped whenever the guide's own data changed under the rows on screen, so they read again. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision

    private val _matching = MutableStateFlow(false)
    val matching: StateFlow<Boolean> = _matching

    /** The suggestions the matcher was not sure enough about, waiting to be accepted or skipped. */
    private val _review = MutableStateFlow<List<EpgMatchSuggestion>>(emptyList())
    val review: StateFlow<List<EpgMatchSuggestion>> = _review

    private val _matchSummary = MutableStateFlow<EpgMatchSummary?>(null)
    val matchSummary: StateFlow<EpgMatchSummary?> = _matchSummary

    init {
        viewModelScope.launch {
            ctx.collect { if (it.profileId >= 0) loadStats() }
        }
        // The guide's own data changing under the screen — a feed added, re-synced or deleted — is
        // not something the rows can notice by themselves: each one keeps what it read. Room reports
        // every write to the programme table, so waiting for those writes to stop and redrawing once
        // covers all four cases without the app having to be restarted to see a new guide.
        viewModelScope.launch {
            epgSourceStore.sources
                .map { sources -> sources.map { it.id } }
                .distinctUntilChanged()
                .flatMapLatest { ids ->
                    if (ids.isEmpty()) flowOf(0)
                    else combine(ids.map { epgDao.countForSource(it) }) { counts -> counts.sum() }
                }
                .debounce(GUIDE_DATA_SETTLE_MS)
                .drop(1) // The first is the guide as it already stands on screen.
                .collect { refreshRows() }
        }
    }

    private suspend fun loadStats() {
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val ids = guide.guideSourceIds()
        val programmes = epgDao.countForSources(ids)
        val guideChannels = if (programmes > 0) epgDao.countGuideChannels(ids) else 0
        _stats.value = GuideStats(
            guideChannels = guideChannels,
            programmes = programmes,
            catchupChannels = channelDao.countCatchup(ctx.value.liveSourceIds),
            hasEpgSources = epgSourceStore.getAll().isNotEmpty(),
            // One row is enough to answer it, and one row is all that is read.
            mismatchedIds = programmes > 0 && channelDao.channelsWithGuide(ids, "", 1).isEmpty(),
        )
        // The phone loads guide rows one at a time (see [row]) — core's own guide_row line covers
        // those. What is only measurable here is the cold-open stats pass and how much the row cache
        // is actually holding, which is what Phase 6's per-row work on the television is compared to.
        tv.own.owntv.core.CorePerf.log {
            "guide_stats programmes=$programmes guideChannels=$guideChannels " +
                "sources=${ids.size} cachedRows=${synchronized(rowCache) { rowCache.size }} " +
                "totalMs=${android.os.SystemClock.elapsedRealtime() - startedAt}"
        }
    }

    fun currentEpgMatch(channel: ChannelEntity): String? =
        custom.value.epgMatchResolver.epgIdFor(channel)

    fun currentEpgShift(channel: ChannelEntity): Int? = EpgShift.overrideFor(custom.value, channel)

    fun globalEpgShift(): Int = epgOffset.value

    suspend fun availableEpgChannels(channelName: String, query: String): List<GuideCandidate> =
        if (ctx.value.profileId < 0) emptyList()
        else guideCandidates.forPicker(channelName, query)

    /** Point a channel at a guide channel by hand; null clears it and hands it back to the matcher. */
    fun setEpgMatch(channel: ChannelEntity, epgChannelId: String?) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            customize.setEpgMatch(pid, MediaType.LIVE, CustomizeKeys.channel(channel), epgChannelId)
            if (epgChannelId != null) fillMatched(listOf(epgChannelId)) else refreshRows()
        }
    }

    /** Shift this channel's guide by [minutes]; null follows the global offset from Settings. */
    fun setEpgShift(channel: ChannelEntity, minutes: Int?) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            val key = CustomizeKeys.channel(channel)
            customize.setEpgShift(pid, MediaType.LIVE, key, minutes)
            // The rows read the shift back off `custom`; wait for the write to land there first.
            withTimeoutOrNull(SHIFT_WRITE_TIMEOUT_MS) {
                custom.first { it.epgShifts[key]?.toIntOrNull() == minutes }
            }
            epgReader.invalidate(channel.id)
            refreshRows()
        }
    }

    /**
     * The review sheet's "Include guide logos" tick. Starts ticked when some EPG source already has
     * "Use this guide's logos" on; while ticked, every match this run makes also turns that option on for
     * the EPG sources that carry a logo for it. Same rule as the television.
     */
    private val _includeGuideLogos = MutableStateFlow(false)
    val includeGuideLogos: StateFlow<Boolean> = _includeGuideLogos.asStateFlow()

    fun setIncludeGuideLogos(include: Boolean) {
        _includeGuideLogos.value = include
    }

    /** Guide ids the current run applied on its own, before the review sheet opened. */
    private var runApplied: List<String> = emptyList()

    private suspend fun includeLogosIfTicked(epgIds: List<String>) {
        // Automatic matching was removed from the guide UI; stored logo choices are preserved.
    }

    private suspend fun openReview(suggestions: List<EpgMatchSuggestion>) {
        if (suggestions.isNotEmpty()) _includeGuideLogos.value = settings.epgUseLogos.first().isNotEmpty()
        _review.value = suggestions
    }

    /**
     * Match every channel with no working guide against the guide's own channels, by name.
     *
     * The confident hits are applied outright; the rest are queued for review. This is the same run
     * the television does, on the same core matcher — a phone just shows the result in a sheet.
     */
    fun autoMatchEpg() {
        if (_matching.value) return
        viewModelScope.launch {
            _matching.value = true
            try {
                val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
                val playlistIds = ctx.value.liveSourceIds
                if (playlistIds.isEmpty()) {
                    _matchSummary.value = EpgMatchSummary.AddPlaylist
                    return@launch
                }
                val outcome = autoMatcher.run(custom.value, playlistIds)
                if (!outcome.hadCandidates) {
                    _matchSummary.value = EpgMatchSummary.NoData
                    return@launch
                }
                val applied = outcome.applied
                runApplied = applied.map { it.second }

                openReview(outcome.review.map {
                    EpgMatchSuggestion(it.channel, it.epgChannelId, it.displayName, it.score)
                })
                _matchSummary.value = when {
                    applied.isEmpty() && outcome.review.isEmpty() -> EpgMatchSummary.AllMatched
                    // Everything found pointed at a guide channel with nothing scheduled. Reporting a
                    // match here would be a success message for a row that stays blank.
                    applied.isEmpty() && outcome.withheldForNoProgrammes == outcome.review.size ->
                        EpgMatchSummary.MatchedNoProgrammes
                    else -> EpgMatchSummary.AutoMatched(applied.size, outcome.review.size)
                }
                if (applied.isNotEmpty()) {
                    applyMatches(pid, applied.associate { it.first to it.second.matchKey() })
                    awaitMatches(applied.map { it.first })
                    fillMatched(applied.map { it.second })
                }
            } finally {
                _matching.value = false
            }
        }
    }

    /** Match one channel by name. The result goes to review rather than being applied silently. */
    fun autoMatchOne(channel: ChannelEntity) {
        if (_matching.value) return
        viewModelScope.launch {
            _matching.value = true
            try {
                val best = autoMatcher.one(channel)
                if (best == null) {
                    _matchSummary.value = EpgMatchSummary.NoMatch(channel.name)
                } else {
                    runApplied = emptyList()
                    openReview(listOf(
                        EpgMatchSuggestion(channel, best.epgChannelId, best.displayName, best.score),
                    ))
                    // Say so up front when the winner's guide channel is empty, instead of letting the
                    // user accept it and find a blank row. Same warning the television gives.
                    if (!best.hasProgrammes) _matchSummary.value = EpgMatchSummary.MatchedNoProgrammes
                }
            } finally {
                _matching.value = false
            }
        }
    }

    fun acceptSuggestion(suggestion: EpgMatchSuggestion) {
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            val key = CustomizeKeys.channel(suggestion.channel)
            customize.setEpgMatch(pid, MediaType.LIVE, key, suggestion.epgChannelId)
            _review.value = _review.value.filterNot { it.channel.id == suggestion.channel.id }
            includeLogosIfTicked(listOf(suggestion.epgChannelId))
            awaitMatches(listOf(key))
            fillMatched(listOf(suggestion.epgChannelId))
        }
    }

    fun skipSuggestion(suggestion: EpgMatchSuggestion) {
        _review.value = _review.value.filterNot { it.channel.id == suggestion.channel.id }
    }

    fun acceptAllSuggestions() {
        val all = _review.value
        if (all.isEmpty()) return
        viewModelScope.launch {
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            val pairs = all.associate { CustomizeKeys.channel(it.channel) to it.epgChannelId.matchKey() }
            applyMatches(pid, pairs)
            val keys = pairs.keys
            _review.value = emptyList()
            includeLogosIfTicked(all.map { it.epgChannelId })
            awaitMatches(keys)
            fillMatched(all.map { it.epgChannelId })
        }
    }

    /** Close the review list and clear the outcome line. */
    fun clearReview() {
        // Closing the sheet still honours the tick for the matches the run applied on its own.
        if (_review.value.isNotEmpty() && runApplied.isNotEmpty()) viewModelScope.launch { includeLogosIfTicked(emptyList()) }
        _review.value = emptyList()
        _matchSummary.value = null
    }

    /**
     * Fill in the newly matched channels' programmes from the downloaded guide, then redraw.
     *
     * A match is only a name pointing at a guide channel — the programmes for it may never have been
     * stored, because nothing was asking for them until now.
     */
    private suspend fun fillMatched(epgIds: Collection<String>) {
        val ids = epgIds.map { it.trim().lowercase() }.filterTo(HashSet()) { it.isNotEmpty() }
        if (ids.isEmpty()) return
        runCatching { epgRepository.storeProgrammesForIdsFromCache(ids) }
        refreshRows()
        // A single match that produced nothing is worth saying out loud: the match worked, but the
        // feed simply has no schedule for that channel, and an empty row looks like a failure.
        if (ids.size == 1) {
            val upcoming = runCatching {
                epgDao.countUpcomingForChannel(ids.first(), System.currentTimeMillis())
            }.getOrDefault(1)
            if (upcoming == 0) _matchSummary.value = EpgMatchSummary.MatchedNoProgrammes
        }
    }

    /** A guide id, stored the way `setEpgMatch` stores one, so a lookup by key finds it. */
    private fun String.matchKey(): String = trim().lowercase()

    /**
     * Write a whole batch of matches in ONE edit. Accepting two hundred rows one call at a time is
     * two hundred separate disk writes, which is slow enough to look like nothing happened.
     */
    private suspend fun applyMatches(pid: Long, matches: Map<String, String>) {
        val clean = matches.filterValues { it.isNotEmpty() }
        if (clean.isEmpty()) return
        customize.update(pid, MediaType.LIVE) { it.copy(epgMatches = it.epgMatches + clean) }
    }

    /**
     * The rows read the matches back off [custom]; wait for the writes to land there first, or the
     * redraw below re-reads the old map and the accepted matches look as if they did nothing.
     */
    private suspend fun awaitMatches(keys: Collection<String>) {
        if (keys.isEmpty()) return
        withTimeoutOrNull(MATCH_WRITE_TIMEOUT_MS) {
            custom.first { c -> keys.all { it in c.epgMatches } }
        }
    }

    /** Drop everything read so far and tell the rows on screen to ask again. */
    private suspend fun refreshRows() {
        synchronized(rowCache) { rowCache.clear() }
        // The reader keeps its own now/next for five minutes, keyed by channel — stale the moment a
        // match changes, so it goes too.
        epgReader.clearCache()
        _onNow.value = emptyMap()
        _revision.value++
        loadStats()
    }

    private companion object {
        const val PAGE_SIZE = 40
        // How many guide rows to keep read. A phone screen shows a handful; this is generous enough
        // that scrolling back never re-reads, and small enough that the guide's memory does not grow
        // with the catalogue.
        const val MAX_CACHED_ROWS = 240
        const val SEARCH_DEBOUNCE_MS = 300L
        // A sync writes the guide in batches, so every batch is reported. This must be longer than
        // the gap between two batch writes, or it coalesces nothing and the guide redraws per batch.
        // Measured on the television (2026-09-18, same core sync): batches land 2.3-6.1 s apart, so
        // the old 1,500 ms never coalesced them. 10 s clears the longest observed gap.
        const val GUIDE_DATA_SETTLE_MS = 10_000L
        const val MAX_CHANNELS = 20_000
        const val SHIFT_WRITE_TIMEOUT_MS = 1_000L
        // Accept-all can write a few hundred matches, so it gets longer than a single shift does.
        const val MATCH_WRITE_TIMEOUT_MS = 5_000L
        const val ON_NOW_LOOK_AHEAD_MS = 6L * 60 * 60 * 1000
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
