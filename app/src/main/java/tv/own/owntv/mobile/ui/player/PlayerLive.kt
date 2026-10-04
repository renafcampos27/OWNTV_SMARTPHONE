package tv.own.owntv.mobile.ui.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.screens.guide.rememberGuideTimeFormat
import tv.own.owntv.mobile.ui.theme.LocalAccentOnVideo
import tv.own.owntv.mobile.ui.theme.LocalAnimations
import tv.own.owntv.mobile.ui.theme.SquircleShape
import java.text.SimpleDateFormat
import java.util.Date

/**
 * The live instrument: the clock, the guide card, the state badge and the way back to the edge.
 *
 * Everything here is the television's, re-cut for a phone. Two differences run through all of it.
 * The screen is narrow, so in portrait each piece shows its shorter half — the wall clock without
 * its date, Now without Next — and grows into the full thing in landscape. And nothing is drawn on
 * an M3 container, so every `Text` states its own colour.
 */

/** The live edge, and the amber that says the picture is behind it. */
private val LiveRed = Color(0xFFDC3232)
private val BehindAmber = Color(0xFFE8A33D)

private const val CLOCK_TICK_MS = 10_000L
private const val CARD_TICK_MS = 20_000L
private const val PULSE_MS = 900

/** Wide enough for the longest translation of "Go live", so the pill is one size in every language. */
private val GoLiveSlot = 108.dp

private val PlateShape = RoundedCornerShape(10.dp)
private val Hairline = Color.White.copy(alpha = 0.18f)

/** A wall clock that ticks slowly. Minute precision is all any of this shows. */
@Composable
private fun rememberWallClock(periodMs: Long): Long {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            nowMs = System.currentTimeMillis()
        }
    }
    return nowMs
}

/** "Tue 3 Sep", in whatever order this language writes it. */
@Composable
private fun rememberPlayerDateFormat(): SimpleDateFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
    }
}

private fun landscape(orientation: Int) = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

/**
 * The clock, in the top dock.
 *
 * Normally one column: the time now. While an archive is on screen a second column joins it on the
 * left — the recording's own time, advancing as it plays — because a lone "22:10" over a picture
 * from yesterday afternoon is worse than no clock at all. Both columns are labelled, which is what
 * stops a single time from being read as a broken device clock.
 *
 * In portrait the date lines are dropped: the dock is a phone wide, and the time is the part that
 * has to fit.
 */
