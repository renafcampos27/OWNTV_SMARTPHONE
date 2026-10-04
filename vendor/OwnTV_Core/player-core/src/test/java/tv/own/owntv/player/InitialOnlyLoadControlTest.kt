package tv.own.owntv.player

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.DefaultAllocator
import org.junit.Assert.*
import org.junit.Test

class InitialOnlyLoadControlTest {
    @Test fun `Java default API forwards lifecycle and loading policy to delegate`() {
        val calls = mutableListOf<String>()
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        val parameters = LoadControl.Parameters(PlayerId.UNSET, Timeline.EMPTY, MediaPeriodId("source"),
            0L, 0L, 1f, true, false, C.TIME_UNSET, C.TIME_UNSET)
        val selections = arrayOfNulls<ExoTrackSelection>(0)
        val delegate = object : LoadControl {
            override fun onPrepared(playerId: PlayerId) { calls += "prepared" }
            override fun onTracksSelected(
                actual: LoadControl.Parameters,
                trackGroups: TrackGroupArray,
                trackSelections: Array<out ExoTrackSelection?>,
            ) {
                assertSame(parameters, actual)
                assertSame(TrackGroupArray.EMPTY, trackGroups)
                assertSame(selections, trackSelections)
                calls += "tracks"
            }
            override fun onStopped(playerId: PlayerId) { calls += "stopped" }
            override fun onReleased(playerId: PlayerId) { calls += "released" }
            override fun getAllocator(playerId: PlayerId) = allocator
            override fun getBackBufferDurationUs(playerId: PlayerId) = 37L
            override fun retainBackBufferFromKeyframe(playerId: PlayerId) = true
            override fun shouldContinueLoading(actual: LoadControl.Parameters): Boolean {
                assertSame(parameters, actual)
                calls += "loading"
                return false
            }
            override fun shouldContinuePreloading(
                playerId: PlayerId, timeline: Timeline, mediaPeriodId: MediaPeriodId,
                bufferedDurationUs: Long,
            ): Boolean {
                assertSame(parameters.playerId, playerId)
                assertSame(parameters.timeline, timeline)
                assertSame(parameters.mediaPeriodId, mediaPeriodId)
                assertEquals(43L, bufferedDurationUs)
                calls += "preloading"
                return true
            }
        }
        val control = InitialOnlyLoadControl(delegate) { true }
        control.onPrepared(parameters.playerId)
        control.onTracksSelected(parameters, TrackGroupArray.EMPTY, selections)
        assertSame(allocator, control.getAllocator(parameters.playerId))
        assertEquals(37L, control.getBackBufferDurationUs(parameters.playerId))
        assertTrue(control.retainBackBufferFromKeyframe(parameters.playerId))
        assertFalse(control.shouldContinueLoading(parameters))
        assertTrue(control.shouldContinuePreloading(parameters.playerId, parameters.timeline,
            parameters.mediaPeriodId, 43L))
        control.onStopped(parameters.playerId)
        control.onReleased(parameters.playerId)
        assertEquals(listOf("prepared", "tracks", "loading", "preloading", "stopped", "released"), calls)
    }

    @Test fun `reprepare after playback bypasses initial wait while new tune restores it`() {
        var opened = false
        var initialChecks = 0
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        val delegate = object : LoadControl {
            override fun getAllocator(playerId: PlayerId) = allocator
            override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
                initialChecks++
                return false
            }
            override fun shouldContinueLoading(parameters: LoadControl.Parameters) = true
        }
        val control = InitialOnlyLoadControl(delegate) { opened }
        val preparing = LoadControl.Parameters(PlayerId.UNSET, Timeline.EMPTY, MediaPeriodId("source"),
            0L, 0L, 1f, true, false, C.TIME_UNSET, C.TIME_UNSET)
        assertFalse(control.shouldStartPlayback(preparing))
        opened = true
        assertTrue(control.shouldStartPlayback(preparing))
        assertEquals(1, initialChecks)
        assertSame(allocator, control.getAllocator(PlayerId.UNSET))
        assertTrue(control.shouldContinueLoading(preparing))
        opened = false
        assertFalse(control.shouldStartPlayback(preparing))
        assertEquals(2, initialChecks)
    }
}
