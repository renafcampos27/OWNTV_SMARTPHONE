package tv.own.owntv.mobile.ui.screens.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.model.MediaType

class ResumeProgressSaverTest {
    @Test fun idleAndLocalRecordingHaveNoCatalogResumeTimer() = runBlocking {
        saveResumeWhileActive(flowOf(null, VodPlayback(MediaType.LIVE, -1L, "Recording")),
            wait = { fail("Idle timer") }, save = { fail("Unexpected resume write") })
    }

    @Test fun activeItemSavesAtTickAndStoppingCancelsThePendingTick() = runBlocking {
        withTimeout(5_000L) {
            val playing = MutableStateFlow<VodPlayback?>(VodPlayback(MediaType.MOVIE, 1L, "Test"))
            val waits = Channel<CompletableDeferred<Unit>>(Channel.UNLIMITED)
            val saved = Channel<Unit>(Channel.UNLIMITED)
            val cancelled = CompletableDeferred<Unit>()
            val job = launch {
                saveResumeWhileActive(playing, wait = {
                    val gate = CompletableDeferred<Unit>()
                    waits.send(gate)
                    try { gate.await() } finally {
                        if (!currentCoroutineContext().isActive) cancelled.complete(Unit)
                    }
                }, save = { saved.send(Unit) })
            }
            waits.receive().complete(Unit)
            saved.receive()
            waits.receive()
            playing.value = null
            cancelled.await()
            job.cancelAndJoin()
            assertTrue(saved.tryReceive().isFailure)
            assertTrue(waits.tryReceive().isFailure)
        }
    }
}
