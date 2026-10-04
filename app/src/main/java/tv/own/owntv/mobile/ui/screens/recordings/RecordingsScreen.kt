package tv.own.owntv.mobile.ui.screens.recordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.mobile.ui.components.ExportDocument
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.recording.RecordingRules
import tv.own.owntv.core.recording.RecordingStorageInfo
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.SectionHeader
import tv.own.owntv.mobile.ui.theme.MobileDimens
import java.text.NumberFormat

/**
 * Live TV recorded onto this phone.
 *
 * Four chips rather than the television's five groups — a phone list is scrolled, not scanned, and
 * the question a user opens this with is one of four: what is happening now, what will happen, what
 * can I watch, and what went wrong. **Missed and failed share the last chip**, because from the
 * user's side both mean "this is not going to be there", and each row still says which it was and
 * why.
 */
@Composable
fun RecordingsScreen(
    onPlayerOpened: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Draw only the list, grouped under its status headings. Downloads hosts this as its **Live TV**
     * tab and already owns the storage bar and the type chips — a recording is a download of a live
     * channel, so it shares that screen rather than repeating it inside it.
     */
    embedded: Boolean = false,
    vm: RecordingsViewModel = koinViewModel(),
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(RecordingTab.ACTIVE) }
    var menuFor by remember { mutableStateOf<RecordingEntity?>(null) }
    var savingCopyOf by remember { mutableStateOf<RecordingEntity?>(null) }

    // The system's own "where do you want it?" — the only way an app may write outside its folder
    // on Android 11 without asking for a permission Play scrutinises.
    val saveCopy = androidx.activity.compose.rememberLauncherForActivityResult(
        ExportDocument("video/*"),
    ) { uri ->
        val recording = savingCopyOf
        savingCopyOf = null
        if (uri != null && recording != null) vm.export(recording, uri)
    }

    LaunchedEffect(playing) {
        if (playing) {
            vm.playerOpened()
            onPlayerOpened()
        }
    }

    // Embedded there are no chips, so every recording is shown at once under its own heading —
    // the television's shape, and the one that lets a running recording be seen without hunting
    // for the chip it is hiding behind.
    val sections = RecordingTab.entries
        .map { section -> section to rows.filter { section.holds(it.status) } }
        .filter { it.second.isNotEmpty() }
    val shown = if (embedded) rows else rows.filter { tab.holds(it.status) }

    Column(modifier.fillMaxSize()) {
        if (!embedded) {
            storage?.let { StorageHeader(it, vm.timersAreExact) }
            FilterChipRow(
                labels = RecordingTab.entries.map { stringResource(it.labelRes) },
                selectedIndex = RecordingTab.entries.indexOf(tab),
                onSelect = { tab = RecordingTab.entries[it] },
            )
        }

        if (shown.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(MobileDimens.GapLarge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.recording_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (embedded) {
                    sections.forEach { (section, items) ->
                        item(key = "header_${section.name}") {
                            SectionHeader(title = stringResource(section.labelRes))
                        }
                        items(items, key = { it.id }) { recording ->
                            RecordingRow(
                                recording = recording,
                                onClick = {
                                    if (recording.status == RecordingStatus.COMPLETED || recording.status == RecordingStatus.PARTIAL) vm.play(recording)
                                    else menuFor = recording
                                },
                                onLongClick = { menuFor = recording },
                            )
                        }
                    }
                } else {
                    items(shown, key = { it.id }) { recording ->
                        RecordingRow(
                            recording = recording,
                            onClick = {
                                if (recording.status == RecordingStatus.COMPLETED || recording.status == RecordingStatus.PARTIAL) vm.play(recording)
                                else menuFor = recording
                            },
                            onLongClick = { menuFor = recording },
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { recording ->
        RecordingMenu(
            recording = recording,
            vm = vm,
            onSaveCopy = {
                savingCopyOf = recording
                saveCopy.launch(recording.filePath?.substringAfterLast('/') ?: recording.title)
            },
            onDismiss = { menuFor = null },
        )
    }
}

/** Missed and failed are one chip: both mean "not going to be there". */
private enum class RecordingTab(val labelRes: Int) {
    ACTIVE(R.string.recording_group_now),
    SCHEDULED(R.string.recording_group_scheduled),
    COMPLETED(R.string.recording_group_completed),
    FAILED(R.string.recording_group_failed),
    ;

    fun holds(status: RecordingStatus): Boolean = when (this) {
        ACTIVE -> status == RecordingStatus.RECORDING
        SCHEDULED -> status == RecordingStatus.SCHEDULED
        COMPLETED -> status == RecordingStatus.COMPLETED || status == RecordingStatus.PARTIAL
        FAILED -> status == RecordingStatus.FAILED || status == RecordingStatus.MISSED
    }
}

@Composable
private fun StorageHeader(storage: RecordingStorageInfo, timersAreExact: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                start = MobileDimens.ScreenPaddingH,
                end = MobileDimens.ScreenPaddingH,
                top = MobileDimens.GapSmall,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.content_downloads_storage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.content_downloads_storage_free,
                    gigabytes(storage.freeBytes),
                    gigabytes(storage.totalBytes),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MobileDimens.GapSmall),
            )
        }
        val used = (storage.totalBytes - storage.freeBytes).coerceAtLeast(0L)
        val fraction = if (storage.totalBytes > 0) {
            (used.toFloat() / storage.totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MobileDimens.GapSmall, bottom = MobileDimens.GapSmall),
        )
        // Said on the screen the consequence shows up on, not buried in settings: this is where a
        // user wonders why a recording began at 20:03.
        if (!timersAreExact) {
            Text(
                text = stringResource(R.string.settings_recording_timers_inexact),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = MobileDimens.GapSmall),
            )
        }
    }
}

