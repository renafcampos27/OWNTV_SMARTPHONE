package tv.own.owntv.mobile.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.i18n.SupportedLocales
import tv.own.owntv.core.settings.StartupMode
import tv.own.owntv.mobile.BuildConfig
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.player.PlaybackErrorLog
import java.text.DateFormat
import java.util.Date

/** Language, fixed startup channel and the installed custom build version. */
@Composable
fun SettingsAppPage(
    onOpenLanguage: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
    localeStore: LocaleStore = koinInject(),
) {
    val tag = localeStore.currentTag.pref("")
    val mode = vm.startupMode.pref(StartupMode.HOME)
    val channel = vm.startupChannel.pref(null)


    val context = LocalContext.current
    var startupSheet by remember { mutableStateOf(false) }
    var channelSheet by remember { mutableStateOf(false) }
    val appIcon = vm.settings.appIcon.pref(tv.own.owntv.core.brand.AppIcon.DEFAULT)
    var showAppIcon by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showAppIcon) {
        tv.own.owntv.mobile.ui.components.AppIconSettingsDialog(
            chosen = appIcon,
            onPick = { vm.edit { setAppIcon(it) } },
            onDismiss = { showAppIcon = false },
        )
    }

    SettingsPage(modifier) {
        settingsSection(R.string.settings_app_group) {
            SettingRow(
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_description),
                value = localeLabel(tag),
                showChevron = true,
                onClick = onOpenLanguage,
            )

            SettingRow(
                title = stringResource(R.string.settings_app_icon),
                subtitle = stringResource(R.string.settings_app_icon_summary),
                value = stringResource(appIcon.label),
                onClick = { showAppIcon = true },
            )

            SettingRow(
                title = stringResource(R.string.settings_app_startup),
                subtitle = stringResource(R.string.settings_app_startup_description),
                value = if (mode == StartupMode.SPECIFIC_CHANNEL && channel != null) {
                    channel.name
                } else {
                    stringResource(mode.labelRes())
                },
                onClick = { startupSheet = true },
            )

            SettingRow(title = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME))

        }

    }


    if (startupSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_app_startup_dialog),
            choices = StartupMode.entries.filter { it != StartupMode.FAVORITES }.map {
                SettingsChoice(it, stringResource(it.labelRes()))
            },
            selected = mode,
            // Picking "Specific channel" is only half an answer — the channel itself is the setting.
            onSelect = { picked ->
                if (picked == StartupMode.SPECIFIC_CHANNEL) channelSheet = true else vm.setStartupMode(picked)
            },
            onDismiss = { startupSheet = false },
        )
    }

    if (channelSheet) {
        StartupChannelSheet(
            vm = vm,
            onPick = { picked -> vm.setStartupChannel(picked); channelSheet = false },
            onDismiss = { channelSheet = false },
        )
    }
}

/**
 * The version, the licence, where the source lives and how to reach the people who use it.
 *
 * A More page rather than a block inside Settings → App: it is a page of facts, and no part of it
 * is a preference. The content itself is untouched — only its door moved.
 */
@Composable
fun AboutPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    SettingsPage(modifier) {
        settingsSection(R.string.settings_about) {
            Column(
                Modifier.padding(
                    horizontal = MobileDimens.ScreenPaddingH,
                    vertical = MobileDimens.GapSmall,
                ),
            ) {
                // The logo in the colour the launcher shows, as on the mockup's About row.
                tv.own.owntv.mobile.ui.components.BrandLockup(
                    markSize = 40,
                    textSize = 26,
                    modifier = Modifier.padding(bottom = MobileDimens.GapSmall),
                )
                Text(
                    text = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_about_description_full_mobile),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = MobileDimens.GapSmall),
                )
                Text(
                    text = stringResource(R.string.settings_about_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_contributions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = MobileDimens.GapSmall),
                )
            }

            SettingRow(
                title = stringResource(R.string.settings_about),
                subtitle = GITHUB_REPO,
                onClick = { openLink(context, "https://$GITHUB_REPO") },
            )


        }
    }
}

/** A search box over the profile's live channels — a playlist is far too long to scroll blind. */
@Composable
private fun StartupChannelSheet(
    vm: SettingsViewModel,
    onPick: (ChannelEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ChannelEntity>>(emptyList()) }
    LaunchedEffect(query) { results = vm.searchChannels(query) }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_startup_specific_channel),
    ) {
        MobileTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.common_search),
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
        )
        results.forEach { channel ->
            MobileListRow(title = channel.name, onClick = { onPick(channel) })
        }
    }
}

/**
 * The crash and playback history, as its own page rather than a dialog: twenty-five entries with
 * four lines each do not fit in a phone-sized dialog, and this is the screen a user is asked to
 * screenshot when they report a problem.
 */
