package tv.own.owntv.mobile.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.live.CatchupJumps
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.theme.MobileDimens
import java.text.SimpleDateFormat
import java.util.TimeZone

/** What the player knows about the archive behind the channel playing, for the "Go back to…" sheet. */
data class CatchupOptions(
    /** Offsets worth offering, nearest first — [CatchupJumps.optionsFor] already bounded them. */
    val offsetsSec: List<Int>,
    /** How deep the provider's archive goes, so a typed time can be clamped to it. */
    val windowSec: Int,
    val onPick: (Int) -> Unit,
)

/**
 * "Go back to…" — start a catch-up channel at an earlier point.
 *
 * Rows are wall-clock times, not "3 hours ago": the user is looking for the news that aired at 19:00,
 * and a clock spares them the arithmetic. A row landing on an earlier day carries its weekday,
 * because "19:00" alone cannot tell today from yesterday. The round offsets are a shortcut, so the
 * last row leaves them behind for a day and a time typed exactly.
 */
@Composable
fun CatchupSheet(options: CatchupOptions, onDismiss: () -> Unit) {
    var exact by remember { mutableStateOf(false) }
    if (exact) {
        ExactTimeSheet(
            windowSec = options.windowSec,
            onPick = { exact = false; options.onPick(it); onDismiss() },
            onDismiss = { exact = false },
        )
        return
    }
    // One "now" for the whole list: recomputing per row would let the clock tick between rows and
    // print two different times for the same offset.
    val nowMs = remember { System.currentTimeMillis() }
    val zone = remember { TimeZone.getDefault() }
    val timeOnly = rememberTimeFormat("Hm")
    val withDay = rememberTimeFormat("EEEHm")

    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.content_catchup_jump)) {
        Text(
            text = stringResource(R.string.content_catchup_jump_prompt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
        )
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
            items(options.offsetsSec, key = { it }) { offset ->
                val at = CatchupJumps.instantFor(offset, nowMs)
                val format = if (CatchupJumps.crossesDay(offset, nowMs, zone)) withDay else timeOnly
                MobileListRow(
                    title = format.format(at),
                    onClick = { options.onPick(offset); onDismiss() },
                )
            }
            item(key = "exact") {
                MobileListRow(
                    title = stringResource(R.string.content_catchup_jump_exact),
                    onClick = { exact = true },
                )
            }
        }
    }
}

/**
 * The day and the time, typed. Every change is pushed through [CatchupJumps.clampToArchive], so the
 * picker simply stops at the live edge and at the far end of the recording rather than letting the
 * user choose a moment that can only fail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExactTimeSheet(windowSec: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    // Frozen for the life of the sheet: a moving "now" would shift the clamp under the user's fingers.
    val nowMs = remember { System.currentTimeMillis() }
    val zone = remember { TimeZone.getDefault() }
    val dayFormat = rememberTimeFormat("EEEMMMd")
    var point by remember {
        mutableStateOf(
            CatchupJumps.clampToArchive(
                CatchupJumps.pointAt(nowMs - 3600_000L, nowMs, zone), nowMs, zone, windowSec,
            ),
        )
    }
    val days = remember(windowSec) { CatchupJumps.selectableDays(windowSec) }
    val time = rememberTimePickerState(initialHour = point.hour, initialMinute = point.minute)
    // The wheel reports every touch; the clamp is what decides where it actually lands.
    LaunchedEffect(time.hour, time.minute) {
        point = CatchupJumps.clampToArchive(
            CatchupJumps.Point(point.daysAgo, time.hour, time.minute), nowMs, zone, windowSec,
        )
    }

    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.content_catchup_jump_exact)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            (0 until days).forEach { daysAgo ->
                FilterChip(
                    selected = daysAgo == point.daysAgo,
                    onClick = {
                        point = CatchupJumps.clampToArchive(
                            point.copy(daysAgo = daysAgo), nowMs, zone, windowSec,
                        )
                    },
                    label = { Text(dayFormat.format(nowMs - daysAgo * 24L * 3600 * 1000)) },
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(MobileDimens.ScreenPaddingH),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TimePicker(state = time)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = MobileDimens.ScreenPaddingH),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            TextButton(
                onClick = { onPick(CatchupJumps.offsetSecOf(point, nowMs, zone)) },
            ) { Text(stringResource(R.string.content_play)) }
        }
    }
}

@Composable
private fun rememberTimeFormat(skeleton: String): SimpleDateFormat {
    // The configuration's list, never Locale.getDefault(): the latter is not observable, so a phone
    // switched to another language mid-session would keep printing yesterday's clock.
    val locale = LocalConfiguration.current.locales[0]
    return remember(skeleton, locale) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
    }
}
