package tv.own.owntv.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter

/**
 * A channel's logo, with something to show when there isn't one.
 *
 * The reason this exists rather than a bare [AsyncImage]: **a logo URL that exists is not a logo that
 * arrives.** Playlists are full of links that 404, time out or point at a host that is long gone, and
 * every copy of this in the app tested only whether the URL was null — so a broken link drew nothing
 * at all, and the channel list, the guide, the home rows and the player's own plate all showed blank
 * gaps where a logo should be. Only a missing URL fell back; a failed one did not.
 *
 * [fallback] is drawn for both cases, which is the point: to a user there is no difference between a
 * channel with no logo and a channel whose logo will not load.
 */
@Composable
fun ChannelLogoImage(
    url: String?,
    fallback: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    // Keyed on the URL so zapping to a channel whose logo is fine tries again rather than inheriting
    // the previous channel's failure.
    var failed by remember(url) { mutableStateOf(url != null && (url in deadLogos || hostOf(url)?.let { it in deadHosts } == true)) }
    if (url.isNullOrBlank() || failed) {
        fallback()
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier,
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    deadLogos += url
                    hostOf(url)?.let { deadHosts += it }
                    failed = true
                }
            },
        )
    }
}

/**
 * Logo URLs that have already failed once this run.
 *
 * Without this the fallback is correct but arrives *late*: a dead host is a connect timeout, so the
 * plate sits empty for the best part of half a minute — and because nothing remembers the outcome,
 * it does it again every time the list is scrolled back or the screen is reopened. A playlist whose
 * logo host has gone away therefore looks broken on every visit rather than the first.
 *
 * Deliberately in memory only, and never removed from: a URL that timed out on this launch is not
 * worth another twenty seconds on this launch. Restarting the app retries everything, which is the
 * right granularity for "the host came back".
 */
private val deadLogos = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

/**
 * Hosts that have already failed once this run — the same memory as [deadLogos], one level up.
 *
 * Per-URL is not enough, because a playlist's logos overwhelmingly come from one host, and when that
 * host is *gone* (measured: no ICMP, no TCP/80, a ten-second connect timeout per request) remembering
 * URLs one at a time means every channel scrolled to pays the ten seconds itself, for ever. One
 * failure is enough to know the rest are coming: the first channel waits, the whole list after it
 * falls back at once.
 *
 * Same lifetime and same reasoning as [deadLogos]: in memory, never removed from, cleared by a restart.
 */
private val deadHosts = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

private fun hostOf(url: String?): String? = runCatching { java.net.URI(url).host }.getOrNull()
