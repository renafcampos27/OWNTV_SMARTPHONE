package tv.own.owntv.mobile.ui.screens.settings

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.dao.LinkedSubtitle
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.screens.settings.customize.ConfirmDialog

/**
 * The subtitle files already on the phone, and the four ways to remove them: one file, all films,
 * all shows, or the lot.
 *
 * Every delete is confirmed, because a downloaded subtitle costs a download from a daily allowance
 * that is small — deleting one by accident is not free.
 */
@Composable
fun DeleteSubtitlesPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DeleteSubtitlesViewModel = koinViewModel(),
) {
    val section by vm.section.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val movieCount by vm.movieCount.collectAsStateWithLifecycle()
    val seriesCount by vm.seriesCount.collectAsStateWithLifecycle()

    var deletingOne by remember { mutableStateOf<LinkedSubtitle?>(null) }
    var deletingSection by remember { mutableStateOf<DeleteSubtitlesViewModel.Section?>(null) }
    var deletingAll by remember { mutableStateOf(false) }

    val sections = DeleteSubtitlesViewModel.Section.entries
    val counts = mapOf(
        DeleteSubtitlesViewModel.Section.MOVIES to movieCount,
        DeleteSubtitlesViewModel.Section.SERIES to seriesCount,
    )

    SettingsPage(modifier) {
        settingsGroup(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MobileIcons.ArrowBack,
                        contentDescription = stringResource(R.string.settings_close),
                    )
                }
                Text(
                    stringResource(R.string.settings_delete_subtitles),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        settingsNote(R.string.player_subtitles_delete_description)
        settingsGroup(key = "sections") {
            FilterChipRow(
                labels = sections.map {
                    stringResource(
                        R.string.settings_section_count,
                        stringResource(it.labelRes()),
                        counts.getValue(it),
                    )
                },
                selectedIndex = sections.indexOf(section),
                onSelect = { vm.selectSection(sections[it]) },
            )
        }

        if (items.isEmpty()) {
            settingsNote(R.string.settings_no_downloaded_subtitles)
        }
        items(items, key = { it.cacheId }) { item ->
            MobileListRow(
                title = item.contentTitle,
                subtitle = listOfNotNull(item.languageName ?: item.language, item.releaseName)
                    .joinToString(stringResource(R.string.content_metadata_separator))
                    .ifBlank { null },
                trailing = {
                    TextButton(onClick = { deletingOne = item }) {
                        Text(stringResource(R.string.common_delete))
                    }
                },
            )
        }

        settingsGroup(key = "delete-section") {
            TextButton(
                onClick = { deletingSection = section },
                enabled = items.isNotEmpty(),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text(stringResource(section.deleteAllRes())) }

            TextButton(
                onClick = { deletingAll = true },
                enabled = movieCount + seriesCount > 0,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text(stringResource(R.string.settings_delete_all)) }
        }
    }

    deletingOne?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_subtitle),
            message = stringResource(R.string.settings_delete_subtitle_message, item.contentTitle),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { vm.deleteOne(item); deletingOne = null },
            onDismiss = { deletingOne = null },
        )
    }

    deletingSection?.let { target ->
        ConfirmDialog(
            title = stringResource(target.deleteAllRes()),
            message = stringResource(R.string.settings_delete_all_subtitles),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { vm.deleteAllInSection(target); deletingSection = null },
            onDismiss = { deletingSection = null },
        )
    }

    if (deletingAll) {
        ConfirmDialog(
            title = stringResource(R.string.settings_delete_all),
            message = stringResource(R.string.settings_delete_all_subtitles),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { vm.deleteAll(); deletingAll = false },
            onDismiss = { deletingAll = false },
        )
    }
}

private fun DeleteSubtitlesViewModel.Section.labelRes() = when (this) {
    DeleteSubtitlesViewModel.Section.MOVIES -> R.string.settings_movies
    DeleteSubtitlesViewModel.Section.SERIES -> R.string.settings_series
}

private fun DeleteSubtitlesViewModel.Section.deleteAllRes() = when (this) {
    DeleteSubtitlesViewModel.Section.MOVIES -> R.string.settings_delete_all_movies
    DeleteSubtitlesViewModel.Section.SERIES -> R.string.settings_delete_all_series
}