@Composable
internal fun MobilePlayerClock(watchingMs: Long?, modifier: Modifier = Modifier) {
    val wide = landscape(LocalConfiguration.current.orientation)
    val formatTime = rememberGuideTimeFormat()
    val formatDate = rememberPlayerDateFormat()
    val nowMs = rememberWallClock(CLOCK_TICK_MS)
    val accent = LocalAccentOnVideo.current

    Row(
        modifier
            .clip(PlateShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), PlateShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (watchingMs != null) {
            ClockColumn(
                label = stringResource(R.string.content_clock_programme),
                time = formatTime.format(Date(watchingMs)),
                date = formatDate.format(Date(watchingMs)).takeIf { wide },
                labelColor = accent,
                timeColor = accent,
                dateColor = accent.copy(alpha = 0.7f),
            )
            Box(Modifier.height(34.dp).width(1.dp).background(Hairline))
        }
        ClockColumn(
            label = stringResource(R.string.content_clock_current),
            time = formatTime.format(Date(nowMs)),
            date = formatDate.format(Date(nowMs)).takeIf { wide },
            labelColor = Color.White.copy(alpha = 0.45f),
            timeColor = Color.White,
            dateColor = Color.White.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun ClockColumn(
    label: String,
    time: String,
    date: String?,
    labelColor: Color,
    timeColor: Color,
    dateColor: Color,
) {
    Column(horizontalAlignment = Alignment.Start) {
        SlotLabel(label, labelColor)
        Text(
            time,
            style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
            color = timeColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (date != null) {
            Text(
                date.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = dateColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Now and Next for the channel playing, with the 2 dp line that says how far through Now is.
 *
 * Informational only, and it renders **nothing at all** when the channel has no guide — a permanent
 * "no info" block would be noise every time the controls come back. In portrait it collapses to Now
 * alone, because Next in a column an inch wide is a truncated word, not information.
 *
 * @param atMs the instant being replayed out of the archive, or null while at the live edge. Every
 *   "how far through are we" question is asked about *that* moment: a programme from yesterday would
 *   otherwise always read as finished.
 */
@Composable
internal fun MobileNowNextCard(epg: EpgNowNext?, atMs: Long?, modifier: Modifier = Modifier) {
    if (epg == null || (epg.now == null && epg.next == null)) return
    val archive = atMs != null
    val wide = landscape(LocalConfiguration.current.orientation)
    val formatTime = rememberGuideTimeFormat()
    val wallNow = rememberWallClock(CARD_TICK_MS)
    val nowMs = atMs ?: wallNow
    val accent = LocalAccentOnVideo.current
    val showNext = wide && epg.next != null

    Row(
        modifier = if (!archive) modifier else modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        epg.now?.let { entry ->
            Column(Modifier.weight(1f, fill = false).widthIn(max = 300.dp)) {
                SlotLabel(
                    stringResource(if (archive) R.string.content_archive_playing else R.string.content_live_now),
                    accent,
                )
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val remaining = ((entry.stopMs - nowMs) / 60_000L).toInt()
                Text(
                    if (remaining in 1..600) {
                        stringResource(
                            R.string.content_live_time_remaining,
                            formatTime.format(Date(entry.stopMs)),
                            remaining,
                        )
                    } else {
                        stringResource(
                            R.string.content_live_time_range,
                            formatTime.format(Date(entry.startMs)),
                            formatTime.format(Date(entry.stopMs)),
                        )
                    },
                    style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content),
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val span = (entry.stopMs - entry.startMs).toFloat()
                if (span > 0f) {
                    val progress = ((nowMs - entry.startMs) / span).coerceIn(0f, 1f)
                    Spacer(Modifier.height(5.dp))
                    Box(
                        Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                    ) {
                        Box(
                            Modifier.fillMaxWidth(progress).height(2.dp)
                                .clip(RoundedCornerShape(1.dp)).background(accent),
                        )
                    }
                }
            }
        }
        if (showNext && epg.now != null) {
            Box(Modifier.height(32.dp).width(1.dp).background(Hairline))
        }
        if (showNext) {
            epg.next?.let { entry ->
                Column(Modifier.weight(1f, fill = false).widthIn(max = 240.dp)) {
                    SlotLabel(
                        stringResource(if (archive) R.string.content_archive_then else R.string.content_live_next),
                        Color.White.copy(alpha = 0.45f),
                    )
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(
                            R.string.content_live_time_range,
                            formatTime.format(Date(entry.startMs)),
                            formatTime.format(Date(entry.stopMs)),
                        ),
                        style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content),
                        color = Color.White.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotLabel(text: String, color: Color) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = color,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(bottom = 1.dp),
    )
}

/**
 * One object with two states: red and pulsing at the live edge, amber and static with the offset
 * while behind it.
 *
 * **Never hand a zero-duration tween to `infiniteRepeatable`.** With animations off the scaled
 * duration is 0 ms, and Compose divides the play time by it to find the current repeat — a
 * divide-by-zero on the main thread on the very next frame. That shipped on the television at 4.2.3
 * and crashed full-screen Live TV for every user who had animations turned off. Off skips the
 * transition entirely instead.
 */
@Composable
internal fun LiveStateBadge(offsetSec: Int?, modifier: Modifier = Modifier) {
    val behind = offsetSec != null && offsetSec > 1
    val tint = if (behind) BehindAmber else LiveRed
    // The animated State, not its value: it is read in the dot's graphicsLayer (draw phase), so the
    // pulse does not recompose the badge every frame. Null means a steady dot.
    val pulse = if (behind || LocalAnimations.current == AnimationLevel.OFF) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "liveDot")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
            label = "liveDotAlpha",
        )
    }
    Row(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(tint.copy(alpha = 0.85f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            Modifier
                .size(9.dp)
                .graphicsLayer { alpha = pulse?.value ?: 1f }
                .clip(RoundedCornerShape(50))
                .background(Color.White),
        )
        Text(
            if (behind) {
                stringResource(R.string.player_live_offset, formatTimestamp(offsetSec * 1000L))
            } else {
                stringResource(R.string.player_live)
            },
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Content),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The way back to now — drawn only when there is somewhere to come back from.
 *
 * Its slot is a fixed width so that the tools beside it sit in the same place in every language, and
 * it is absent rather than disabled at the live edge: a button that cannot do anything is one more
 * thing to read.
 */
@Composable
internal fun GoLivePill(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (!enabled) return
    Row(
        modifier
            .width(GoLiveSlot)
            .heightIn(min = 44.dp)
            .clip(SquircleShape(12.dp))
            .background(LiveRed.copy(alpha = 0.85f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(50)).background(Color.White))
        Text(
            stringResource(R.string.player_go_live),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
