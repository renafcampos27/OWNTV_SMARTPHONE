package tv.own.owntv.mobile.ui.screens.settings

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.metadata.MetadataConfig
import tv.own.owntv.core.metadata.MetadataMode
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.screens.settings.customize.ConfirmDialog
import tv.own.owntv.mobile.ui.theme.MobileDimens
import java.util.Date
import java.util.Locale

/**
 * Where the details on a film come from — the provider's own, TMDB, or both.
 *
 * The keys and server addresses are typed once and saved deliberately, rather than written on every
 * keystroke: a half-typed API key is a key that fails, and a failing key looks like a broken app.
 */
@Composable
fun SettingsMetadataPage(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val mode = vm.settings.metadataMode.pref(MetadataMode.PROVIDER_PLUS_TMDB)
    val language = vm.settings.metadataLanguage.pref("")
    val storedKey = vm.settings.tmdbApiKey.pref("")
    val storedUrl = vm.settings.metadataServerUrl.pref("")

    val context = LocalContext.current
    val tier by vm.metadataTier.collectAsStateWithLifecycle()
    val budget by vm.metadataBudgetStatus.collectAsStateWithLifecycle()
    val testState by vm.metadataTest.collectAsStateWithLifecycle()
    LaunchedEffect(tier) {
        if (tier == MetadataConfig.Tier.DEFAULT_WORKER) vm.refreshMetadataBudget()
    }

    var modeSheet by remember { mutableStateOf(false) }
    var languageSheet by remember { mutableStateOf(false) }
    var testTitle by rememberSaveable { mutableStateOf("") }
    var clearingAdvanced by remember { mutableStateOf(false) }

    // Seeded once from what is stored, then owned by the field until Save puts it back.
    var key by remember { mutableStateOf(storedKey) }
    var url by remember { mutableStateOf(storedUrl) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(storedKey, storedUrl) {
        if (!seeded) {
            key = storedKey; url = storedUrl
            seeded = true
        }
    }

    SettingsPage(modifier) {
        settingsGroup(key = "metadata-mode") {
            SettingRow(
                title = stringResource(R.string.settings_metadata_source),
                subtitle = stringResource(R.string.settings_metadata_source_description),
                value = stringResource(mode.labelRes()),
                onClick = { modeSheet = true },
            )
        }
        if (mode.enrich) {
            settingsGroup(key = "metadata-language") {
                SettingRow(
                    title = stringResource(R.string.settings_metadata_language),
                    subtitle = stringResource(R.string.settings_metadata_language_description),
                    value = metadataLanguageName(language),
                    onClick = { languageSheet = true },
                )
            }
            settingsNote(R.string.settings_metadata_server_description)
            settingsGroup(key = "metadata-fields") {
                Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH)) {
                    MobileTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = stringResource(R.string.settings_metadata_key_label),
                        isPassword = true,
                        imeAction = ImeAction.Next,
                    )
                    MobileTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = stringResource(R.string.settings_metadata_server_label),
                    )
                    MobileButton(
                        text = stringResource(R.string.common_save),
                        onClick = { vm.edit { setTmdbApiKey(key); setMetadataServerUrl(url) } },
                        modifier = Modifier.padding(vertical = MobileDimens.GapSmall),
                    )
                    // The only way back to the shared service once a key or a server has been
                    // entered — emptying the fields by hand is easy to get half-right.
                    if (storedKey.isNotBlank() || storedUrl.isNotBlank()) {
                        MobileButton(
                            text = stringResource(R.string.settings_metadata_clear_advanced_title),
                            onClick = { clearingAdvanced = true },
                            style = MobileButtonStyle.SECONDARY,
                            modifier = Modifier.padding(bottom = MobileDimens.GapSmall),
                        )
                    }
                }

                SettingRow(
                    title = stringResource(R.string.settings_metadata_active_source),
                    subtitle = when (tier) {
                        MetadataConfig.Tier.DEFAULT_WORKER ->
                            stringResource(R.string.settings_metadata_shared_worker_description)
                        MetadataConfig.Tier.OWN_KEY -> maskSecret(storedKey)
                        MetadataConfig.Tier.SELF_HOST -> storedUrl
                    },
                    value = stringResource(tier.labelRes()),
                )
            }

            // The allowance only exists on the shared service. An own key or a self-hosted server is
            // the user's own resource and is never metered, so showing a limit there would be a lie.
            if (tier == MetadataConfig.Tier.DEFAULT_WORKER) {
                budget?.let { b ->
                    val resetTime = DateFormat.getTimeFormat(context).format(Date(b.resetAtMs))
                    settingsSection(R.string.settings_metadata_allowance) {
                        AllowanceRow(R.string.settings_allowance_minute, b.remainingMinute, b.limitMinute)

                        AllowanceRow(R.string.settings_allowance_hour, b.remainingHour, b.limitHour)

                        AllowanceRow(
                            R.string.settings_allowance_day,
                            b.remainingDay,
                            b.limitDay,
                            stringResource(R.string.settings_allowance_refills, resetTime),
                        )
                    }
                    settingsNote(R.string.settings_metadata_fair_share)
                }
            }

            settingsSection(R.string.settings_metadata_test_connection) {
                val testing = testState is SettingsViewModel.MetadataTestState.Testing
                Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH)) {
                    MobileTextField(
                        value = testTitle,
                        onValueChange = { testTitle = it },
                        label = stringResource(R.string.settings_lookup_movie),
                        placeholder = stringResource(R.string.settings_metadata_test_title),
                    )
                    MobileButton(
                        text = stringResource(
                            if (testing) R.string.settings_looking_up else R.string.settings_test_lookup,
                        ),
                        onClick = { vm.testMetadataLookup(testTitle) },
                        enabled = !testing,
                        modifier = Modifier.padding(vertical = MobileDimens.GapSmall),
                    )
                    MetadataTestLabel(testState)
                }
            }
        }
    }

    if (clearingAdvanced) {
        ConfirmDialog(
            title = stringResource(R.string.settings_metadata_clear_advanced_title),
            message = stringResource(R.string.settings_metadata_clear_advanced_message),
            confirmLabel = stringResource(R.string.common_ok),
            onConfirm = {
                key = ""
                url = ""
                vm.edit { setTmdbApiKey(""); setMetadataServerUrl("") }
                vm.resetMetadataTest()
                clearingAdvanced = false
            },
            onDismiss = { clearingAdvanced = false },
        )
    }

    if (modeSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_metadata_source),
            choices = MetadataMode.entries.map {
                SettingsChoice(it, stringResource(it.labelRes()), stringResource(it.descriptionRes()))
            },
            selected = mode,
            onSelect = { picked -> vm.edit { setMetadataMode(picked) } },
            onDismiss = { modeSheet = false },
        )
    }

    if (languageSheet) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_metadata_language),
            choices = METADATA_LANGUAGE_CODES.map { SettingsChoice(it, metadataLanguageName(it)) },
            selected = language,
            onSelect = { code -> vm.edit { setMetadataLanguage(code) } },
            onDismiss = { languageSheet = false },
        )
    }
}