@Composable
fun SettingsErrorLogPage(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<PlaybackErrorLog.Entry>?>(null) }
    var reload by remember { mutableStateOf(0) }
    var exportPath by remember { mutableStateOf<String?>(null) }
    var exportFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        entries = withContext(Dispatchers.IO) { PlaybackErrorLog.read(context) }
    }

    val stamp = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    val list = entries

    SettingsPage(modifier) {
        settingsNote(R.string.settings_playback_error_description_full)
        when {
            list == null -> settingsNote(R.string.settings_loading)
            list.isEmpty() -> settingsNote(R.string.settings_no_playback_errors)
            else -> items(list.size, key = { list[it].atMs.toString() + it }) { index ->
                LogEntry(list[index], stamp.format(Date(list[index].atMs)))
            }
        }
        item(key = "log-actions") {
            Column(
                Modifier.padding(
                    horizontal = MobileDimens.ScreenPaddingH,
                    vertical = MobileDimens.GapSmall,
                ),
            ) {
                Row {
                    // Export stays available on an empty list: the live diagnostics ring goes into the
                    // file too, and a handoff can leave useful detail without logging an entry.
                    MobileButton(
                        text = stringResource(R.string.settings_export),
                        style = MobileButtonStyle.SECONDARY,
                        onClick = {
                            scope.launch {
                                val path = withContext(Dispatchers.IO) { PlaybackErrorLog.export(context) }
                                exportPath = path
                                exportFailed = path == null
                            }
                        },
                    )
                    if (!list.isNullOrEmpty()) {
                        MobileButton(
                            text = stringResource(R.string.settings_clear_log),
                            style = MobileButtonStyle.SECONDARY,
                            onClick = {
                                PlaybackErrorLog.clear(context)
                                exportPath = null
                                exportFailed = false
                                reload++
                            },
                            modifier = Modifier.padding(start = MobileDimens.GapSmall),
                        )
                    }
                }
                exportPath?.let {
                    Text(
                        text = stringResource(R.string.settings_backup_saved_to, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (exportFailed) {
                    Text(
                        text = stringResource(R.string.settings_backup_export_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogEntry(entry: PlaybackErrorLog.Entry, when_: String) {
    Column(
        Modifier.padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapSmall),
    ) {
        Text(
            text = stringResource(
                R.string.settings_playback_entry_with_kind,
                when_,
                stringResource(entry.kind.labelRes()),
                entry.engine.engineName(),
                stringResource(if (entry.live) R.string.settings_live else R.string.settings_vod),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (entry.kind == PlaybackErrorLog.Kind.ERROR) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        val reason = entry.reason?.let { stringResource(it.messageRes) } ?: entry.legacyReason
        reason?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        entry.spec?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.raw?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(R.string.settings_device_details, entry.model, entry.android),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val GITHUB_REPO = "github.com/ahXN00/OwnTV"
private const val TELEGRAM_LINK = "t.me/owntvplayer"

/** The group's address as a code to point a camera at, with the line that says what to do with it. */
@Composable
private fun TelegramQr() {
    val qr = remember { CompanionLink.renderQr("https://$TELEGRAM_LINK") } ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MobileDimens.ScreenPaddingH),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(8.dp),
        ) {
            Image(
                bitmap = qr.asImageBitmap(),
                contentDescription = stringResource(R.string.settings_telegram_qr),
                modifier = Modifier.size(160.dp),
            )
        }
        Text(
            text = stringResource(R.string.settings_telegram_scan),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = MobileDimens.GapSmall),
        )
    }
}

private fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun localeLabel(tag: String): String =
    if (tag.isBlank()) {
        stringResource(R.string.settings_language_device)
    } else {
        SupportedLocales.pickerRows.firstOrNull { it.languageTag == tag }?.endonym ?: tag
    }

private fun StartupMode.labelRes() = when (this) {
    StartupMode.HOME -> R.string.settings_startup_home
    StartupMode.LAST_CHANNEL -> R.string.settings_startup_last_channel
    StartupMode.FAVORITES -> R.string.settings_startup_favorites
    StartupMode.SPECIFIC_CHANNEL -> R.string.settings_startup_specific_channel
}

private fun PlaybackErrorLog.Kind.labelRes() = when (this) {
    PlaybackErrorLog.Kind.ERROR -> R.string.settings_playback_kind_error
    PlaybackErrorLog.Kind.EVENT -> R.string.settings_playback_kind_event
    PlaybackErrorLog.Kind.REPORT -> R.string.settings_playback_kind_report
}

@Composable
private fun String.engineName(): String = when (trim().lowercase()) {
    "mpv" -> stringResource(R.string.settings_player_mpv)
    "exoplayer", "exo" -> stringResource(R.string.settings_player_exoplayer)
    else -> this
}
