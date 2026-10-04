package tv.own.owntv.mobile.dev

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.PosterCard
import tv.own.owntv.mobile.ui.components.SectionHeader
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.glassSurface
import tv.own.owntv.mobile.ui.theme.labelRes

/**
 * Every shared component on one scrolling screen, drawn with the real theme and the real stored
 * settings — the Plan 4 Phase 1 exit check.
 *
 * Its text is deliberately all core's own strings: this screen is thrown away when the shell
 * arrives, and a throwaway screen must not add a single literal to the i18n baseline.
 */
@Composable
fun ThemeGalleryScreen(modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    val scope = rememberCoroutineScope()
    val zoom by settings.uiZoomPercent.collectAsStateWithLifecycle(UiZoom.DEFAULT)
    val accent by settings.accent.collectAsStateWithLifecycle(AccentColor.TEAL)
    val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.DARK)

    var favourite by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var sheetOpen by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(0) }

    val themeModes = listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT)
    val categories = listOf(
        stringResource(R.string.common_nav_home),
        stringResource(R.string.common_nav_live_tv),
        stringResource(R.string.common_nav_movies),
        stringResource(R.string.common_nav_series),
        stringResource(R.string.common_nav_guide),
        stringResource(R.string.common_nav_downloads),
    )

    LazyColumn(modifier = modifier.fillMaxWidth()) {

        // --- UI Zoom: the exit check wants this screen at 100% and at 150% ---
        item {
            SectionHeader(
                title = stringResource(R.string.settings_ui_zoom),
                actionLabel = stringResource(R.string.common_reset),
                onAction = { scope.launch { settings.setUiZoomPercent(UiZoom.DEFAULT) } },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MobileDimens.ScreenPaddingH),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium),
            ) {
                MobileButton(
                    text = stringResource(R.string.common_stepper_minus),
                    style = MobileButtonStyle.SECONDARY,
                    onClick = { scope.launch { settings.setUiZoomPercent(zoom - UiZoom.STEP) } },
                )
                Text(
                    text = stringResource(R.string.common_percent, zoom),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MobileButton(
                    text = stringResource(R.string.common_stepper_plus),
                    style = MobileButtonStyle.SECONDARY,
                    onClick = { scope.launch { settings.setUiZoomPercent(zoom + UiZoom.STEP) } },
                )
            }
        }

        // --- Accent and theme: both stored in core, both applied live by MobileTheme ---
        item {
            SectionHeader(title = stringResource(R.string.settings_accent))
            FilterChipRow(
                labels = AccentColor.entries.map { stringResource(it.labelRes) },
                selectedIndex = AccentColor.entries.indexOf(accent),
                onSelect = { index ->
                    scope.launch { settings.setAccent(AccentColor.entries[index]) }
                },
            )
            SectionHeader(title = stringResource(R.string.settings_theme))
            FilterChipRow(
                labels = themeModes.map {
                    stringResource(
                        when (it) {
                            ThemeMode.SYSTEM -> R.string.settings_theme_system
                            ThemeMode.DARK -> R.string.settings_theme_dark
                            ThemeMode.LIGHT -> R.string.settings_theme_light
                        },
                    )
                },
                selectedIndex = themeModes.indexOf(themeMode),
                onSelect = { index -> scope.launch { settings.setThemeMode(themeModes[index]) } },
            )
        }

        // --- Chips, posters, rows, settings rows, a text field, a sheet ---
        item {
            SectionHeader(title = stringResource(R.string.common_search))
            FilterChipRow(
                labels = categories,
                selectedIndex = category,
                onSelect = { category = it },
            )
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = MobileDimens.ScreenPaddingH,
                ),
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
            ) {
                items(categories) { name ->
                    PosterCard(
                        title = name,
                        subtitle = stringResource(R.string.common_nav_movies),
                        progress = 0.4f,
                    )
                }
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.common_nav_live_tv))
            Column(
                Modifier
                    .padding(horizontal = MobileDimens.ScreenPaddingH)
                    .fillMaxWidth()
                    .glassSurface(GlassSurface.PANELS),
            ) {
                categories.take(3).forEach { name ->
                    MobileListRow(
                        title = name,
                        subtitle = stringResource(R.string.common_nav_guide),
                        leading = {
                            Icon(
                                imageVector = MobileIcons.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        onClick = { sheetOpen = true },
                        onLongClick = { sheetOpen = true },
                    )
                }
            }
        }

        item {
            SectionHeader(title = stringResource(R.string.common_nav_settings))
            SettingRow(
                title = stringResource(R.string.content_favorite),
                subtitle = stringResource(R.string.settings_theme_description),
                checked = favourite,
                onCheckedChange = { favourite = it },
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.settings_glass_effect),
                value = stringResource(if (favourite) R.string.common_on else R.string.common_off),
            )
            HorizontalDivider()
            SettingRow(
                title = stringResource(R.string.common_nav_downloads),
                showChevron = true,
                onClick = { sheetOpen = true },
            )
        }

        item {
            SectionHeader(title = stringResource(R.string.common_search))
            Column(
                modifier = Modifier.padding(
                    horizontal = MobileDimens.ScreenPaddingH,
                    vertical = MobileDimens.GapSmall,
                ),
                verticalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium),
            ) {
                MobileTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.common_search),
                    placeholder = stringResource(R.string.common_search_hint),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
                    MobileButton(
                        text = stringResource(R.string.common_save),
                        onClick = { sheetOpen = true },
                    )
                    MobileButton(
                        text = stringResource(R.string.common_reset),
                        style = MobileButtonStyle.SECONDARY,
                        onClick = { query = "" },
                    )
                    MobileButton(
                        text = stringResource(R.string.common_cancel),
                        style = MobileButtonStyle.TEXT,
                        onClick = { query = "" },
                    )
                }
            }
        }
    }

    if (sheetOpen) {
        MobileBottomSheet(
            onDismissRequest = { sheetOpen = false },
            title = stringResource(R.string.common_nav_live_tv),
        ) {
            MobileListRow(
                title = stringResource(R.string.content_favorite),
                onClick = { sheetOpen = false },
            )
            MobileListRow(
                title = stringResource(R.string.common_nav_downloads),
                onClick = { sheetOpen = false },
            )
            MobileListRow(
                title = stringResource(R.string.common_cancel),
                onClick = { sheetOpen = false },
            )
        }
    }
}
