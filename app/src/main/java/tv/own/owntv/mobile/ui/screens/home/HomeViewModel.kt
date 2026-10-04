package tv.own.owntv.mobile.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.home.TrendingHomeItem
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.core.home.HomeFeed
import tv.own.owntv.core.home.HomeFeedReader
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.weather.WeatherInfo
import tv.own.owntv.core.weather.WeatherRepository
import tv.own.owntv.mobile.ui.screens.ContentActions
import tv.own.owntv.mobile.ui.screens.library.VodTuner

/**
 * Home, for the phone.
 *
 * The rails themselves are core's — the same reader the television's Home uses, so the rows are the
 * ones the user arranged there, in that order, minus the ones they hid. What is here is only the
 * phone's part: when to rebuild them (this screen is left and returned to rather than lived on), and
 * playing what was tapped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val reader: HomeFeedReader,
    private val settings: SettingsRepository,
    private val profileDao: ProfileDao,
    private val tuner: VodTuner,
    private val actions: ContentActions,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val metadata: MetadataRepository,
    weatherRepository: WeatherRepository,
    connectivity: ConnectivityObserver,
) : ViewModel() {

    private val profileId: StateFlow<Long> = settings.activeProfileId
        .map { stored -> if (stored >= 0) profileDao.resolveExistingProfileId(stored) ?: -1L else -1L }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1L)

    /**
     * One conflated request to rebuild. The screen asks on every entry, because coming back from a
     * film is exactly when the continue rows are wrong, and a profile switch asks on its own —
     * `collectLatest` drops a load already running rather than letting the two queue up.
     */
    private val reloads = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val _feed = MutableStateFlow<HomeFeed?>(null)

    /** Null until the first load finishes — the screen shows nothing rather than a false empty state. */
    val feed: StateFlow<HomeFeed?> = _feed

    init {
        reloads.tryEmit(Unit)
        viewModelScope.launch {
            combine(profileId, reloads) { pid, _ -> pid }.collectLatest { pid ->
                _feed.value = if (pid < 0) HomeFeed() else reader.load(pid)
            }
        }
    }

    fun refresh() {
        reloads.tryEmit(Unit)
    }

    /** The weather chip, when the setting is on and there is a network to ask over. */
    val weather: StateFlow<WeatherInfo?> =
        combine(connectivity.isOnline, settings.weatherEnabled, settings.weatherLocation) { online, enabled, location ->
            Triple(online, enabled, location)
        }.flatMapLatest { (online, enabled, location) ->
            if (!online || !enabled) flowOf<WeatherInfo?>(null) else flow { emit(weatherRepository.get(location)) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val fahrenheit: StateFlow<Boolean> = settings.weatherFahrenheit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Switch a live row between logo cards and what-is-on-now. Stored where the TV app reads it. */
    fun toggleLiveMode(row: HomeRow) {
        val pid = profileId.value.takeIf { it >= 0 } ?: return
        viewModelScope.launch {
            settings.updateHomeConfig(pid) { config ->
                when (row) {
                    HomeRow.RECENT_CHANNELS -> config.copy(recentLiveMode = config.recentLiveMode.toggled())
                    HomeRow.FAVORITE_CHANNELS -> config.copy(favoriteLiveMode = config.favoriteLiveMode.toggled())
                    else -> config
                }
            }
            // The guide slice is only read for a row that is in "on now" mode, so the feed has to be
            // rebuilt for the row that just switched into it.
            refresh()
        }
    }

    fun modeOf(row: HomeRow, state: HomeFeed): HomeLiveRowMode = when (row) {
        HomeRow.FAVORITE_CHANNELS -> state.config.favoriteLiveMode
        else -> state.config.recentLiveMode
    }

    /** Play a film, or an episode, from where it was left. [onStarted] runs only if the picture is
     *  opening here rather than in another app — the same rule the detail screen plays by. */
    fun playMovie(movieId: Long, positionMs: Long, onStarted: () -> Unit) {
        viewModelScope.launch { if (tuner.playMovie(movieId, positionMs)) onStarted() }
    }

    fun playEpisode(episodeId: Long, positionMs: Long, onStarted: () -> Unit) {
        viewModelScope.launch { if (tuner.playEpisode(episodeId, positionMs)) onStarted() }
    }

    // --- Now Trending ---------------------------------------------------------------------------

    /**
     * Act on the trending item: play the film, or open the show's episodes.
     *
     * The saved row is re-read first, because the hero is built from a snapshot the worker took and a
     * sync since then may have replaced or dropped that exact version. [onUnavailable] is the case the
     * television shows the same message for, and the feed is rebuilt so the dead item goes.
     */
    fun activateTrending(
        item: TrendingHomeItem,
        onPlayerOpened: () -> Unit,
        onOpenSeries: (Long) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        viewModelScope.launch {
            when (val current = revalidateTrendingItem(item)) {
                is TrendingHomeItem.Movie ->
                    if (tuner.playMovie(current.movie.id, 0L)) onPlayerOpened() else gone(onUnavailable)
                is TrendingHomeItem.Series -> onOpenSeries(current.series.id)
                null -> gone(onUnavailable)
            }
        }
    }

    private fun gone(onUnavailable: () -> Unit) {
        onUnavailable()
        refresh()
    }

    /** Re-resolve the exact saved provider row, in case a sync replaced it since the snapshot. */
    private suspend fun revalidateTrendingItem(item: TrendingHomeItem): TrendingHomeItem? =
        withContext(Dispatchers.IO) {
            when (item) {
                is TrendingHomeItem.Movie -> movieDao.getById(item.movie.id)
                    ?.takeIf { it.sourceId == item.snapshot.sourceId }
                    ?.let { item.copy(movie = it) }
                is TrendingHomeItem.Series -> seriesDao.getSeriesById(item.series.id)
                    ?.takeIf { it.sourceId == item.snapshot.sourceId }
                    ?.let { item.copy(series = it) }
            }
        }

    /** The full cached TMDB payload the details sheet shows, fetched under Trending's own TMDB id. */
    suspend fun resolveTrendingDetails(item: TrendingHomeItem): TrendingDetails =
        withContext(Dispatchers.IO) {
            val cache = when (item) {
                is TrendingHomeItem.Movie -> metadata.resolveKnownMovie(item.movie, item.snapshot.tmdbId)
                is TrendingHomeItem.Series -> metadata.resolveKnownSeries(item.series, item.snapshot.tmdbId)
            }
            TrendingDetails(cache, settings.metadataConfig().mode.tmdbWins)
        }

    data class TrendingDetails(val cache: MetadataCacheEntity?, val tmdbWins: Boolean)

    // --- The long-press menu ------------------------------------------------------------------------

    val favoriteChannels: StateFlow<Set<Long>> = favoriteIds(MediaType.LIVE)
    val favoriteMovies: StateFlow<Set<Long>> = favoriteIds(MediaType.MOVIE)
    val favoriteSeries: StateFlow<Set<Long>> = favoriteIds(MediaType.SERIES)

    private fun favoriteIds(type: MediaType): StateFlow<Set<Long>> = actions.favoriteIds(type)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Favouriting or hiding changes what the rails hold, so the feed is rebuilt after either. */
    fun toggleFavorite(type: MediaType, itemId: Long) {
        viewModelScope.launch {
            actions.toggleFavorite(type, itemId)
            refresh()
        }
    }

    fun hide(type: MediaType, itemId: Long) {
        viewModelScope.launch {
            actions.hide(type, itemId)
            refresh()
        }
    }

    fun download(type: MediaType, itemId: Long) {
        viewModelScope.launch { actions.download(type, itemId) }
    }
}
