package tv.own.owntv.mobile.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * "Add subtitles": search OpenSubtitles for the film or episode playing, and attach the chosen one
 * without leaving the picture.
 *
 * Every dead end says what to do about it — signed out, out of downloads for today, nothing found in
 * the chosen language — because a search that simply comes back empty is indistinguishable from a
 * search that is broken.
 */
@Composable
fun SubtitleSearchSheet(onDismiss: () -> Unit, vm: SubtitleSearchViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val applying by vm.applying.collectAsStateWithLifecycle()
    val quota by vm.quotaNote.collectAsStateWithLifecycle()
    // The sheet is opened and closed many times over one film, and the view model outlives it, so the
    // search is started on each open rather than once at construction.
    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(Unit) { vm.applied.collect { onDismiss() } }

    var query by remember { mutableStateOf(vm.initialQuery) }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.player_subtitles_search_title),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.player_subtitles_title_label)) },
            singleLine = true,
            keyboardActions = androidx.compose.foundation.text.KeyboardActions { vm.editSearch(query) },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapTiny),
        )
        Row(
            Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            TextButton(onClick = { vm.editSearch(query) }) {
                Text(stringResource(R.string.player_subtitles_search))
            }
            if ((state as? SubtitleSearchViewModel.UiState.Results)?.showingAllLanguages == false ||
                (state as? SubtitleSearchViewModel.UiState.Empty)?.showingAllLanguages == false
            ) {
                TextButton(onClick = vm::showAllLanguages) {
                    Text(stringResource(R.string.player_subtitles_show_all_languages))
                }
            }
        }

        quota?.let { q ->
            Note(
                if (q.reset != null) {
                    pluralStringResource(R.plurals.player_subtitles_remaining_reset, q.remaining, q.remaining, q.reset)
                } else {
                    pluralStringResource(R.plurals.player_subtitles_remaining_count, q.remaining, q.remaining)
                },
            )
        }

        when (val s = state) {
            is SubtitleSearchViewModel.UiState.Loading -> Box(
                Modifier.fillMaxWidth().padding(MobileDimens.ScreenPaddingH),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }

            is SubtitleSearchViewModel.UiState.SignedOut -> Note(
                stringResource(
                    if (s.sessionExpired) {
                        R.string.player_subtitles_session_expired
                    } else {
                        R.string.player_subtitles_account_needed
                    },
                ),
            )

            is SubtitleSearchViewModel.UiState.Empty -> Note(
                stringResource(
                    if (s.showingAllLanguages) {
                        R.string.player_subtitles_no_matches_all_languages
                    } else {
                        R.string.player_subtitles_no_matches_chosen_language
                    },
                ),
            )

            is SubtitleSearchViewModel.UiState.Error -> {
                Note(
                    stringResource(
                        when (s.kind) {
                            SubtitleSearchViewModel.UiState.ErrorKind.LIMIT_REACHED ->
                                R.string.player_subtitles_limit_reached
                            SubtitleSearchViewModel.UiState.ErrorKind.NETWORK ->
                                R.string.player_subtitles_network_error
                        },
                    ),
                )
                TextButton(
                    onClick = vm::retry,
                    modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
                ) {
                    Text(stringResource(R.string.player_subtitles_try_again))
                }
            }

            is SubtitleSearchViewModel.UiState.Results -> LazyColumn(
                Modifier.heightIn(max = sheetListHeight()),
            ) {
                items(s.results, key = { it.fileId }) { result ->
                    val separator = stringResource(R.string.player_subtitles_tags_separator)
                    val tags = buildList {
                        if (result.hearingImpaired) add(stringResource(R.string.player_subtitles_sdh))
                        if (result.aiTranslated) add(stringResource(R.string.player_subtitles_ai))
                        if (result.fromTrusted) add(stringResource(R.string.player_subtitles_trusted))
                        if (result.downloads > 0) {
                            add(
                                pluralStringResource(
                                    R.plurals.player_subtitles_download_count,
                                    result.downloads,
                                    result.downloads,
                                ),
                            )
                        }
                    }.joinToString(separator)
                    MobileListRow(
                        title = result.languageName
                            ?: result.language
                            ?: stringResource(R.string.player_subtitles_subtitle),
                        subtitle = listOfNotNull(result.releaseName, tags.takeIf { it.isNotEmpty() })
                            .joinToString(separator)
                            .takeIf { it.isNotEmpty() },
                        subtitleMaxLines = 2,
                        trailing = if (applying == result.fileId) {
                            { CircularProgressIndicator(Modifier.size(20.dp)) }
                        } else {
                            null
                        },
                        onClick = { vm.select(result) },
                    )
                }
            }
        }
        Note(stringResource(R.string.player_subtitles_api_notice))
    }
}

@Composable
private fun Note(text: String) {
    Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapTiny)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
