package tv.own.owntv.player

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VideoOutputReadyTest {
    @Test fun noDecoderStartUntilTheVisibleViewArrives() = runTest {
        val surfaces = MutableStateFlow<Int?>(null)
        val audio = MutableStateFlow(false)
        val start = async { awaitVideoOutput(surfaces, audio) { it > 0 }; true }
        runCurrent(); assertFalse(start.isCompleted)
        surfaces.value = -1
        runCurrent(); assertFalse(start.isCompleted)
        surfaces.value = 1
        runCurrent(); assertTrue(start.await())
    }

    @Test fun currentValidOutputStartsWithoutAnArtificialDelay() = runTest {
        val before = testScheduler.currentTime
        awaitVideoOutput(MutableStateFlow(Any()), MutableStateFlow(false)) { true }
        assertEquals(before, testScheduler.currentTime)
    }

    @Test fun enablingAudioOnlyUnblocksWithoutAnySurface() = runTest {
        val audio = MutableStateFlow(false)
        val start = async { awaitVideoOutput(MutableStateFlow<Any?>(null), audio) { true } }
        runCurrent(); assertFalse(start.isCompleted)
        audio.value = true
        runCurrent(); assertTrue(start.isCompleted)
        start.await()
    }

    @Test fun returningFromAudioOnlyRequiresARealOutputAgain() = runTest {
        val surfaces = MutableStateFlow<Any?>(null)
        val audio = MutableStateFlow(true)
        awaitVideoOutput(surfaces, audio) { true }
        audio.value = false
        val video = async { awaitVideoOutput(surfaces, audio) { true } }
        runCurrent(); assertFalse(video.isCompleted)
        surfaces.value = Any()
        runCurrent(); assertTrue(video.isCompleted)
        video.await()
    }

    @Test fun supersededWaitDoesNotStartOnTheNextViewsCallback() = runTest {
        val surfaces = MutableStateFlow<Any?>(null)
        var oldStarts = 0
        val old = async { awaitVideoOutput(surfaces, MutableStateFlow(false)) { true }; oldStarts++ }
        runCurrent(); old.cancel(); surfaces.value = Any()
        runCurrent(); assertTrue(old.isCancelled); assertEquals(0, oldStarts)
    }

    @Test fun missingOutputHasABoundedPreparationDeadline() = runTest {
        val start = withTimeoutOrNull(5000) {
            awaitVideoOutput(MutableStateFlow<Any?>(null), MutableStateFlow(false)) { true }
            true
        }
        assertNull(start)
        assertEquals(5000L, testScheduler.currentTime)
    }
}
