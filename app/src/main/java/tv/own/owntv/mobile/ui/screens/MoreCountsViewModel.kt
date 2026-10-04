package tv.own.owntv.mobile.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/** How many channels, films and shows a list holds, one number per type. */
data class TypeCounts(val live: Int = 0, val movies: Int = 0, val series: Int = 0) {
    val total: Int get() = live + movies + series
}

/**
 * The counts the More rows and the two screens' chips carry.
 *
 * Every one of these queries already exists on the DAOs — `countFavorites` and `countHistory`, both
 * joined to the content table so a favourite whose channel went stale on a re-sync is not counted
 * before the relink purges it. Nothing here is a new query.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MoreCountsViewModel(
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    settings: SettingsRepository,
    sourceDao: SourceDao,
) : ViewModel() {

    private val ctx: StateFlow<ActiveProfileSources> = activeProfileSources(settings, sourceDao)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActiveProfileSources(-1L, emptyList()))

    val favorites: StateFlow<TypeCounts> = counts(favorites = true)
    val history: StateFlow<TypeCounts> = counts(favorites = false)

    private fun counts(favorites: Boolean): StateFlow<TypeCounts> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) {
                flowOf(TypeCounts())
            } else {
                // An empty id list makes the IN clause match nothing useful — the sentinel every
                // other count flow in the app uses.
                fun ids(type: MediaType) = c.sourceIdsFor(type).ifEmpty { listOf(-1L) }
                combine(
                    if (favorites) channelDao.countFavorites(c.profileId, ids(MediaType.LIVE))
                    else channelDao.countHistory(c.profileId, ids(MediaType.LIVE)),
                    if (favorites) movieDao.countFavorites(c.profileId, ids(MediaType.MOVIE))
                    else movieDao.countHistory(c.profileId, ids(MediaType.MOVIE)),
                    if (favorites) seriesDao.countFavorites(c.profileId, ids(MediaType.SERIES))
                    else seriesDao.countHistory(c.profileId, ids(MediaType.SERIES)),
                    ::TypeCounts,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TypeCounts())
}
