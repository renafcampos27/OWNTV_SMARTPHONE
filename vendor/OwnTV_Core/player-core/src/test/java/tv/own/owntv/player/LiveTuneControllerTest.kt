package tv.own.owntv.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.player.EnginePreference
import tv.own.owntv.core.stalker.ReconnectUrlProvider

/**
 * The sequencing both apps used to hand-copy: superseding tunes, the ladder across engines, and the
 * "Give up after" alarm. Driven on virtual time against a fake engine pair.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveTuneControllerTest {

    private class FakeEngines : LiveEngines {
        val log = mutableListOf<String>()
        var lastRequest: LiveRequest? = null
        override val exoManualTs: Boolean get() = lastRequest?.manualTs == true
        override val exoChannelOptions get() = lastRequest?.channelOptions
        var resolverCancellations = 0
        override var exoUrl: String? = null
        override var exoIsHls = false
        override var exoFailed = false
        override var exoPlaybackRequested = true
        override var mpvPlaybackRequested = true
        override var mpvPlaybackRevision = 0L
        var drain: CompletableDeferred<Boolean>? = null
        override suspend fun exoStopAndAwaitDrain(): Boolean { exoStop(); return drain?.await() ?: true }
        override var exoPlaybackConfirmed = false
        override var mpvPlaybackConfirmed = false
        override var mpvHasStream = false
        var settingsReady: CompletableDeferred<Unit>? = null
        override suspend fun awaitSettings() { settingsReady?.await() }

        /** Complete with a reason to fail the ExoPlayer watch, or with null for "opened". */
        var exoWatch: CompletableDeferred<String?>? = null
        var mpvWatch: CompletableDeferred<MpvOutcome>? = null
        var midstreamFailure: CompletableDeferred<String>? = null
        var wait: LiveProviderWait? = null
        var progress: LiveStartupProgress? = null
        override fun providerWait() = wait
        override fun startupProgress() = progress

        override fun exoPlay(url: String, muted: Boolean, request: LiveRequest) {
            exoUrl = url
            exoPlaybackConfirmed = false
            lastRequest = request
            log += "exo:$url"
        }
        override fun exoSetMuted(muted: Boolean) { log += if (muted) "exo-mute" else "exo-unmute" }
        override fun exoStop() { exoUrl = null }
        override fun exoCancelResolver() { resolverCancellations++ }
        override fun exoReleaseUhdDecoder() {}
        override fun exoAbandon(reason: String) { log += "exo-abandon" }

        override suspend fun watchExo(
            channelName: String,
            stillOurs: () -> Boolean,
            handOver: suspend (String) -> Unit,
            onOpened: () -> Unit,
            postponeDeadline: (Long) -> Unit,
            log: (String) -> Unit,
        ) {
            val d = CompletableDeferred<String?>().also { exoWatch = it }
            val reason = d.await()
            if (reason == null) {
                exoPlaybackConfirmed = true
                onOpened()
                midstreamFailure?.await()?.let { exoPlaybackConfirmed = false; if (stillOurs()) handOver(it) }
            } else if (stillOurs()) handOver(reason)
        }

        override fun mpvPlay(url: String, request: LiveRequest) {
            lastRequest = request
            mpvHasStream = true
            mpvPlaybackConfirmed = false
            log += "mpv:$url"
        }
        override fun mpvStop() { mpvHasStream = false }
        override suspend fun mpvStopAndAwaitRelease() { mpvHasStream = false }
        override fun mpvAbandon(reason: String) { log += "mpv-abandon" }
        override suspend fun awaitMpvOutcome(timeoutMs: Long): MpvOutcome? {
            val d = CompletableDeferred<MpvOutcome>().also { mpvWatch = it }
            return withTimeoutOrNull(timeoutMs) { d.await() }
        }
        override fun setReconnectProvider(provider: ReconnectUrlProvider?) {}
    }

    private class FakeHost(private val scope: TestScope) : LiveTuneController.Host {
        var strictHls = false
        val options = mutableMapOf<Long, tv.own.owntv.core.settings.ChannelPlaybackOptions>()
        override suspend fun channelOptions(channel: ChannelEntity) = options[channel.id]
        val manualTsIds = mutableSetOf<Long>()
        override suspend fun manualTs(channel: ChannelEntity) = channel.id in manualTsIds
        var source: SourceEntity? = null
        override suspend fun hlsOnly() = strictHls
        var preference = EnginePreference.EXO_FIRST
        var budgetSecs = 30
        var sourceDelayMs = 0L
        val pins = mutableListOf<Boolean>()
        val events = mutableListOf<PlayerFailureReason>()
        val opened = mutableListOf<Long>()
        override fun onOpened(channel: ChannelEntity) { opened += channel.id }

        override suspend fun sourceOf(sourceId: Long): SourceEntity? {
            if (sourceDelayMs > 0) delay(sourceDelayMs)
            return source
        }
        override fun needsResolve(source: SourceEntity?) = false
        override suspend fun resolve(source: SourceEntity, cmd: String): String? = null
        override suspend fun enginePin(channel: ChannelEntity): Boolean? = null
        override suspend fun pin(channel: ChannelEntity, onMpv: Boolean) { pins += onMpv }
        override suspend fun globalPreference() = preference
        override suspend fun globalBudgetSecs() = budgetSecs
        override fun meta(channel: ChannelEntity) = MediaMeta(title = channel.name)
        override fun recordLadderEvent(onExo: Boolean, reason: PlayerFailureReason, detail: String) {
            events += reason
        }
        override fun nowMs(): Long = scope.testScheduler.currentTime
    }

    private fun channel(id: Long) = ChannelEntity(
        id = id,
        sourceId = 1,
        name = "ch$id",
        streamUrl = "http://tune-controller-test.invalid/live/$id.ts",
    )

    private fun TestScope.controller(engines: FakeEngines, host: FakeHost) =
        LiveTuneController(backgroundScope, engines, host)

    @Test
    fun `local reader uses only local HLS without upstream credentials or alternate URL`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { preference = EnginePreference.MPV_ONLY }
        val c = controller(engines, host)
        val original = channel(1).copy(httpHeaders = "Authorization: secret", directSource = "http://upstream.invalid/private", manifestType = "mpd")
        c.startLocalHls(original, "http://127.0.0.1:1234/token/index.m3u8", 42)
        runCurrent()
        assertTrue(c.liveOnExo.value)
        assertEquals("http://127.0.0.1:1234/token/index.m3u8", engines.exoUrl)
        assertEquals(null, engines.lastRequest?.httpHeaders)
        assertEquals(null, engines.lastRequest?.directSource)
        assertEquals("hls", engines.lastRequest?.manifestType)
        assertEquals(42L, engines.lastRequest?.choiceId)
        engines.exoWatch!!.complete("boom")
        advanceTimeBy(1000); runCurrent()
        assertFalse(engines.log.any { it.startsWith("mpv:") || it.contains("upstream.invalid") || it.contains("index.ts") })
        c.stop()
    }

    @Test
    fun `a newer tune supersedes one still waiting out the decoder release`() = runTest {
        val engines = FakeEngines().apply { exoUrl = "http://preview.invalid/x" }
        val host = FakeHost(this).apply { preference = EnginePreference.MPV_FIRST }
        val c = controller(engines, host)
        c.tune(channel(1))
        runCurrent() // channel 1 is now waiting for ExoPlayer's decoder before mpv
        host.preference = EnginePreference.EXO_FIRST
        c.tune(channel(2))
        advanceTimeBy(5_000)
        assertFalse("the superseded tune must never reach mpv", engines.log.any { it.startsWith("mpv:") })
        assertEquals(listOf("exo:${channel(2).streamUrl}"), engines.log)
    }

    @Test
    fun `an ExoPlayer failure climbs to mpv, and mpv failing too ends the tune`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this)
        val c = controller(engines, host)
        c.tune(channel(3))
        runCurrent()
        assertTrue(c.liveOnExo.value)
        engines.exoWatch!!.complete("boom")
        advanceTimeBy(OwnTVPlayer.SURFACE_HANDOFF_MS + 1)
        assertFalse(c.liveOnExo.value)
        assertTrue(engines.log.contains("mpv:${channel(3).streamUrl}"))
        engines.mpvWatch!!.complete(MpvOutcome(opened = false, error = "nope"))
        runCurrent()
        assertEquals("mpv-abandon", engines.log.last())
        assertEquals(
            listOf(PlayerFailureReason.LIVE_FALLBACK, PlayerFailureReason.LIVE_NO_FALLBACK),
            host.events,
        )
    }

    @Test
    fun `the give-up alarm ends a tune that never opens`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { budgetSecs = 10 }
        val c = controller(engines, host)
        c.tune(channel(4))
        advanceTimeBy(9_000)
        assertFalse(engines.log.contains("exo-abandon"))
        advanceTimeBy(1_001)
        assertEquals("exo-abandon", engines.log.last())
        assertEquals(listOf(PlayerFailureReason.LIVE_NO_FALLBACK), host.events)
    }

    @Test
    fun `a channel that opens stands the alarm down`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { budgetSecs = 10 }
        val c = controller(engines, host)
        c.tune(channel(5))
        runCurrent()
        engines.exoWatch!!.complete(null)
        advanceTimeBy(60_000)
        assertFalse(engines.log.contains("exo-abandon"))
        assertTrue(host.events.isEmpty())
    }

    @Test
    fun `a late failure of a replaced tune changes nothing`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this)
        val c = controller(engines, host)
        c.tune(channel(6))
        runCurrent()
        val oldWatch = engines.exoWatch!!
        c.tune(channel(7))
        runCurrent()
        oldWatch.complete("late failure")
        advanceTimeBy(5_000)
        assertFalse(engines.log.any { it.startsWith("mpv:") })
        assertTrue(host.events.isEmpty())
    }

    @Test
    fun `handing the player to an archive cancels a live tune still in flight`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { sourceDelayMs = 100 }
        val c = controller(engines, host)
        c.tune(channel(8))
        advanceTimeBy(50)
        var archiveStarted = false
        c.launch {
            releaseForArchive()
            delay(10) // a catch-up resolving its archive URL
            archiveStarted = true
        }
        advanceTimeBy(5_000)
        assertTrue("the catch-up must survive its own release", archiveStarted)
        assertTrue("the live stream must not start over the archive", engines.log.isEmpty())
    }

    @Test
    fun `the engine button pins the choice and stays on that engine`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this)
        val c = controller(engines, host)
        c.tune(channel(9))
        runCurrent()
        c.toggleEngine()
        advanceTimeBy(OwnTVPlayer.SURFACE_HANDOFF_MS + 1)
        assertEquals(listOf(true), host.pins)
        assertTrue(engines.log.contains("mpv:${channel(9).streamUrl}"))
        engines.mpvWatch!!.complete(MpvOutcome(opened = false, error = "nope"))
        advanceTimeBy(5_000)
        // "mpv only" for this tune: no hand-back to ExoPlayer, the failure stays on screen.
        assertEquals("mpv-abandon", engines.log.last())
        assertEquals(1, engines.log.count { it.startsWith("exo:") })
    }

    @Test
    fun `a tune on mpv with nothing on ExoPlayer does not wait for a decoder`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { preference = EnginePreference.MPV_FIRST }
        val c = controller(engines, host)
        c.tune(channel(10))
        runCurrent()
        assertTrue(engines.log.contains("mpv:${channel(10).streamUrl}"))
    }

    @Test
    fun `a tune started before the settings are read waits for them instead of using defaults`() = runTest {
        val engines = FakeEngines()
        val stored = CompletableDeferred<EnginePreference>()
        val host = object : LiveTuneController.Host by FakeHost(this) {
            override suspend fun globalPreference() = stored.await()
        }
        val c = LiveTuneController(backgroundScope, engines, host)
        c.tune(channel(12))
        advanceTimeBy(1_000)
        assertTrue("nothing may open on a default while the store is unread", engines.log.isEmpty())
        stored.complete(EnginePreference.MPV_ONLY) // the store's first read lands: the user chose mpv
        runCurrent()
        assertEquals(listOf("mpv:${channel(12).streamUrl}"), engines.log)
    }

    @Test
    fun `pressing OK on the channel being previewed promotes it instead of rebuilding`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this)
        val c = controller(engines, host)
        c.preview(channel(11), muted = true)
        runCurrent()
        c.tune(channel(11))
        runCurrent()
        assertEquals(listOf("exo:${channel(11).streamUrl}", "exo-unmute"), engines.log)
    }
    @Test
    fun `both preview and fullscreen wait for the engine settings snapshot`() = runTest {
        val engines = FakeEngines().apply { settingsReady = CompletableDeferred() }
        val c = controller(engines, FakeHost(this))
        c.preview(channel(20), muted = true)
        runCurrent()
        assertTrue(engines.log.isEmpty())
        c.tune(channel(21))
        runCurrent()
        assertTrue(engines.log.isEmpty())
        engines.settingsReady!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("exo:${channel(21).streamUrl}"), engines.log)
    }

    @Test
    fun `a failed preview is rebuilt when OK is pressed`() = runTest {
        val engines = FakeEngines()
        val c = controller(engines, FakeHost(this))
        c.preview(channel(22), muted = true)
        runCurrent()
        engines.exoFailed = true
        c.tune(channel(22))
        runCurrent()
        assertEquals(2, engines.log.count { it.startsWith("exo:") })
        assertFalse(engines.log.contains("exo-unmute"))
    }

    @Test
    fun `a slow cancelled lookup cannot retune A after A B A`() = runTest {
        val engines = FakeEngines()
        var lookups = 0
        val host = object : LiveTuneController.Host by FakeHost(this) {
            override suspend fun sourceOf(sourceId: Long): SourceEntity? {
                if (lookups++ == 0) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { delay(200) }
                return null
            }
        }
        val c = LiveTuneController(backgroundScope, engines, host)
        c.tune(channel(23))
        runCurrent()
        c.tune(channel(24))
        runCurrent()
        c.tune(channel(23))
        runCurrent()
        val before = engines.log.toList()
        advanceTimeBy(300)
        runCurrent()
        assertEquals(before, engines.log)
        assertEquals(listOf("exo:${channel(24).streamUrl}", "exo:${channel(23).streamUrl}"), engines.log)
    }

    @Test
    fun `mpv fallback back to Exo survives replacing its own watcher`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { preference = EnginePreference.MPV_FIRST }
        val c = controller(engines, host)
        c.tune(channel(25))
        runCurrent()
        engines.mpvWatch!!.complete(MpvOutcome(false, "bad stream"))
        advanceTimeBy(OwnTVPlayer.SURFACE_HANDOFF_MS + 1)
        runCurrent()
        assertTrue(c.liveOnExo.value)
        assertTrue(engines.log.contains("exo:${channel(25).streamUrl}"))
    }

    @Test
    fun `stop invalidates an opening alarm and a pending preview`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { budgetSecs = 1 }
        val c = controller(engines, host)
        c.tune(channel(26))
        runCurrent()
        c.stop()
        host.sourceDelayMs = 100
        c.preview(channel(27), true)
        runCurrent()
        c.stop()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(listOf("exo:${channel(26).streamUrl}"), engines.log)
        assertFalse(c.liveOnExo.value)
        assertTrue(host.events.isEmpty())
    }

    @Test fun `per channel settings reach the request and do not leak into the next tune`() = runTest {
        val engines = FakeEngines()
        val override = tv.own.owntv.core.settings.ChannelPlaybackOptions(
            engine = EnginePreference.EXO_ONLY, reserveSecs = 15, extraSecs = 4, prerollSecs = 6,
            latencySecs = 12, softwareAudio = true, audioDelayMs = 250)
        val host = FakeHost(this).apply { strictHls = true; options[1] = override }
        val c = controller(engines, host)
        c.tune(channel(1)); runCurrent()
        assertEquals(override, engines.lastRequest?.channelOptions)
        assertEquals(15, engines.lastRequest?.reserveBuffer?.secs)
        assertEquals(4, engines.lastRequest?.reserveExtraSecs)
        assertEquals(6, engines.lastRequest?.prerollSecs)
        assertEquals(12, engines.lastRequest?.liveBuffer?.secs)
        c.tune(channel(2)); advanceTimeBy(1000); runCurrent()
        assertNull(engines.lastRequest?.channelOptions)
        assertNull(engines.lastRequest?.reserveBuffer)
        assertNull(engines.lastRequest?.prerollSecs)
        c.stop()
    }

    @Test fun `explicit per channel format permission allows mpv without changing global HLS`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply {
            strictHls = true
            options[1] = tv.own.owntv.core.settings.ChannelPlaybackOptions(engine = EnginePreference.MPV_ONLY,
                format = tv.own.owntv.core.settings.ChannelStreamFormat.AUTO, prerollSecs = 5, audioDelayMs = -150)
        }
        val c = controller(engines, host)
        c.tune(channel(1)); advanceTimeBy(1000); runCurrent()
        assertTrue(engines.log.any { it == "mpv:${channel(1).streamUrl}" })
        assertEquals(-150, engines.lastRequest?.channelOptions?.audioDelayMs)
        c.tune(channel(2)); advanceTimeBy(1000); runCurrent()
        assertTrue(c.liveOnExo.value)
        assertNull(engines.lastRequest?.channelOptions)
        assertTrue(host.strictHls)
        c.stop()
    }


    @Test fun `manual mpv with strict HLS retains signed headers and never falls back to TS or next channel`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply {
            strictHls = true
            preference = EnginePreference.MPV_ONLY
            source = SourceEntity(id = 1, name = "test", type = tv.own.owntv.core.model.SourceType.XTREAM,
                url = "http://tune-controller-test.invalid", preferHls = false)
            options[1] = tv.own.owntv.core.settings.ChannelPlaybackOptions(engine = EnginePreference.MPV_ONLY,
                format = tv.own.owntv.core.settings.ChannelStreamFormat.HLS)
        }
        val c = controller(engines, host)
        c.tune(channel(1).copy(streamUrl = "http://tune-controller-test.invalid/live/1.ts?token=x",
            httpHeaders = "Authorization: Bearer test"))
        advanceTimeBy(1000); runCurrent()
        assertTrue(engines.log.contains("mpv:http://tune-controller-test.invalid/live/1.m3u8?token=x"))
        assertTrue(engines.lastRequest!!.strictHls)
        assertEquals("Authorization: Bearer test", engines.lastRequest?.httpHeaders)
        engines.mpvWatch!!.complete(MpvOutcome(false, "manifest error")); runCurrent()
        assertTrue(engines.log.contains("mpv-abandon"))
        assertFalse(engines.log.any { it.startsWith("exo:") || it.startsWith("mpv:") && it.endsWith(".ts?token=x") })
        c.tune(channel(2)); advanceTimeBy(1000); runCurrent()
        assertEquals("http://tune-controller-test.invalid/live/2.m3u8", engines.exoUrl)
        assertNull(engines.lastRequest?.channelOptions)
        c.stop()
    }

    @Test fun `explicit HLS channel setting overrides a legacy TS exception`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply {
            strictHls = true
            manualTsIds += 1L
            options[1] = tv.own.owntv.core.settings.ChannelPlaybackOptions(format = tv.own.owntv.core.settings.ChannelStreamFormat.HLS)
            source = SourceEntity(id = 1, name = "test", type = tv.own.owntv.core.model.SourceType.XTREAM, url = "http://tune-controller-test.invalid")
        }
        val c = controller(engines, host)
        c.tune(channel(1)); runCurrent()
        assertEquals(false, engines.lastRequest?.manualTs)
        assertEquals("http://tune-controller-test.invalid/live/1.m3u8", engines.exoUrl)
        c.stop()
    }

    @Test fun `changed channel buffer cannot reuse a preview with the old configuration`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { options[1] = tv.own.owntv.core.settings.ChannelPlaybackOptions(prerollSecs = 3) }
        val c = controller(engines, host)
        c.preview(channel(1), true); runCurrent()
        host.options[1] = tv.own.owntv.core.settings.ChannelPlaybackOptions(prerollSecs = 8)
        c.tune(channel(1)); runCurrent()
        assertEquals(8, engines.lastRequest?.prerollSecs)
        assertEquals(2, engines.log.count { it == "exo:${channel(1).streamUrl}" })
        c.stop()
    }

    @Test fun `manual TS exception does not contaminate the following HLS channel`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply {
            strictHls = true
            preference = EnginePreference.MPV_ONLY
            manualTsIds += 1L
            source = SourceEntity(id = 1, name = "test", type = tv.own.owntv.core.model.SourceType.XTREAM,
                url = "http://tune-controller-test.invalid", preferHls = true)
        }
        val c = controller(engines, host)
        c.tune(channel(1).copy(streamUrl = "http://tune-controller-test.invalid/live/1.m3u8")); runCurrent()
        assertEquals("http://tune-controller-test.invalid/live/1.ts", engines.exoUrl)
        assertEquals(true, engines.lastRequest?.manualTs)
        engines.exoWatch!!.complete("manifest malformed"); runCurrent()
        assertFalse(engines.log.any { it.startsWith("mpv:") || it == "exo:http://tune-controller-test.invalid/live/1.m3u8" })
        c.tune(channel(2)); advanceTimeBy(1000); runCurrent()
        assertEquals("http://tune-controller-test.invalid/live/2.m3u8", engines.exoUrl)
        assertEquals(false, engines.lastRequest?.manualTs)
        host.manualTsIds.clear()
        c.tune(channel(1)); advanceTimeBy(1000); runCurrent()
        assertEquals("http://tune-controller-test.invalid/live/1.m3u8", engines.exoUrl)
        c.stop()
    }

    @Test fun `changed manual exception cannot promote a preview built under the old policy`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this)
        val c = controller(engines, host)
        c.preview(channel(1), true); runCurrent()
        assertEquals(false, engines.lastRequest?.manualTs)
        host.manualTsIds += 1L
        c.tune(channel(1)); runCurrent()
        assertEquals(true, engines.lastRequest?.manualTs)
        assertEquals(2, engines.log.count { it == "exo:${channel(1).streamUrl}" })
        c.toggleEngine(channel(1)); runCurrent()
        assertTrue(host.pins.isEmpty())
        c.stop()
    }

    @Test fun `manual TS cannot override local timeshift HLS`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { strictHls = true; manualTsIds += 1L }
        val c = controller(engines, host)
        c.startLocalHls(channel(1), "http://127.0.0.1:1234/local/index.m3u8", 1L); runCurrent()
        assertEquals(false, engines.lastRequest?.manualTs)
        assertEquals("http://127.0.0.1:1234/local/index.m3u8", engines.exoUrl)
        c.stop()
    }

    @Test fun `strict HLS overrides mpv preference and refuses TS fallback after error`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply {
            strictHls = true
            preference = EnginePreference.MPV_ONLY
            source = SourceEntity(id = 1, name = "test", type = tv.own.owntv.core.model.SourceType.XTREAM,
                url = "http://tune-controller-test.invalid", preferHls = false)
        }
        val c = controller(engines, host)
        c.tune(channel(1)); runCurrent()
        assertTrue(engines.log.contains("exo:http://tune-controller-test.invalid/live/1.m3u8"))
        engines.exoWatch!!.complete("response code:403"); runCurrent()
        assertTrue(engines.log.contains("exo-abandon"))
        assertFalse(engines.log.any { it.startsWith("mpv:") || it.startsWith("exo:") && it.endsWith(".ts") })
        c.toggleEngine(channel(1)); runCurrent()
        assertTrue(host.pins.isEmpty())
    }
    @Test fun `turning strict HLS off restores the saved engine preference`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { strictHls = true; preference = EnginePreference.MPV_ONLY }
        val c = controller(engines, host)
        c.tune(channel(1)); runCurrent()
        host.strictHls = false
        c.tune(channel(2)); advanceTimeBy(1000); runCurrent()
        assertTrue(engines.log.any { it == "mpv:${channel(2).streamUrl}" })
    }

    @Test fun `provider wait starts protecting a fifteen second deadline before watchdog timeout`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { budgetSecs = 15 }
        val c = controller(engines, host)
        c.tune(channel(40)); runCurrent()
        engines.wait = LiveProviderWait(1, 30_000L)
        advanceTimeBy(20_000L); runCurrent()
        assertFalse(engines.log.contains("exo-abandon"))
        engines.wait = null
        advanceTimeBy(25_101L); runCurrent()
        assertTrue(engines.log.contains("exo-abandon"))
    }

    @Test fun `repeated provider waits cannot extend the alarm indefinitely`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { budgetSecs = 15 }
        val c = controller(engines, host)
        c.tune(channel(41)); runCurrent()
        engines.wait = LiveProviderWait(1, 60_000L)
        advanceTimeBy(20_000L); runCurrent()
        engines.wait = LiveProviderWait(2, 60_000L)
        advanceTimeBy(55_101L); runCurrent()
        assertTrue(engines.log.contains("exo-abandon"))
    }

    @Test fun `a failure after five minutes starts finite recovery instead of using expired opening deadline`() = runTest {
        val engines = FakeEngines().apply { midstreamFailure = CompletableDeferred() }
        val host = FakeHost(this).apply { budgetSecs = 3 }
        val c = controller(engines, host)
        c.tune(channel(42)); runCurrent()
        engines.exoWatch!!.complete(null); runCurrent()
        advanceTimeBy(300_000L); runCurrent()
        engines.midstreamFailure!!.complete("midstream failure")
        advanceTimeBy(OwnTVPlayer.SURFACE_HANDOFF_MS + 1); runCurrent()
        assertTrue(engines.log.contains("mpv:${channel(42).streamUrl}"))
        assertFalse(engines.log.contains("mpv-abandon"))
        advanceTimeBy(30_001L); runCurrent()
        assertTrue(engines.log.contains("mpv-abandon"))
    }

    @Test fun `a new choice cancels a provider wait and its credited deadline`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { budgetSecs = 1 }
        val c = controller(engines, host)
        c.tune(channel(43)); runCurrent()
        engines.wait = LiveProviderWait(1, 30_000L)
        advanceTimeBy(500); runCurrent()
        engines.wait = null
        c.tune(channel(44)); runCurrent()
        advanceTimeBy(1_100L); runCurrent()
        assertEquals(1, engines.log.count { it == "exo-abandon" })
        assertEquals(channel(44).streamUrl, engines.exoUrl)
    }

    @Test fun `new selection cancels old portal resolution before a slow source lookup completes`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this)
        val c = controller(engines, host)
        c.tune(channel(45)); runCurrent()
        val before = engines.resolverCancellations
        host.sourceDelayMs = 500
        c.tune(channel(46)); runCurrent()
        assertTrue(engines.resolverCancellations > before)
        assertEquals(listOf("exo:${channel(45).streamUrl}"), engines.log)
    }


    @Test
    fun `opening at the deadline succeeds before the watcher next polls`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { budgetSecs = 1 }
        val c = controller(engines, host)
        c.tune(channel(70)); runCurrent()
        advanceTimeBy(999); runCurrent()
        engines.exoPlaybackConfirmed = true // render happened; watch callback is still pending.
        advanceTimeBy(2); runCurrent()
        assertFalse(engines.log.contains("exo-abandon"))
        assertTrue(host.events.isEmpty())
        engines.exoWatch!!.complete(null); runCurrent()
        assertEquals(listOf(70L), host.opened)
        c.stop()
    }

    @Test
    fun `a stale failure cannot replace a source that has recovered`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this)
        val c = controller(engines, host)
        c.tune(channel(71)); runCurrent()
        engines.exoPlaybackConfirmed = true
        engines.exoWatch!!.complete("old opening error")
        advanceTimeBy(1000); runCurrent()
        assertFalse(engines.log.any { it == "exo-abandon" || it.startsWith("mpv:") })
        assertTrue(host.events.isEmpty())
        c.stop()
    }

    @Test
    fun `a previous channel success does not satisfy the new channel deadline`() = runTest {
        val engines = FakeEngines()
        val host = FakeHost(this).apply { budgetSecs = 1; strictHls = true }
        val c = controller(engines, host)
        c.tune(channel(72)); runCurrent()
        engines.exoPlaybackConfirmed = true
        c.tune(channel(73)); runCurrent()
        advanceTimeBy(1001); runCurrent()
        assertTrue(engines.log.contains("exo-abandon"))
        c.stop()
    }
    @Test fun `paused opening consumes no deadline but remaining active budget is finite`() = runTest {
        val e = FakeEngines(); val h = FakeHost(this).apply { budgetSecs = 1 }
        val c = controller(e, h)
        c.tune(channel(1)); runCurrent()
        advanceTimeBy(400); runCurrent()
        e.exoPlaybackRequested = false
        advanceTimeBy(30_000); runCurrent()
        assertFalse(e.log.contains("exo-abandon"))
        e.exoPlaybackRequested = true
        advanceTimeBy(800); runCurrent()
        assertTrue(e.log.contains("exo-abandon"))
    }

    @Test fun `failure observed during pause cannot advance or open another engine`() = runTest {
        val e = FakeEngines(); val h = FakeHost(this)
        val c = controller(e, h)
        c.tune(channel(1)); runCurrent()
        e.exoPlaybackRequested = false
        e.exoWatch!!.complete("failed"); runCurrent()
        advanceTimeBy(40_000); runCurrent()
        assertFalse(e.log.any { it.startsWith("mpv:") || it.endsWith("abandon") })
        e.exoPlaybackRequested = true
        advanceTimeBy(1000); runCurrent()
        assertTrue(e.log.any { it.startsWith("mpv:") })
    }

    @Test fun `mpv handoff awaits Exo drain rather than the hardware delay alone`() = runTest {
        val e = FakeEngines().apply { drain = CompletableDeferred() }; val h = FakeHost(this)
        val c = controller(e, h)
        c.tune(channel(1)); runCurrent()
        e.exoWatch!!.complete("failed"); runCurrent()
        advanceTimeBy(1200); runCurrent()
        assertFalse(e.log.any { it.startsWith("mpv:") })
        e.drain!!.complete(true); runCurrent()
        assertTrue(e.log.any { it.startsWith("mpv:") })
    }

    @Test fun `drain timeout refuses mpv opening`() = runTest {
        val e = FakeEngines().apply { drain = CompletableDeferred() }; val h = FakeHost(this)
        val c = controller(e, h)
        c.tune(channel(1)); runCurrent()
        e.exoWatch!!.complete("failed"); runCurrent()
        e.drain!!.complete(false); runCurrent()
        advanceTimeBy(1000); runCurrent()
        assertFalse(e.log.any { it.startsWith("mpv:") })
        assertTrue(e.log.contains("mpv-abandon"))
    }

    @Test fun `new channel cancels old handoff waiting for HTTP drain`() = runTest {
        val e = FakeEngines().apply { drain = CompletableDeferred() }; val h = FakeHost(this)
        val c = controller(e, h)
        c.tune(channel(1)); runCurrent()
        e.exoWatch!!.complete("failed"); runCurrent()
        c.tune(channel(2)); runCurrent()
        e.drain!!.complete(true); advanceTimeBy(1000); runCurrent()
        assertFalse(e.log.any { it.startsWith("mpv:") })
        assertTrue(e.exoUrl!!.contains("/2."))
    }

    @Test fun `pause during cross-engine drain prevents new mpv request until Play`() = runTest {
        val e = FakeEngines().apply { drain = CompletableDeferred() }; val h = FakeHost(this)
        val c = controller(e, h)
        c.tune(channel(1)); runCurrent()
        e.exoWatch!!.complete("failed"); runCurrent()
        e.mpvPlaybackRequested = false; e.mpvPlaybackRevision++
        e.drain!!.complete(true); advanceTimeBy(5000); runCurrent()
        assertFalse(e.log.any { it.startsWith("mpv:") })
        e.mpvPlaybackRequested = true; e.mpvPlaybackRevision++
        advanceTimeBy(101); runCurrent()
        assertTrue(e.log.any { it.startsWith("mpv:") })
    }

}
