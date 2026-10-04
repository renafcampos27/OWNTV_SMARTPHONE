package tv.own.owntv.mobile.ui.components

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import tv.own.owntv.core.content.AirDate
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import java.text.SimpleDateFormat
import java.util.Date

/**
 * The day an episode first aired, ready to render, or null when nothing knows it — the provider's
 * own date where the panel sends one, TMDB's otherwise (see `AirDate` in core).
 *
 * Asked for by a user whose series run to thousands of episodes, where the titles are near-identical
 * and the episode number stops being a landmark long before episode nine hundred.
 */
@Composable
fun rememberAirDateLabel(episode: EpisodeEntity, meta: MetadataCacheEntity?): String? {
    val ms = AirDate.of(episode.airDateMs, meta?.airDate) ?: return null
    val locale = LocalConfiguration.current.locales[0]
    // Formatted in UTC, like it was parsed: an air date is a calendar day, and rendering it in the
    // phone's own zone would show the day before to everyone west of Greenwich.
    val formatter = remember(locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, AIR_DATE_SKELETON), locale)
            .apply { timeZone = AirDate.UTC }
    }
    return remember(formatter, ms) { formatter.format(Date(ms)) }
}

/** Year, abbreviated month, day — the shortest form that still identifies a specific broadcast. */
private const val AIR_DATE_SKELETON = "yMMMd"
