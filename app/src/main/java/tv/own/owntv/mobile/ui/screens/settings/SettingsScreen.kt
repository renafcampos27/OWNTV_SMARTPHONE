package tv.own.owntv.mobile.ui.screens.settings

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.components.TwoPane
import tv.own.owntv.mobile.ui.nav.isExpandedWidth
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * The settings root: a field to search all of it, the switches the user pinned, and the seven groups.
 *
 * The TV app puts every setting behind a two-column browser on one enormous screen. A phone gets a
 * plain list instead — seven rows, each opening a page — because a thumb scrolls faster than it aims,
 * and because search makes the depth cheap: typing "wifi" reaches a row four taps down in one.
 */
@Composable
fun SettingsScreen(
    onOpenRoute: (String) -> Unit,
    onAddSource: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    // Held here rather than in the list, so unfolding a device mid-search keeps what was typed:
    // the two layouts are different call sites, and state inside one of them does not reach the other.
    var query by rememberSaveable { mutableStateOf("") }
    if (isExpandedWidth()) {
        SettingsTwoPane(
            query = query,
            onQuery = { query = it },
            onAddSource = onAddSource,
            modifier = modifier,
            vm = vm,
        )
    } else {
        SettingsList(
            query = query,
            onQuery = { query = it },
            onOpenRoute = onOpenRoute,
            modifier = modifier,
            vm = vm,
        )
    }
}

/**
 * The group list beside the group it opens.
 *
 * The right side keeps a stack of its own rather than using navigation's, because the list must
 * stay on screen: pushing a leaf onto the app's back stack would replace the whole page, and the
 * left column would disappear at exactly the width that has room for it. Back pops that stack while
 * it is deeper than one, which is the same gesture doing the same thing one pane in.
 */
@Composable
private fun SettingsTwoPane(
    query: String,
    onQuery: (String) -> Unit,
    onAddSource: () -> Unit,
    modifier: Modifier,
    vm: SettingsViewModel,
) {
    // Never empty: a pane with nothing in it is half a screen of wasted tablet, so the first group
    // is open from the start. It is Sources, which is what the list itself begins with.
    val stack = rememberSaveable(saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        mutableStateListOf(SettingsGroup.entries.first().route)
    }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    TwoPane(
        list = {
            SettingsList(
                query = query,
                onQuery = onQuery,
                onOpenRoute = { route ->
                    // Choosing a group starts a fresh stack; a leaf reached from inside one is pushed.
                    if (SettingsGroup.entries.any { it.route == route }) {
                        stack.clear()
                        stack.add(route)
                    } else {
                        stack.add(route)
                    }
                },
                selectedRoute = stack.first(),
                vm = vm,
            )
        },
        detail = {
            SettingsRoutePage(
                route = stack.last(),
                onOpenRoute = { stack.add(it) },
                onAddSource = onAddSource,
            )
        },
        modifier = modifier,
    )
}

/** The list itself: search, the pinned switches, the seven groups. */
@Composable
private fun SettingsList(
    query: String,
    onQuery: (String) -> Unit,
    onOpenRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedRoute: String? = null,
    vm: SettingsViewModel = koinViewModel(),
) {
    val entries = rememberSettingsSearchEntries()
    val results = entries.matching(query)
    SettingsPage(modifier) {
        item(key = "search") {
            val settingsKeyboard = LocalSoftwareKeyboardController.current
            val settingsFocus = LocalFocusManager.current
            val settingsBiv = remember { BringIntoViewRequester() }
            val settingsScope = rememberCoroutineScope()
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.settings_search_hint)) },
                leadingIcon = { Icon(MobileIcons.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        settingsKeyboard?.hide()
                        settingsFocus.clearFocus()
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(settingsBiv)
                    .onFocusEvent { if (it.isFocused) settingsScope.launch { runCatching { settingsBiv.bringIntoView() } } }
                    .padding(
                        horizontal = MobileDimens.ScreenPaddingH,
                        vertical = MobileDimens.GapSmall,
                    ),
            )
        }

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                item(key = "no-results") {
                    Text(
                        // The settings' own "nothing matched", not the catalogue's: this list is
                        // rows, not titles, and the television answers an unmatched query the same way.
                        text = stringResource(R.string.settings_no_settings_match, query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MobileDimens.ScreenPaddingH,
                            vertical = MobileDimens.GapMedium,
                        ),
                    )
                }
            } else {
                item(key = "results-header") {
                    SettingsSectionLabel(stringResource(R.string.settings_results_title))
                }
                settingsNote(R.string.settings_results_summary)
                settingsGroup(key = "results") {
                    results.forEach { entry ->
                        MobileListRow(
                            title = entry.title,
                            subtitle = settingsBreadcrumb(entry),
                            leading = { Icon(entry.group.icon, contentDescription = null) },
                            onClick = { onOpenRoute(entry.route) },
                        )
                    }
                }
            }
            return@SettingsPage
        }



        settingsGroup(key = "groups") {
            SettingsGroup.entries.forEach { group ->
                SettingRow(
                    title = stringResource(group.titleRes),
                    subtitle = stringResource(group.summaryRes),
                    leading = { Icon(group.icon, contentDescription = null) },
                    // Beside its page, a chevron would promise to go somewhere the list is not
                    // leaving; the tinted row is what says which group is open instead.
                    showChevron = selectedRoute == null,
                    onClick = { onOpenRoute(group.route) },
                    modifier = if (group.route == selectedRoute) {
                        Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}
