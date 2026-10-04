package tv.own.owntv.mobile.ui.screens.live

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity

class ChannelAlternativesTest {
    @Test fun prebufferProgressReceivesFiniteOpeningAllowance() = runBlocking {
        val tried = mutableListOf<Int>()
        val result = tryChannelAlternatives(
            initial = 1,
            alternatives = { listOf(2) },
            timeoutMs = 80,
            attempt = { tried += it; delay(180); true },
            failed = {}, switching = {},
            startupProgress = { tv.own.owntv.player.LiveStartupProgress(1, 400, 100) },
        )
        assertTrue(result)
        assertEquals(listOf(1), tried)
    }

    @Test fun prebufferSilenceStillTimesOutAndMovesToAnAlternative() = runBlocking {
        val tried = mutableListOf<Int>()
        assertTrue(tryChannelAlternatives(
            initial = 1, alternatives = { listOf(2) }, timeoutMs = 80,
            attempt = { tried += it; if (it == 1) awaitCancellation(); true },
            failed = {}, switching = {},
            startupProgress = { tv.own.owntv.player.LiveStartupProgress(1, 10_000, 0) },
        ))
        assertEquals(listOf(1, 2), tried)
    }

    private fun ch(id: Long, name: String, source: Long = 1, url: String = "https://test/$id") =
        ChannelEntity(id = id, name = name, sourceId = source, categoryId = 1, streamUrl = url, remoteId = "$id")

    @Test fun qualityAndCodecLabelsAreEquivalent() {
        val names = listOf("PT: Sport TV 7", "PT: Sport TV 7 Full HD", "PT: Sport TV 7 (low)", "PT: Sport TV 7 hevc", "089. PT: Sport TV 7 HD")
        assertEquals(1, names.map(ChannelAlternatives::key).distinct().size)
        assertEquals(ChannelAlternatives.key("PT: TVI"), ChannelAlternatives.key("PT: TVI HVEC"))
    }
    @Test fun confirmedAliasesWork() {
        assertEquals(ChannelAlternatives.key("PT: Historia HD"), ChannelAlternatives.key("PT: Canal Historia"))
        assertEquals(ChannelAlternatives.key("PT: Discovery HD"), ChannelAlternatives.key("PT: Discovery Channel"))
    }
    @Test fun differentProgrammesStaySeparate() {
        for ((a,b) in listOf("PT: RTP 1" to "PT: RTP 2", "PT: CNN" to "PT: CNN Portugal", "PT: Sport TV NBA HD" to "PT: NBA HD", "PT: Mezzo" to "PT: Mezzo Live HD", "PT: RTP 1" to "BR: RTP 1")) {
            assertNotEquals(ChannelAlternatives.key(a), ChannelAlternatives.key(b))
        }
    }
    @Test fun selectionComesFirstAndDuplicateNamesKeepSeparateStreams() {
        val initial = ch(1,"PT: Da Zone 1 HD")
        val choices = ChannelAlternatives.ordered(initial, listOf(ch(2,"PT: Da Zone 1 Full HD"), ch(3,"PT: Da Zone 1 Full HD"), ch(4,"PT: Da Zone 1 Low"), ch(5,"PT: Da Zone 1 HD",source=2), ch(6,"PT: Da Zone 2 HD"), ch(7,"PT: Da Zone 1 HD",url=initial.streamUrl)))
        assertEquals(listOf(1L,2L,3L,4L), choices.map { it.id })
    }
    @Test fun successfulInitialChannelDoesNotLoadAlternatives() = runBlocking {
        assertTrue(tryChannelAlternatives(1, alternatives = { error("Unexpected lookup") }, attempt = { true }, failed = {}, switching = {}))
    }
    @Test fun timeoutSwitchesOnceAndStopsOnSuccess() = runBlocking {
        val tried = mutableListOf<Int>()
        var failures = 0
        assertTrue(tryChannelAlternatives(1, { listOf(2,3) }, timeoutMs=30,
            attempt = { tried += it; if (it==1) awaitCancellation(); true },
            failed = { failures++ }, switching = {}))
        assertEquals(listOf(1,2), tried)
        assertEquals(1, failures)
    }
    @Test fun cancellationNeverStartsAnAlternative() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val tried = mutableListOf<Int>()
        val job = launch { tryChannelAlternatives(1, { listOf(2) }, attempt = { tried += it; started.complete(Unit); awaitCancellation() }, failed = {}, switching = {}) }
        started.await()
        job.cancelAndJoin()
        assertEquals(listOf(1), tried)
    }
    @Test fun exhaustionTerminatesWithoutRepeating() = runBlocking {
        val tried = mutableListOf<Int>()
        assertFalse(tryChannelAlternatives(1, { listOf(2,3) }, attempt = { tried += it; false }, failed = {}, switching = {}))
        assertEquals(listOf(1,2,3), tried)
    }
    @Test fun providerBackoffPausesTimeoutAndAllowsSuccess() = runBlocking {
        var backingOff = true
        val tried = mutableListOf<Int>()
        val result = tryChannelAlternatives(
            initial = 1,
            alternatives = { listOf(2) },
            timeoutMs = 60,
            attempt = {
                tried += it
                delay(100)
                backingOff = false
                true
            },
            failed = {},
            switching = {},
            isBackingOff = { backingOff },
        )
        assertTrue(result)
        assertEquals(listOf(1), tried)
    }
    @Test fun providerBackoffSkipsAlternatives() = runBlocking {
        val tried = mutableListOf<Int>()
        val result = tryChannelAlternatives(
            initial = 1,
            alternatives = { listOf(2, 3) },
            timeoutMs = 20,
            attempt = { tried += it; false },
            failed = {},
            switching = {},
            isBackingOff = { true },
        )
        assertFalse(result)
        assertEquals(listOf(1), tried)
    }
    @Test fun shouldSkipAlternativeSkipsDesignatedCandidates() = runBlocking {
        val tried = mutableListOf<Int>()
        val result = tryChannelAlternatives(
            initial = 1,
            alternatives = { listOf(2, 3, 4) },
            timeoutMs = 20,
            attempt = { tried += it; it == 4 },
            failed = {},
            switching = {},
            shouldSkipAlternative = { it == 2 || it == 3 },
        )
        assertTrue(result)
        assertEquals(listOf(1, 4), tried)
    }

    @Test fun shouldSkipAlternativeDoesNotSkipInitialChannel() = runBlocking {
        val tried = mutableListOf<Int>()
        val result = tryChannelAlternatives(
            initial = 1,
            alternatives = { listOf(2) },
            timeoutMs = 20,
            attempt = { tried += it; true },
            failed = {},
            switching = {},
            shouldSkipAlternative = { it == 1 || it == 2 },
        )
        assertTrue(result)
        assertEquals(listOf(1), tried)
    }

    @Test fun backoffCeilingTimesOutWhenBackoffExceedsCeiling() = runBlocking {
        val result = runWithBackoffAwareTimeout(
            timeoutMs = 200,
            isBackingOff = { true },
            backoffRemainingMs = { 5000L },
            maxWaitCeilingMs = 80L,
            block = {
                delay(200)
                true
            },
        )
        assertFalse(result)
    }
}
