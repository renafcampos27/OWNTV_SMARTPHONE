package tv.own.owntv.mobile.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Called from one dispatcher: metadata never waits for artwork, and obsolete results cannot publish. */
internal class NotificationArtwork<S : Any, A : Any>(
    private val scope: CoroutineScope,
    private val load: suspend (String) -> A?,
    private val publish: (S, A?) -> Unit,
) {
    private var latest: S? = null
    private var url: String? = null
    private var artwork: A? = null
    private var request: Job? = null
    private var generation = 0L

    fun update(shown: S, artworkUrl: String?) {
        latest = shown
        val nextUrl = artworkUrl?.takeIf { it.isNotBlank() }
        if (nextUrl == url) {
            publish(shown, artwork)
            return
        }
        request?.cancel()
        val currentGeneration = ++generation
        url = nextUrl
        artwork = null
        // Clear the old channel's image and publish controls before starting any image request.
        publish(shown, null)
        if (nextUrl == null) return
        request = scope.launch {
            val result = try {
                load(nextUrl)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (!isActive || currentGeneration != generation) return@launch
            artwork = result
            if (result != null) latest?.let { publish(it, result) }
        }
    }

    fun close() {
        generation++
        request?.cancel()
        latest = null
        artwork = null
    }
}
