package tv.own.owntv.mobile.ui.screens.settings

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileSlider
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileGroup
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.nav.MobileDestination
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * The seven settings groups, in the order the TV app's root list puts them.
 *
 * Two of the original nine are gone: **Data**, which held Backup, Local sync, Clear history and the
 * download folder — a place, a place, an action and a download preference, none of them a setting —
 * and **Profile**, which was a manager rather than a preference. Both live behind More now.
 *
 * Each is a route of its own rather than an expanding block: a phone screen holds far less than a
 * television's, and a back gesture out of a page is cheaper than scrolling past eight collapsed
 * groups to reach the ninth. Quick is not here — it lives inline at the top of the root.
 */
/** A heading is a label for the block below it, not a title of its own — it stays quiet. */
private const val SECTION_LABEL_ALPHA = 0.75f

/** The heading stands a little further in than its rows, so it reads as sitting above them. */
private val SECTION_LABEL_INSET = 14.dp

enum class SettingsGroup(
    val route: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int,
    val icon: ImageVector,
) {
    SOURCES("settings/sources", R.string.settings_group_sources, R.string.settings_group_summary_sources, MobileIcons.PlaylistPlay),
    APPEARANCE("settings/appearance", R.string.settings_appearance_group, R.string.settings_group_summary_appearance, MobileIcons.Palette),
    LAYOUT("settings/layout", R.string.settings_group_layout, R.string.settings_group_summary_layout, MobileIcons.ViewList),
    CONTENT("settings/content", R.string.settings_group_content_metadata, R.string.settings_group_summary_content_metadata, MobileIcons.Image),
    PLAYBACK("settings/playback", R.string.settings_playback_group, R.string.settings_group_summary_playback, MobileIcons.PlayCircle),
    NETWORK("settings/network", R.string.settings_network_group, R.string.settings_group_summary_network, MobileIcons.Wifi),
    APP("settings/app", R.string.settings_app_group, R.string.settings_group_summary_app, MobileIcons.Info),
}

/** The group a settings route belongs to, so the shell's bar can name the page and offer back. */
fun settingsGroupOf(route: String?): SettingsGroup? =
    SettingsGroup.entries.firstOrNull { it.route == route }

/**
 * The bar's title for a settings page, or null when the route is not one — the root, the nine group
 * pages and every leaf under them. A non-null answer is also what tells the bar to offer back.
 *
 * Leaves are asked first: a leaf route begins with its group's route, so testing the group first
 * would title every leaf after the group it hangs off.
 */
@StringRes
fun settingsPageTitleRes(route: String?): Int? = when (route) {
    // The root is reached from More, not from the bar, so nothing else would name it and the bar
    // would keep showing whichever tab the user came from.
    MobileDestination.SETTINGS.route -> R.string.common_nav_settings
    else -> settingsLeafOf(route)?.titleRes ?: settingsGroupOf(route)?.titleRes
}

/** Collect a settings flow for the row that displays it. */
@Composable
fun <T> Flow<T>.pref(initial: T): T = collectAsStateWithLifecycle(initial).value

/** The scrolling body every group page shares. */
@Composable
fun SettingsPage(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(
            start = MobileDimens.PagePaddingH,
            end = MobileDimens.PagePaddingH,
            top = MobileDimens.ScreenPaddingV,
            bottom = MobileDimens.GapLarge,
        ),
        content = content,
    )
}

/**
 * A heading inside a group page — "Mobile", "Subtitles", "Guide".
 *
 * A small label standing above its rows, not a title with a rule across the page: the rows below it
 * are already separated from each other by their own hairlines, and a second full-width line only
 * chops the page into slabs. It is what makes a long settings page scannable at a glance.
 */
fun LazyListScope.settingsSection(@StringRes titleRes: Int) {
    item(key = "section-$titleRes") { SettingsSectionLabel(stringResource(titleRes)) }
}

