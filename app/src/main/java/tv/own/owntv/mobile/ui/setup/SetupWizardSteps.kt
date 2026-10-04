package tv.own.owntv.mobile.ui.setup

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.mobile.ui.components.AppIconPicker
import tv.own.owntv.mobile.ui.components.BrandLockup
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.i18n.SupportedLocales
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.screens.settings.SettingsSlider
import tv.own.owntv.mobile.ui.screens.settings.stepsFor
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.glassDialogWindow

/**
 * The first-run wizard's own steps, in the television's order and with its words.
 *
 * The television's sequence is Welcome → Disclaimer → Setup choice → Create profile → Add content →
 * (a new playlist, an existing one, or a backup) → Importing, and this is the same sequence read down
 * a phone instead of across a ten-foot screen: the TV's side-by-side `ChoiceCard`s become rows,
 * because two cards side by side on a 360dp screen are two cards nobody can read.
 *
 * Two of the television's steps are deliberately absent, and their absence is the point rather than
 * an omission: **"Add source chooser"** and **"Import backup chooser"** exist only to offer *type it
 * on your phone* beside *type it here*, and the first of those has no meaning when the phone **is**
 * the device. Each chooser would be one card wide, so the flow goes straight to the form.
 */

/** Step 1 — the app's name, what it is, and the language everything after this is written in. */
@Composable
fun WelcomeStep(onNext: () -> Unit) {
    SetupPage {
        Text(
            text = stringResource(R.string.setup_welcome_to),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        // The stacked logo, as on the mockup's phone Welcome screen, in the colour the launcher shows.
        BrandLockup(markSize = 64, textSize = 26, stacked = true)
        Text(
            text = stringResource(R.string.setup_welcome_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MobileDimens.GapMedium))
        // The television offers the language here too, and it must be here rather than in Settings:
        // every screen after this one is text, and a user who cannot read the disclaimer cannot agree
        // to it. Setting it restarts the activity, which is why nothing is lost by it being first.
        FirstRunLanguageRow()
        Spacer(Modifier.height(MobileDimens.GapSmall))
        MobileButton(text = stringResource(R.string.setup_get_started), onClick = onNext)
    }
}

/**
 * Step 2 — how big everything is, before the first screen that is mostly words (#179).
 *
 * The two settings are the ones the Appearance and Fonts pages edit, written straight through, so a
 * choice made here is the app's from now on. `MobileTheme` scales every dp and sp from these same
 * values, which is what makes this screen resize under the finger as the slider moves — the sample
 * line below the sliders is the point of the step, not decoration.
 *
 * Zoom offers its whole range, the same one the Appearance page offers. Below
 * [UiZoom.LOW_RAM_WARN] a screen holds enough extra items to exhaust a small-memory device, so
 * crossing that line raises the same accept-the-risk prompt Settings raises — asked once, on the
 * drag that crosses it, and not again for the rest of the visit.
 */
@Composable
fun DisplaySizeStep(onNext: () -> Unit, onBack: () -> Unit) {
    val settings: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val zoom by settings.uiZoomPercent.collectAsStateWithLifecycle(UiZoom.DEFAULT)
    val fonts by settings.fontCustomization.collectAsStateWithLifecycle(FontCustomization())
    val appIcon by settings.appIcon.collectAsStateWithLifecycle(AppIcon.DEFAULT)
    var pendingLowZoom by remember { mutableStateOf<Int?>(null) }
    var lowZoomAccepted by remember { mutableStateOf(zoom < UiZoom.LOW_RAM_WARN) }
    SetupPage {
        Text(
            text = stringResource(R.string.setup_display_size_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.setup_display_size_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MobileDimens.GapSmall))
        // Zoom first: it scales the font row below it too, so it is the coarse control and often
        // the only one that needs touching.
        SettingsSlider(
            title = stringResource(R.string.settings_ui_zoom),
            value = zoom,
            range = UiZoom.MIN..UiZoom.MAX,
            steps = stepsFor(UiZoom.MIN, UiZoom.MAX, UiZoom.STEP),
            onValueChange = { raw ->
                val pct = UiZoom.clamp(raw)
                if (pct < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) {
                    pendingLowZoom = pct
                } else {
                    scope.launch { settings.setUiZoomPercent(pct) }
                }
            },
        )
        SettingsSlider(
            title = stringResource(R.string.settings_font_size),
            value = fonts.sizePercent,
            range = UiFontScale.MIN..UiFontScale.MAX,
            steps = stepsFor(UiFontScale.MIN, UiFontScale.MAX, UiFontScale.STEP),
            onValueChange = { raw ->
                scope.launch {
                    settings.setFontCustomization(fonts.copy(sizePercent = UiFontScale.clamp(raw)))
                }
            },
        )
        // No restart prompt here: the pick applies as soon as the app is next in the background.
        Text(
            text = stringResource(R.string.settings_app_icon),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        AppIconPicker(selected = appIcon, onPick = { scope.launch { settings.setAppIcon(it) } })
        Spacer(Modifier.height(MobileDimens.GapSmall))
        Text(
            text = stringResource(R.string.setup_display_size_preview),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(MobileDimens.GapSmall))
        MobileButton(text = stringResource(R.string.setup_continue), onClick = onNext)
        MobileButton(
            text = stringResource(R.string.settings_reset),
            onClick = {
                scope.launch {
                    settings.setUiZoomPercent(UiZoom.DEFAULT)
                    settings.setFontCustomization(fonts.copy(sizePercent = UiFontScale.DEFAULT))
                }
            },
            style = MobileButtonStyle.TEXT,
        )
        MobileButton(
            text = stringResource(R.string.common_back),
            onClick = onBack,
            style = MobileButtonStyle.TEXT,
        )
    }

    // The same prompt the Appearance page raises, for the same reason (#51): below the warning
    // point a small-memory device can run out of memory. Cancel leaves the zoom untouched.
    pendingLowZoom?.let { target ->
        AlertDialog(
            modifier = Modifier.glassDialogWindow(),
            onDismissRequest = { pendingLowZoom = null },
            title = { Text(stringResource(R.string.settings_low_zoom_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_low_zoom_warning_mobile,
                        UiZoom.LOW_RAM_WARN,
                        UiZoom.LOW_RAM_WARN,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        lowZoomAccepted = true
                        pendingLowZoom = null
                        scope.launch { settings.setUiZoomPercent(target) }
                    },
                ) { Text(stringResource(R.string.settings_low_zoom_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingLowZoom = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** Step 3 — what the app is not: it ships no channels, and the user brings their own sources. */
@Composable
fun DisclaimerStep(onAgree: () -> Unit, onBack: () -> Unit) {
    SetupPage {
        Text(
            text = stringResource(R.string.setup_before_you_start),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.setup_disclaimer),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MobileDimens.GapSmall))
        MobileButton(text = stringResource(R.string.setup_i_understand), onClick = onAgree)
        MobileButton(
            text = stringResource(R.string.common_back),
            onClick = onBack,
            style = MobileButtonStyle.TEXT,
        )
    }
}

/**
 * Step 5 — where the content comes from.
 *
 * [hasExisting] is false on a genuinely new install, and the row is then not drawn at all: offering
 * to reuse another profile's playlists when there are no other profiles is an empty promise.
 */
@Composable
fun AddContentStep(
    hasExisting: Boolean,
    onNew: () -> Unit,
    onExisting: () -> Unit,
    onImport: () -> Unit,
    onSkip: () -> Unit,
    onBack: (() -> Unit)?,
) {
    SetupPage {
        Text(
            text = stringResource(R.string.setup_add_playlist),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.setup_add_playlist_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MobileDimens.GapSmall))
        MobileListRow(
            title = stringResource(R.string.setup_new),
            subtitle = stringResource(R.string.setup_add_m3u_xtream),
            leading = { Icon(MobileIcons.PlaylistAdd, contentDescription = null) },
            onClick = onNew,
        )
        if (hasExisting) {
            MobileListRow(
                title = stringResource(R.string.setup_existing),
                subtitle = stringResource(R.string.setup_use_other_profile_playlists),
                leading = { Icon(MobileIcons.PlaylistPlay, contentDescription = null) },
                onClick = onExisting,
            )
        }
        MobileListRow(
            title = stringResource(R.string.setup_import),
            subtitle = stringResource(R.string.setup_restore_backup_file),
            leading = { Icon(MobileIcons.Restore, contentDescription = null) },
            onClick = onImport,
        )
        MobileButton(
            text = stringResource(R.string.setup_skip_for_now),
            onClick = onSkip,
            style = MobileButtonStyle.TEXT,
        )
        if (onBack != null) {
            MobileButton(
                text = stringResource(R.string.common_back),
                onClick = onBack,
                style = MobileButtonStyle.TEXT,
            )
        }
    }
}

/** Step 5a — playlists another profile already has, shared rather than downloaded a second time. */
@Composable
fun ExistingSourcesStep(
    sources: List<SourceEntity>,
    onAdd: (Set<Long>) -> Unit,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf(setOf<Long>()) }
    SetupPage {
        Text(
            text = stringResource(R.string.setup_use_existing_playlists),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.setup_pick_playlists),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MobileDimens.GapSmall))
        // Bounded, or a user with twenty shared playlists cannot reach the button under the list.
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = ExistingListMaxHeight)) {
            items(sources, key = { it.id }) { source ->
                val checked = source.id in selected
                MobileListRow(
                    title = source.name,
                    subtitle = source.url,
                    leading = {
                        Icon(
                            imageVector = if (checked) MobileIcons.Check else MobileIcons.PlaylistPlay,
                            contentDescription = null,
                            tint = if (checked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    onClick = {
                        selected = if (checked) selected - source.id else selected + source.id
                    },
                )
            }
        }
        Spacer(Modifier.height(MobileDimens.GapSmall))
        MobileButton(
            text = pluralStringResource(
                R.plurals.setup_add_selected_playlists,
                selected.size,
                selected.size,
            ),
            onClick = { onAdd(selected) },
            enabled = selected.isNotEmpty(),
        )
        MobileButton(
            text = stringResource(R.string.common_back),
            onClick = onBack,
            style = MobileButtonStyle.TEXT,
        )
    }
}

/**
 * The language, on the very first screen.
 *
 * A row that opens the list rather than the Settings page's searchable wall: there are twenty-six of
 * them, but a first-run user is picking their own, which they will recognise on sight.
 */
@Composable
private fun FirstRunLanguageRow(localeStore: LocaleStore = koinInject()) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val current by localeStore.currentTag.collectAsStateWithLifecycle()
    val systemLabel = stringResource(R.string.settings_language_system_default)
    val rows = remember { SupportedLocales.pickerRows.sortedBy { it.englishName } }
    val label = rows.firstOrNull { it.languageTag == current }?.endonym ?: systemLabel

    MobileListRow(
        title = stringResource(R.string.settings_language),
        subtitle = label,
        leading = { Icon(MobileIcons.Translate, contentDescription = null) },
        onClick = { open = true },
    )
    if (open) {
        MobileBottomSheet(
            onDismissRequest = { open = false },
            title = stringResource(R.string.settings_language),
        ) {
            LazyColumn(Modifier.heightIn(max = LanguageSheetMaxHeight)) {
                item(key = "system") {
                    MobileListRow(
                        title = systemLabel,
                        onClick = {
                            scope.launch { runCatching { localeStore.set("") } }
                            open = false
                        },
                    )
                }
                items(rows, key = { it.languageTag }) { row ->
                    MobileListRow(
                        title = row.endonym,
                        subtitle = row.englishName,
                        onClick = {
                            scope.launch { runCatching { localeStore.set(row.languageTag) } }
                            open = false
                        },
                    )
                }
            }
        }
    }
}

private val ExistingListMaxHeight = 320.dp
private val LanguageSheetMaxHeight = 420.dp
