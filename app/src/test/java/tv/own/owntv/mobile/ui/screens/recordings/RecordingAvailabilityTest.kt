package tv.own.owntv.mobile.ui.screens.recordings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.storage.MediaAvailability

class RecordingAvailabilityTest {
    private fun row(id: Long) = RecordingEntity(id = id, profileId = id, sourceId = 1, channelId = 1,
        channelName = "", streamUrl = "", title = "", programmeStartMs = 0, programmeStopMs = 100,
        startMs = 0, stopMs = 100, bytes = 100, filePath = "/capture.ts", status = RecordingStatus.COMPLETED)

    @Test fun leavingCancelsTimerAndReturningReadsTheNewProfileWithoutBackgroundProbes() = runBlocking {
        withTimeout(5_000L) {
            val rows = MutableStateFlow(listOf(row(1)))
            val active = MutableStateFlow(false)
            val emitted = Channel<Map<Long, MediaAvailability>>(Channel.UNLIMITED)
            val inspected = Channel<List<Long>>(Channel.UNLIMITED)
            val waiting = Channel<Unit>(Channel.UNLIMITED)
            var cancelledTimers = 0
            val job = launch {
                recordingAvailability(rows, active, inspect = { list ->
                    inspected.send(list.map { it.id })
                    if (list.single().id == 1L) mapOf(1L to MediaAvailability.STORAGE_UNAVAILABLE) else emptyMap()
                }, pause = {
                    waiting.send(Unit)
                    try { awaitCancellation() } finally { cancelledTimers++ }
                }).collect { emitted.send(it) }
            }
            assertEquals(emptyMap<Long, MediaAvailability>(), emitted.receive())
            assertTrue(inspected.tryReceive().isFailure)
            active.value = true
            assertEquals(listOf(1L), inspected.receive())
            assertEquals(mapOf(1L to MediaAvailability.STORAGE_UNAVAILABLE), emitted.receive())
            waiting.receive()
            active.value = false
            assertEquals(emptyMap<Long, MediaAvailability>(), emitted.receive())
            assertEquals(1, cancelledTimers)
            rows.value = listOf(row(2))
            active.value = true
            assertEquals(listOf(2L), inspected.receive())
            waiting.receive()
            job.cancelAndJoin()
            assertEquals(2, cancelledTimers)
            assertTrue(inspected.tryReceive().isFailure)
        }
    }

    @Test fun leavingDuringReadCancelsTheReadAndNeverPublishesItsOldResult() = runBlocking {
        withTimeout(5_000L) {
            val entered = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val active = MutableStateFlow(true)
            val emitted = Channel<Map<Long, MediaAvailability>>(Channel.UNLIMITED)
            val job = launch {
                recordingAvailability(MutableStateFlow(listOf(row(1))), active, inspect = {
                    entered.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }).collect { emitted.send(it) }
            }
            entered.await()
            active.value = false
            assertEquals(emptyMap<Long, MediaAvailability>(), emitted.receive())
            cancelled.await()
            job.cancelAndJoin()
            assertTrue(emitted.tryReceive().isFailure)
        }
    }

    @Test fun activeRecordingProgressAndFinishedMetadataDoNotRestartAvailabilityProbes() = runBlocking {
        withTimeout(5_000L) {
            val finished = row(1)
            val capturing = row(2).copy(status = RecordingStatus.RECORDING, bytes = 10L)
            val update = CompletableDeferred<Unit>()
            val processed = CompletableDeferred<Unit>()
            val paused = CompletableDeferred<Unit>()
            var reads = 0
            var cancelledTimers = 0
            val inputs = flow {
                emit(listOf(finished, capturing))
                update.await()
                emit(listOf(capturing.copy(bytes = 200L), finished.copy(title = "Updated title", bytes = 101L)))
                processed.complete(Unit)
                awaitCancellation()
            }
            val job = launch {
                recordingAvailability(inputs, MutableStateFlow(true), inspect = { list ->
                    reads++
                    assertEquals(listOf(1L), list.map { it.id })
                    emptyMap()
                }, pause = {
                    paused.complete(Unit)
                    try { awaitCancellation() } finally { cancelledTimers++ }
                }).collect()
            }
            paused.await()
            update.complete(Unit)
            processed.await()
            assertEquals(1, reads)
            assertEquals(0, cancelledTimers)
            job.cancelAndJoin()
            assertEquals(1, cancelledTimers)
        }
    }
}
