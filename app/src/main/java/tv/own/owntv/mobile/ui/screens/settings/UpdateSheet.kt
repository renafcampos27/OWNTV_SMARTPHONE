package tv.own.owntv.mobile.ui.screens.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.update.UpdateManager
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * The in-app updater, as a bottom sheet.
 *
 * Every state, string and decision is the television's — the whole state machine lives in core's
 * [UpdateManager], which is already a Koin `single` there, so this file is the phone's shape for it
 * and nothing more. The television draws a centred dialog because a remote has nowhere better to
 * look; a phone gets a sheet, where a thumb already is.
 *
 * [checkOnOpen] makes opening the sheet start a fresh check, which is what the Settings row wants.
 * The startup check passes `false`, because by then the check has already run and the sheet only
 * opens at all if it found something.
 */
@Composable
fun UpdateSheet(onDismiss: () -> Unit, checkOnOpen: Boolean = false) {
    val manager: UpdateManager = koinInject()
    val state by manager.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (checkOnOpen) manager.check()
    }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.update_title),
    ) {
        Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH)) {
            when (val s = state) {
                UpdateManager.State.Idle, UpdateManager.State.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(MobileDimens.GapSmall))
                        Text(
                            text = stringResource(R.string.update_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                UpdateManager.State.UpToDate -> {
                    Text(
                        text = stringResource(R.string.update_latest, manager.currentVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(MobileDimens.GapMedium))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        MobileButton(
                            text = stringResource(R.string.settings_close),
                            onClick = onDismiss,
                        )
                    }
                }

                is UpdateManager.State.Available -> {
                    Text(
                        text = stringResource(
                            R.string.update_available_version,
                            s.info.version,
                            manager.currentVersion,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (s.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(MobileDimens.GapSmall))
                        Text(
                            text = stringResource(R.string.update_whats_new),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(MobileDimens.GapSmall))
                        Text(
                            text = renderReleaseNotes(s.info.notes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Capped and scrolled so Update / Later stay on screen, as on the
                            // television — a sheet that grows past its buttons is a sheet with no
                            // way to act on it.
                            modifier = Modifier
                                .heightIn(
                                    max = (LocalConfiguration.current.screenHeightDp.dp - 300.dp)
                                        .coerceIn(96.dp, 280.dp),
                                )
                                .verticalScroll(rememberScrollState()),
                        )
                    }
                    Spacer(Modifier.height(MobileDimens.GapMedium))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                    ) {
                        MobileButton(
                            text = stringResource(R.string.update_later),
                            onClick = onDismiss,
                            style = MobileButtonStyle.SECONDARY,
                        )
                        Spacer(Modifier.weight(1f))
                        MobileButton(
                            text = stringResource(R.string.update_now),
                            onClick = { manager.downloadAndInstall() },
                        )
                    }
                }

                is UpdateManager.State.Downloading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(MobileDimens.GapSmall))
                        Text(
                            text = stringResource(R.string.update_downloading, s.percent),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(MobileDimens.GapSmall))
                    Text(
                        text = stringResource(R.string.update_installer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is UpdateManager.State.Failed -> {
                    Text(
                        text = updateFailureText(s.failure),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(MobileDimens.GapMedium))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                    ) {
                        MobileButton(
                            text = stringResource(R.string.settings_close),
                            onClick = onDismiss,
                            style = MobileButtonStyle.SECONDARY,
                        )
                        Spacer(Modifier.weight(1f))
                        MobileButton(
                            text = stringResource(R.string.update_try_again),
                            onClick = { manager.retry() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(MobileDimens.GapMedium))
        }
    }
}

/** Each failure in the words core already has for it, so the phone and the television read alike. */
@Composable
private fun updateFailureText(failure: UpdateManager.Failure): String = when (failure) {
    is UpdateManager.Failure.CheckHttp ->
        stringResource(R.string.update_failed_check_http, failure.code.toString())
    UpdateManager.Failure.CheckNetwork -> stringResource(R.string.update_failed_check)
    UpdateManager.Failure.NoCompatibleApk -> stringResource(R.string.update_no_compatible_apk)
    UpdateManager.Failure.InvalidReleaseResponse ->
        stringResource(R.string.update_invalid_release_response)
    is UpdateManager.Failure.DownloadHttp ->
        stringResource(R.string.update_failed_download_http, failure.code.toString())
    UpdateManager.Failure.EmptyDownload -> stringResource(R.string.update_empty_download)
    UpdateManager.Failure.DownloadNetwork -> stringResource(R.string.update_failed_download)
    UpdateManager.Failure.RecordingsBusy -> stringResource(R.string.update_install_failed)
    UpdateManager.Failure.Install -> stringResource(R.string.update_install_failed)
    is UpdateManager.Failure.NotEnoughSpace -> stringResource(
        R.string.update_not_enough_space,
        Formatter.formatShortFileSize(LocalContext.current, failure.requiredBytes),
    )
    UpdateManager.Failure.DamagedDownload -> stringResource(R.string.update_damaged_download)
    // The installer's own wording names the real cause when it gives one; fall back when it doesn't.
    is UpdateManager.Failure.InstallRejected -> failure.message
        ?.let { stringResource(R.string.update_install_rejected, it) }
        ?: stringResource(R.string.update_install_failed)
}

/**
 * The release notes, which come from `CHANGELOG_APP.md` through the GitHub release body. Enough
 * Markdown for that file's bullets-only format — `### ` and `## ` headings become bold lines, `- `
 * and `* ` become bullets, `**bold**` renders bold — and nothing else. Not a Markdown parser.
 */
private fun renderReleaseNotes(notes: String): AnnotatedString = buildAnnotatedString {
    notes.replace("\r\n", "\n").trim().split("\n").forEachIndexed { index, raw ->
        if (index > 0) append("\n")
        val line = raw.trimEnd()
        when {
            line.startsWith("### ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInline(line.removePrefix("### ").trim())
            }
            line.startsWith("## ") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendInline(line.removePrefix("## ").trim())
            }
            line.startsWith("- ") -> { append("•  "); appendInline(line.removePrefix("- ")) }
            line.startsWith("* ") -> { append("•  "); appendInline(line.removePrefix("* ")) }
            else -> appendInline(line)
        }
    }
}

/** Appends [text], turning `**bold**` spans into real bold runs and leaving everything else alone. */
private fun AnnotatedString.Builder.appendInline(text: String) {
    var i = 0
    while (i < text.length) {
        val start = text.indexOf("**", i)
        if (start < 0) { append(text.substring(i)); break }
        val end = text.indexOf("**", start + 2)
        if (end < 0) { append(text.substring(i)); break }
        append(text.substring(i, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(start + 2, end)) }
        i = end + 2
    }
}