/**
 * The two-letter tags TMDB answers in. Blank leaves TMDB on its own default, which is what an
 * install that predates the setting has always had.
 */
private val METADATA_LANGUAGE_CODES = listOf(
    "", MetadataConfig.LANGUAGE_AUTO, "ar", "bg", "zh", "hr", "cs", "da", "nl", "en", "et", "fi",
    "fr", "de", "el", "he", "hi", "hu", "id", "it", "ja", "ko", "lv", "lt", "ms", "no", "fa", "pl",
    "pt-BR", "pt-PT", "ro", "ru", "sr", "sk", "sl", "es", "es-MX", "sv", "th", "tr", "uk", "vi",
)

/**
 * A language's name in the reader's own language, from Android rather than from a translated list —
 * the platform already knows that "de" is "Deutsch" to a German and "German" to an Englishman.
 */
@Composable
private fun metadataLanguageName(code: String): String = when (code) {
    "" -> stringResource(R.string.settings_language_default)
    MetadataConfig.LANGUAGE_AUTO -> stringResource(R.string.settings_language_device)
    else -> Locale.forLanguageTag(code)
        .getDisplayName(LocalConfiguration.current.locales[0])
}

/** One window of the shared allowance: how many lookups are left of how many. */
@Composable
private fun AllowanceRow(
    @StringRes labelRes: Int,
    remaining: Int,
    limit: Int,
    subtitle: String? = null,
) {
    SettingRow(
        title = stringResource(labelRes),
        subtitle = subtitle ?: stringResource(R.string.settings_metadata_reset_automatic),
        value = pluralStringResource(R.plurals.settings_allowance_value, remaining, remaining, limit),
    )
}