@Composable
private fun RecordingRow(recording: RecordingEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    MobileListRow(
        title = recording.title,
        // Three lines' worth in two: the channel it came from, then where it got to or why it did not.
        subtitle = listOf(recording.channelName, statusLine(recording)).joinToString("\n"),
        subtitleMaxLines = 2,
        leading = {
            Icon(
                imageVector = MobileIcons.LiveTv,
                contentDescription = null,
                tint = if (recording.status == RecordingStatus.RECORDING) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        trailing = {
            if (recording.status == RecordingStatus.COMPLETED || recording.status == RecordingStatus.PARTIAL) {
                Icon(
                    imageVector = MobileIcons.PlayArrow,
                    contentDescription = stringResource(R.string.content_downloads_play),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/** Where this recording got to, or why it never did. */
@Composable
private fun statusLine(recording: RecordingEntity): String {
    val context = LocalContext.current
    return when (recording.status) {
        RecordingStatus.RECORDING -> stringResource(R.string.recording_group_now)
        RecordingStatus.SCHEDULED -> stringResource(
            R.string.recording_scheduled_for,
            android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(recording.startMs)),
        )
        RecordingStatus.COMPLETED, RecordingStatus.PARTIAL -> pluralStringResource(
            R.plurals.recording_minutes,
            recordedMinutes(recording),
            recordedMinutes(recording),
        )
        RecordingStatus.FAILED, RecordingStatus.MISSED ->
            RecordingRules.displayTextOf(recording.failure, context.resources)
                ?: stringResource(R.string.recording_failed_unknown)
        RecordingStatus.CANCELLED -> ""
    }
}

@Composable
private fun RecordingMenu(
    recording: RecordingEntity,
    vm: RecordingsViewModel,
    onSaveCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss, title = recording.title) {
        when (recording.status) {
            RecordingStatus.COMPLETED, RecordingStatus.PARTIAL -> MenuRow(R.string.content_downloads_play, MobileIcons.PlayArrow) {
                vm.play(recording); onDismiss()
            }
            RecordingStatus.RECORDING -> MenuRow(R.string.recording_stop, MobileIcons.Pause) {
                vm.stop(recording); onDismiss()
            }
            RecordingStatus.SCHEDULED -> MenuRow(R.string.common_cancel, MobileIcons.Delete) {
                vm.cancel(recording); onDismiss()
            }
            RecordingStatus.FAILED, RecordingStatus.MISSED -> MenuRow(R.string.common_retry, MobileIcons.Refresh) {
                vm.retry(recording); onDismiss()
            }
            RecordingStatus.CANCELLED -> Unit
        }
        // Nothing to delete while it is only scheduled: there is no file yet, and Cancel is what
        // "do not do this" means.
        // Only a finished recording has a file worth copying anywhere.
        if (recording.status == RecordingStatus.COMPLETED || recording.status == RecordingStatus.PARTIAL) {
            MenuRow(R.string.settings_export, MobileIcons.Save) { onSaveCopy(); onDismiss() }
        }
        // Where the file actually is, spelled out in full and copied on a tap — the download menu's
        // row, on the tab that is the same screen's other half.
        recording.filePath?.let { stored ->
            val path = tv.own.owntv.core.storage.StorageAccess.folderLabel(stored) ?: stored
            HorizontalDivider()
            MobileListRow(
                title = stringResource(R.string.settings_backup_location),
                subtitle = path,
                subtitleMaxLines = 3,
                leading = {
                    Icon(
                        imageVector = MobileIcons.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                onClick = { vm.copyPath(path); onDismiss() },
            )
        }
        if (recording.status != RecordingStatus.SCHEDULED) {
            HorizontalDivider()
            MenuRow(R.string.common_delete, MobileIcons.Delete, destructive = true) {
                vm.delete(recording); onDismiss()
            }
        }
    }
}

@Composable
private fun MenuRow(
    labelRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    MobileListRow(
        title = stringResource(labelRes),
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        onClick = onClick,
    )
}

/** Minutes actually captured, from the clock where there is one and the programme's length otherwise. */
private fun recordedMinutes(recording: RecordingEntity): Int {
    val started = recording.startedAt
    val ended = recording.endedAt
    val span = if (started != null && ended != null && ended > started) {
        ended - started
    } else {
        recording.programmeStopMs - recording.programmeStartMs
    }
    return (span / 60_000L).toInt().coerceAtLeast(1)
}

private fun gigabytes(bytes: Long): String = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}.format(bytes.coerceAtLeast(0) / 1_073_741_824.0)
