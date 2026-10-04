package tv.own.owntv.mobile.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.core.companion.CompanionLink
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.i18n.SupportedLocale
import tv.own.owntv.core.i18n.SupportedLocales
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens
import java.util.Locale

/**
 * Every language the app is packaged in, each in its own name, with how much of it is translated.
 *
 * A search box rather than the television's two-column wall: twenty-six languages do not fit on a
 * phone, and typing "por" is faster than scrolling to Portuguese. The coverage figure is shown
 * because an eighty-percent language still has English in it, and a user who knows that in advance
 * is not surprised by it afterwards.
 */
@Composable
fun SettingsLanguagePage(
    modifier: Modifier = Modifier,
    localeStore: LocaleStore = koinInject(),
) {
    val current = localeStore.currentTag.pref("")
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var helpSheet by remember { mutableStateOf(false) }

    val rows = remember { SupportedLocales.pickerRows.sortedBy { it.englishName.lowercase(Locale.ROOT) } }
    val needle = query.trim()
    val matches = remember(needle) { rows.filter { it.matches(needle) } }
    val systemLabel = stringResource(R.string.settings_language_system_default)
    val systemDescription = stringResource(R.string.settings_language_system_default_description)
    val showSystem = needle.isEmpty() ||
        systemLabel.contains(needle, true) || systemDescription.contains(needle, true)

    fun choose(tag: String) = scope.launch { runCatching { localeStore.set(tag) } }

    SettingsPage(modifier) {
        item(key = "search") {
            MobileTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.settings_language_search_hint),
                modifier = Modifier.padding(
                    horizontal = MobileDimens.ListRowPaddingH,
                    vertical = MobileDimens.GapSmall,
                ),
            )
        }

        settingsGroup(key = "help") {
            SettingRow(
                title = stringResource(R.string.settings_language_help_translate),
                subtitle = stringResource(R.string.settings_language_contribution_description),
                leading = { Icon(MobileIcons.Translate, contentDescription = null) },
                showChevron = true,
                onClick = { helpSheet = true },
            )
        }

        settingsGroup(key = "languages") {
            if (showSystem) {
                LanguageRow(
                    title = systemLabel,
                    subtitle = systemDescription,
                    coverage = null,
                    selected = current.isEmpty(),
                    onClick = { choose(SupportedLocales.SYSTEM_DEFAULT_TAG) },
                )
            }
            matches.forEach { locale ->
                LanguageRow(
                    title = locale.endonym,
                    subtitle = locale.englishName,
                    coverage = SupportedLocales.coverageBadgePercent(locale),
                    selected = current == locale.languageTag,
                    onClick = { choose(locale.languageTag) },
                )
            }
            if (!showSystem && matches.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_settings_match, needle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MobileDimens.ListRowPaddingH),
                )
            }
        }
    }

    if (helpSheet) {
        TranslationContributionSheet(onDismiss = { helpSheet = false })
    }
}

/** Endonym, English name or tag — whichever the user happens to think of the language by. */
private fun SupportedLocale.matches(needle: String): Boolean =
    needle.isEmpty() ||
        endonym.contains(needle, true) ||
        englishName.contains(needle, true) ||
        languageTag.contains(needle, true)

@Composable
private fun LanguageRow(
    title: String,
    subtitle: String,
    coverage: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    MobileListRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        selected = selected,
        trailing = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (coverage != null) {
                    Text(
                        text = stringResource(R.string.settings_language_coverage, coverage),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = MobileDimens.GapSmall, vertical = 2.dp),
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = MobileIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    )
}

/**
 * The sheet behind "Help translate": the project's QR code, the two links, and a copy button for the
 * phone that has no browser willing to take an intent.
 */
@Composable
private fun TranslationContributionSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val url = SupportedLocales.CONTRIBUTION_PROJECT_URL
    val requestUrl = SupportedLocales.LANGUAGE_REQUEST_URL
    val qr = remember(url) { CompanionLink.renderQr(url) }
    var status by remember { mutableStateOf<Int?>(null) }
    var copyUrl by remember { mutableStateOf(url) }

    fun open(target: String) {
        status = if (openContributionLink(context, target)) {
            null
        } else {
            copyUrl = target
            R.string.settings_language_contribution_open_failed
        }
    }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_language_help_translate),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            Text(
                text = stringResource(R.string.settings_language_contribution_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_language_request_workflow),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (qr != null) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(8.dp),
                ) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription =
                            stringResource(R.string.settings_language_contribution_qr_description),
                        modifier = Modifier.size(180.dp),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.settings_language_contribution_qr_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            MobileButton(
                text = url,
                onClick = { open(url) },
                modifier = Modifier.fillMaxWidth(),
                style = MobileButtonStyle.SECONDARY,
            )
            MobileButton(
                text = stringResource(R.string.settings_language_request_new),
                onClick = { open(requestUrl) },
                modifier = Modifier.fillMaxWidth(),
                style = MobileButtonStyle.SECONDARY,
            )
            status?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
                MobileButton(
                    text = stringResource(R.string.settings_language_contribution_copy),
                    onClick = {
                        status = if (copyContributionLink(context, copyUrl)) {
                            R.string.settings_language_contribution_copied
                        } else {
                            R.string.settings_language_contribution_copy_failed
                        }
                    },
                    style = MobileButtonStyle.SECONDARY,
                )
                MobileButton(
                    text = stringResource(R.string.settings_close),
                    onClick = onDismiss,
                    style = MobileButtonStyle.TEXT,
                )
            }
        }
    }
}

private fun openContributionLink(context: Context, url: String): Boolean = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    true
}.getOrDefault(false)

private fun copyContributionLink(context: Context, url: String): Boolean = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val label = context.getString(R.string.settings_language_help_translate)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, url))
    true
}.getOrDefault(false)