/**
 * A heading and the run of rows under it, as one rounded block.
 *
 * The block is the group, so everything inside it is flat. Rows go in directly rather than through
 * `item {}`: a settings group is a handful of lines, and holding them in one lazy item is what lets
 * them share a single pane. A page whose rows are a long dynamic list keeps the heading-only form
 * above and stays lazy.
 */
fun LazyListScope.settingsSection(
    @StringRes titleRes: Int,
    rows: @Composable ColumnScope.() -> Unit,
) {
    settingsSection(titleRes)
    settingsGroup(key = "group-$titleRes", rows = rows)
}

/** A block of rows with no heading of its own — the head of a page, or a single stray group. */
fun LazyListScope.settingsGroup(key: String, rows: @Composable ColumnScope.() -> Unit) {
    item(key = key) { MobileGroup(content = rows) }
}

/** The heading itself, for the one page — the settings root — that is not built from [SettingsPage]. */
@Composable
fun SettingsSectionLabel(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.8.sp,
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SECTION_LABEL_ALPHA),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(
            start = SECTION_LABEL_INSET,
            end = SECTION_LABEL_INSET,
            top = MobileDimens.GapMedium,
            bottom = MobileDimens.GapTiny + 2.dp,
        ),
    )
}

/** The rows that open a group's screen-sized settings, at the head of its page. */
fun LazyListScope.settingsLeafRows(group: SettingsGroup, onOpen: (SettingsLeaf) -> Unit) {
    val leaves = leavesOf(group)
    if (leaves.isEmpty()) return
    settingsGroup(key = "leaves-${group.route}") {
        leaves.forEach { leaf ->
            SettingRow(
                title = stringResource(leaf.titleRes),
                subtitle = leaf.summaryRes?.let { stringResource(it) },
                leading = { Icon(leaf.icon, contentDescription = null) },
                showChevron = true,
                onClick = { onOpen(leaf) },
            )
        }
    }
}

/** Explanatory text under a heading, for the settings that need a sentence rather than a subtitle. */
fun LazyListScope.settingsNote(@StringRes textRes: Int) {
    item(key = "note-$textRes") {
        Text(
            text = stringResource(textRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = SECTION_LABEL_INSET,
                end = SECTION_LABEL_INSET,
                bottom = MobileDimens.GapSmall,
            ),
        )
    }
}

/**
 * A percentage a finger drags, rather than a value a remote steps through. The label shows the live
 * value while the thumb moves, so the user is never guessing what they are about to commit to.
 */
@Composable
fun SettingsSlider(
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueLabel: String = stringResource(R.string.common_percent, value),
    steps: Int = 0,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = MobileDimens.ListRowPaddingH,
                vertical = MobileDimens.GapSmall,
            ),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MobileSlider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
        )
    }
}

/** One option in a [SettingsChoiceSheet]. */
data class SettingsChoice<T>(val value: T, val label: String, val description: String? = null)

/**
 * The bottom sheet that replaces the TV app's centred picker dialogs — theme, accent, fonts, zoom
 * and every other "one of these" setting. Choosing dismisses it; there is no confirm button.
 *
 * [description] is the line the TV app's dialogs carry under their title, for the few pickers whose
 * consequence is not obvious from the options — the playlist selector, whose choice applies to every
 * screen and is remembered.
 *
 * [footer] is for the one setting whose answer is not always in the list: the accent, where a colour
 * picker sits under the presets. It belongs inside this sheet rather than on the page, because it
 * answers the same question the presets do.
 */
@Composable
fun <T> SettingsChoiceSheet(
    title: String,
    choices: List<SettingsChoice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = MobileDimens.ScreenPaddingH,
                    end = MobileDimens.ScreenPaddingH,
                    bottom = MobileDimens.GapSmall,
                ),
            )
        }
        choices.forEach { choice ->
            MobileListRow(
                title = choice.label,
                subtitle = choice.description,
                onClick = { onSelect(choice.value); onDismiss() },
                selected = choice.value == selected,
                trailing = if (choice.value == selected) {
                    {
                        Icon(
                            imageVector = MobileIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 0.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
        footer?.invoke()
    }
}
