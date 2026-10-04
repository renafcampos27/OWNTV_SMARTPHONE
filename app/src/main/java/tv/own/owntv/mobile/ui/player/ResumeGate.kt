package tv.own.owntv.mobile.ui.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * Below this there is nothing to come back to: a film abandoned after eight seconds is a film that
 * was started, not one that was watched. The television draws the line in the same place.
 */
private const val MIN_RESUME_MS = 10_000L

/**
 * Starts something that has a saved position, obeying the user's **Resume playback** setting.
 *
 * The setting has been on the phone since the settings screen was built and, until now, nothing read
 * it: every screen handed the saved position straight to the tuner, so the phone always resumed
 * silently and "Ask" and "Never" did nothing at all. The rule belongs in one place because six
 * different rows can start a film, and six copies of it is six chances to disagree.
 *
 * Call the returned lambda with the saved position and what to do with the position actually chosen.
 * On **Ask** it puts up the sheet and calls back when the user has answered; on **Always** and
 * **Never** it calls back immediately, so a caller never needs to know which mode is in force.
 */
@Composable
fun rememberResumeGate(
    settings: SettingsRepository = koinInject(),
): (positionMs: Long, play: (Long) -> Unit) -> Unit {
    // ASK is core's own default, so an unread preference behaves as the stored one will.
    val mode by settings.resumeMode.collectAsStateWithLifecycle(
        initialValue = SettingsRepository.ResumeMode.ASK,
    )
    var pending by remember { mutableStateOf<Pair<Long, (Long) -> Unit>?>(null) }

    pending?.let { (positionMs, play) ->
        ResumeSheet(
            positionMs = positionMs,
            onResume = { pending = null; play(positionMs) },
            onStartOver = { pending = null; play(0L) },
            onDismiss = { pending = null },
        )
    }

    return { positionMs, play ->
        when {
            positionMs < MIN_RESUME_MS -> play(0L)
            mode == SettingsRepository.ResumeMode.NEVER -> play(0L)
            mode == SettingsRepository.ResumeMode.AUTO -> play(positionMs)
            else -> pending = positionMs to play
        }
    }
}

/**
 * "Resume playback?" — the television's dialog, as a sheet, because that is where a thumb is.
 *
 * Dismissing plays nothing. Backing out of this question means the user did not mean to start
 * anything, and starting it from the beginning would be the one answer they did not give.
 */
@Composable
private fun ResumeSheet(
    positionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.common_resume_prompt),
    ) {
        Text(
            text = stringResource(R.string.common_resume_position, formatTimestamp(positionMs)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
        )
        MobileListRow(title = stringResource(R.string.common_resume), onClick = onResume)
        MobileListRow(title = stringResource(R.string.common_start_over), onClick = onStartOver)
    }
}
