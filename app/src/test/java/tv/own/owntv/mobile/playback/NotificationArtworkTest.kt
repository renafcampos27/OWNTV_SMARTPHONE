package tv.own.owntv.mobile.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class NotificationArtworkTest {
    @Test fun metadataAndControlsPublishBeforeSlowArtAndCompletionUsesLatestState() = runBlocking {
        val gate = CompletableDeferred<String>()
        val published = mutableListOf<Pair<String, String?>>()
        var loads = 0
        val artwork = NotificationArtwork<String, String>(this, { loads++; gate.await() }) { state, art -> published += state to art }
        artwork.update("A playing", "a")
        assertEquals(listOf("A playing" to null), published)
        yield()
        artwork.update("A paused", "a")
        assertEquals("A paused" to null, published.last())
        gate.complete("logo A")
        yield()
        assertEquals("A paused" to "logo A", published.last())
        assertEquals(1, loads)
        artwork.update("A playing", "a")
        assertEquals("A playing" to "logo A", published.last())
        assertEquals(1, loads)
        artwork.close()
    }

    @Test fun lateCancelledArtCannotOverwriteNewChannelEvenAfterReturningToSameUrl() = runBlocking {
        val old = CompletableDeferred<String>()
        val published = mutableListOf<Pair<String, String?>>()
        var firstA = true
        val artwork = NotificationArtwork<String, String>(this, { url ->
            if (url == "a" && firstA) {
                firstA = false
                withContext(NonCancellable) { old.await() }
            } else "new $url"
        }) { state, art -> published += state to art }
        artwork.update("A old", "a"); yield()
        artwork.update("B", "b"); yield()
        assertEquals("B" to "new b", published.last())
        artwork.update("A new", "a"); yield()
        assertEquals("A new" to "new a", published.last())
        val before = published.size
        old.complete("obsolete a"); yield(); yield()
        assertEquals(before, published.size)
        artwork.close()
    }

    @Test fun missingUrlClearsImageAndCancelsPendingFetch() = runBlocking {
        val gate = CompletableDeferred<String>()
        val published = mutableListOf<Pair<String, String?>>()
        val artwork = NotificationArtwork<String, String>(this, { gate.await() }) { state, art -> published += state to art }
        artwork.update("A", "a"); yield()
        artwork.update("B", null)
        gate.complete("old"); yield()
        assertEquals("B" to null, published.last())
        artwork.update("C", "   ")
        assertEquals("C" to null, published.last())
        artwork.close()
    }

    @Test fun failedArtDoesNotBlockStateChangesOrRetryOnEveryPause() = runBlocking {
        val published = mutableListOf<Pair<String, String?>>()
        var loads = 0
        val artwork = NotificationArtwork<String, String>(this, { loads++; throw java.io.IOException() }) { state, art -> published += state to art }
        artwork.update("A playing", "a"); yield()
        artwork.update("A paused", "a")
        assertEquals("A paused" to null, published.last())
        assertEquals(1, loads)
        artwork.close()
    }

    @Test fun serviceCloseRejectsLateNonCancellableCompletion() = runBlocking {
        val gate = CompletableDeferred<String>()
        val published = mutableListOf<Pair<String, String?>>()
        val artwork = NotificationArtwork<String, String>(this, { withContext(NonCancellable) { gate.await() } }) { state, art -> published += state to art }
        artwork.update("A", "a"); yield()
        artwork.close()
        gate.complete("late"); yield(); yield()
        assertEquals(listOf("A" to null), published)
    }
}