/** What the last test lookup answered — a match, or the reason there wasn't one. */
@Composable
private fun MetadataTestLabel(state: SettingsViewModel.MetadataTestState) {
    val text = when (state) {
        SettingsViewModel.MetadataTestState.Idle, SettingsViewModel.MetadataTestState.Testing -> return
        is SettingsViewModel.MetadataTestState.Ok -> stringResource(
            R.string.settings_metadata_match_result,
            state.title,
            state.year?.let { stringResource(R.string.settings_metadata_year, it) }.orEmpty(),
            state.tmdbId,
        )
        is SettingsViewModel.MetadataTestState.Fail -> when (val failure = state.failure) {
            SettingsViewModel.MetadataFailure.EmptyTitle ->
                stringResource(R.string.settings_metadata_empty_title)
            SettingsViewModel.MetadataFailure.ServerUnavailable ->
                stringResource(R.string.settings_metadata_server_unavailable)
            is SettingsViewModel.MetadataFailure.NoMatch ->
                stringResource(R.string.settings_metadata_no_match, failure.query)
            is SettingsViewModel.MetadataFailure.Unknown ->
                failure.rawMessage ?: stringResource(R.string.settings_metadata_lookup_failed)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state is SettingsViewModel.MetadataTestState.Ok) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.error
        },
        modifier = Modifier.padding(bottom = MobileDimens.GapSmall),
    )
}

private const val MASK_DOT = '•'

/** Enough of a key to tell two apart, useless to anyone reading it over a shoulder. */
private fun maskSecret(secret: String): String {
    val trimmed = secret.trim()
    if (trimmed.length <= 4) return MASK_DOT.toString().repeat(4)
    return MASK_DOT.toString().repeat(8) + trimmed.takeLast(4)
}

private fun MetadataConfig.Tier.labelRes() = when (this) {
    MetadataConfig.Tier.DEFAULT_WORKER -> R.string.settings_tier_default
    MetadataConfig.Tier.OWN_KEY -> R.string.settings_tier_key
    MetadataConfig.Tier.SELF_HOST -> R.string.settings_tier_self_host
}

private fun MetadataMode.labelRes() = when (this) {
    MetadataMode.PROVIDER -> R.string.settings_metadata_provider_only
    MetadataMode.PROVIDER_PLUS_TMDB -> R.string.settings_metadata_provider_plus_tmdb
    MetadataMode.TMDB_ONLY -> R.string.settings_metadata_tmdb_only
}

private fun MetadataMode.descriptionRes() = when (this) {
    MetadataMode.PROVIDER -> R.string.settings_metadata_provider_description
    MetadataMode.PROVIDER_PLUS_TMDB -> R.string.settings_metadata_provider_tmdb_description
    MetadataMode.TMDB_ONLY -> R.string.settings_metadata_tmdb_only_description
}
