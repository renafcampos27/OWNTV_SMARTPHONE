package tv.own.owntv.mobile.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.LinkedSubtitle
import tv.own.owntv.core.subtitles.SubtitleController

/** The subtitle files already downloaded, per Movies and Series, with the four ways to delete them. */
class DeleteSubtitlesViewModel(
    private val controller: SubtitleController,
) : ViewModel() {

    enum class Section(val mediaType: String) { MOVIES("MOVIE"), SERIES("SERIES") }

    private val _section = MutableStateFlow(Section.MOVIES)
    val section: StateFlow<Section> = _section.asStateFlow()

    private val _items = MutableStateFlow<List<LinkedSubtitle>>(emptyList())
    val items: StateFlow<List<LinkedSubtitle>> = _items.asStateFlow()

    /** Counts per section, for the tab labels and the empty states. */
    private val _movieCount = MutableStateFlow(0)
    val movieCount: StateFlow<Int> = _movieCount.asStateFlow()
    private val _seriesCount = MutableStateFlow(0)
    val seriesCount: StateFlow<Int> = _seriesCount.asStateFlow()

    init { refresh() }

    fun selectSection(section: Section) {
        _section.value = section
        reloadItems()
    }

    fun deleteOne(item: LinkedSubtitle) {
        viewModelScope.launch {
            controller.deleteCached(item.cacheId)
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch { controller.deleteAllDownloads(); refresh() }
    }

    fun deleteAllInSection(section: Section) {
        viewModelScope.launch { controller.deleteAllForType(section.mediaType); refresh() }
    }

    private fun refresh() {
        viewModelScope.launch {
            _movieCount.value = controller.downloadsForType(Section.MOVIES.mediaType).size
            _seriesCount.value = controller.downloadsForType(Section.SERIES.mediaType).size
            reloadItems()
        }
    }

    private fun reloadItems() {
        viewModelScope.launch { _items.value = controller.downloadsForType(_section.value.mediaType) }
    }
}
