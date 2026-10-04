package tv.own.owntv.mobile.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R
import tv.own.owntv.player.TrackLabelKind
import tv.own.owntv.player.TrackOption
import java.util.Locale

/**
 * The final wording of a track row. The engine hands out raw labels, language codes and ordinals and
 * nothing else — a translated word stored in engine state would be the wrong word after a language
 * change — so a nameless track becomes "English" or "Subtitle 2" here and only here.
 */
@Composable
fun TrackOption.displayLabel(): String {
    val configuration = LocalResources.current.configuration
    val locale = if (configuration.locales.isEmpty) Locale.ENGLISH else configuration.locales[0]
    val raw = label.takeIf { it.isNotBlank() }
    val language = lang
        ?.takeIf { it.isNotBlank() && !it.equals("und", ignoreCase = true) }
        ?.let { code ->
            runCatching { Locale.forLanguageTag(code).getDisplayLanguage(locale) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: code.uppercase(locale)
        }
    val fallback = when (labelKind) {
        TrackLabelKind.AUDIO -> stringResource(R.string.player_audio_track_number, displayNumber())
        TrackLabelKind.SUBTITLE -> stringResource(R.string.player_subtitle_track_number, displayNumber())
    }
    return raw ?: language ?: fallback
}

private fun TrackOption.displayNumber(): Int = (typeIndex.takeIf { it >= 0 } ?: mpvId) + 1
