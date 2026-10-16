package tv.own.owntv.mobile.ui.screens.recordings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.content.SearchReader
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.recording.RecordingManager
import tv.own.owntv.core.settings.SettingsRepository

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class ManualRecordingViewModel(private val settings: SettingsRepository, private val sources: SourceDao,
    private val search: SearchReader, private val recordings: RecordingManager): ViewModel() {
    private val ctx = activeProfileSources(settings, sources).stateIn(viewModelScope, SharingStarted.Eagerly,
        tv.own.owntv.core.repository.ActiveProfileSources(-1L, emptyList()))
    val query = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val result = MutableStateFlow<Int?>(null)
    val scheduled = MutableStateFlow(false)
    val selected = MutableStateFlow<ChannelEntity?>(null)
    val channels = combine(query.debounce(250), ctx) { q, c -> q to c }.mapLatest { (q,c) ->
        val liveOnly = c.copy(sources=c.sources.map { it.copy(syncMovies=false,syncSeries=false) })
        search.search(c.profileId, liveOnly, q.trim(), 40, includeProgrammes=false).channels.map { it.channel }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    init { viewModelScope.launch { ctx.map { it.profileId }.distinctUntilChanged().collect {
        selected.value=null; result.value=null; scheduled.value=false
    } } }
    fun schedule(date: String, start: String, end: String) {
        if (busy.value) return
        val channel = selected.value ?: return
        val profile = ctx.value.profileId
        val window = ManualRecordingInput.resolve(date,start,end,java.time.ZoneId.systemDefault())
        if (window == null) { result.value=tv.own.owntv.mobile.R.string.own_manual_invalid; return }
        busy.value=true; result.value=null
        viewModelScope.launch {
            try {
                if (profile != settings.activeProfileId.first() || channel.sourceId !in ctx.value.liveSourceIds) return@launch
                val row=recordings.scheduleManual(profile,channel,window.first,window.last)
                if (profile != settings.activeProfileId.first()) return@launch
                scheduled.value = row != null && row.status != tv.own.owntv.core.model.RecordingStatus.FAILED
                if (!scheduled.value) result.value=tv.own.owntv.mobile.R.string.own_manual_failed
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { result.value=tv.own.owntv.mobile.R.string.own_manual_failed }
            finally { busy.value=false }
        }
    }
}
