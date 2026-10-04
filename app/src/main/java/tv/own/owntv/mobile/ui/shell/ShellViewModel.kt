package tv.own.owntv.mobile.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.core.nav.NavVisibility
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.StartupMode

/** Shell state: which destinations the active playlist offers, and the tap that sends a list home. */
@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModel(
    navVisibility: NavVisibility,
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val profileDao: ProfileDao,
    private val settings: SettingsRepository,
    private val customize: tv.own.owntv.core.customize.CustomizationStore,
) : ViewModel() {

    /** Core's rule, unchanged — the same set the TV app's rail is built from. */
    val visibleSections: StateFlow<Set<MainSection>> = navVisibility.visibleSections()
        .map { it - setOf(MainSection.MOVIES, MainSection.SERIES, MainSection.DOWNLOADS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainSection.allBrowse)

    /**
     * The playlists the active profile can pick between, for the top bar's selector.
     *
     * The profile's own linked sources, **not** `activeProfileSources()` — that one has the chosen
     * playlist already applied, so a picker built on it would offer only the playlist it is showing.
     */
    val playlists: StateFlow<List<SourceEntity>> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) flowOf(emptyList()) else sourceDao.observeForProfile(pid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Which one is showing. `-1` — or an id the profile no longer has — means All playlists. */
    val activePlaylistId: StateFlow<Long> = settings.defaultSourceId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1L)

    /** Narrow every browse screen to one playlist, or to all of them. Persisted, so it survives a
     *  restart — it is the same value Settings → Playlists calls the default. */
    fun selectPlaylist(sourceId: Long) {
        viewModelScope.launch { settings.setDefaultSource(sourceId) }
    }

    private val _scrollToTop = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits a route whose list should jump back to the top — a long press on its nav item. */
    val scrollToTop: SharedFlow<String> = _scrollToTop.asSharedFlow()

    fun requestScrollToTop(route: String) {
        viewModelScope.launch { _scrollToTop.emit(route) }
    }

    /**
     * Where this launch should land, from the profile's own "Start on" setting.
     *
     * A stored channel is looked up three ways because a re-sync gives every channel a new row id:
     * the provider's own id first, then the name, then the old row id. Losing the channel is not a
     * reason to refuse to start, so anything unresolved lands on Home and says so.
     */
    suspend fun resolveStartup(): StartupTarget {
        val ctx = activeProfileSources(settings, sourceDao).first { it.profileId >= 0L }
        return when (settings.startupMode(ctx.profileId).first()) {
            StartupMode.HOME -> StartupTarget.Home
            StartupMode.FAVORITES -> StartupTarget.Favorites
            StartupMode.LAST_CHANNEL -> {
                val channel = channelDao.recentlyWatched(ctx.profileId, 1).first().firstOrNull()
                if (channel != null && isVisible(channel, ctx.profileId, ctx.sources.map { it.id }.toSet())) {
                    StartupTarget.Channel(channel.id)
                } else {
                    StartupTarget.Home
                }
            }
            StartupMode.SPECIFIC_CHANNEL -> {
                val ref = settings.startupChannel(ctx.profileId).first()
                val channel = ref?.let {
                    (it.remoteId?.takeIf(String::isNotBlank)?.let { id -> channelDao.findByRemote(it.sourceId, id) })
                        ?: channelDao.findByName(it.sourceId, it.name)
                        ?: it.itemId.takeIf { id -> id > 0L }?.let { id -> channelDao.getById(id) }?.takeIf { candidate ->
                            candidate.sourceId == it.sourceId &&
                                (it.remoteId?.takeIf(String::isNotBlank)?.let { remote -> candidate.remoteId == remote }
                                    ?: (candidate.name == it.name))
                        }
                }
                if (channel != null && isVisible(channel, ctx.profileId, ctx.sources.map { it.id }.toSet())) {
                    StartupTarget.Channel(channel.id)
                } else {
                    StartupTarget.ChannelUnavailable
                }
            }
        }
    }

    /** A channel the profile cannot reach must not be opened by a setting the profile did not set. */
    private suspend fun isVisible(channel: ChannelEntity, profileId: Long, sourceIds: Set<Long>): Boolean {
        if (channel.sourceId !in sourceIds || !AdultCategoryClassifier.allows(profileId, channel.categoryId, profileDao, categoryDao)) return false
        val custom = customize.observe(profileId, tv.own.owntv.core.model.MediaType.LIVE).first()
        if (tv.own.owntv.core.customize.CustomizeKeys.channel(channel) in custom.hiddenItems) return false
        return channel.categoryId?.let { categoryDao.getById(it) }?.let {
            tv.own.owntv.core.customize.CustomizeKeys.category(it) !in custom.hiddenCategories
        } != false
    }

}

/** Where a launch lands. */
sealed interface StartupTarget {
    data object Home : StartupTarget
    data object Favorites : StartupTarget
    data class Channel(val id: Long) : StartupTarget

    /** The chosen channel is gone — the playlist dropped it, or a re-sync renamed it. */
    data object ChannelUnavailable : StartupTarget
}

/**
 * "Start on: Favorites", handed to Live TV.
 *
 * The shell decides it and the Live TV screen acts on it, and the two are built at different moments
 * on different screens, so it is a one-shot the shell sets and Live TV takes exactly once — a second
 * visit to Live TV in the same session is an ordinary visit.
 */
class StartupLiveSelection {
    private var pendingFavorites = false

    fun requestFavorites() { pendingFavorites = true }

    fun consumeFavorites(): Boolean = pendingFavorites.also { pendingFavorites = false }
}
