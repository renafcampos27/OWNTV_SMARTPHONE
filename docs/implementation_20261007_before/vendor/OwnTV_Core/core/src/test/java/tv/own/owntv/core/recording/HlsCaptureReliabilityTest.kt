package tv.own.owntv.core.recording

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class HlsCaptureReliabilityTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun session() = HlsCaptureSession(temporary.newFolder())
    private fun segment(sequence: Long, token: String = "new") = HlsMediaPlaylist.Segment("$sequence.ts?token=$token", 6.0, sequence)
    private fun downloaded(session: HlsCaptureSession, bytes: ByteArray) = File(session.directory, "download").also {
        HlsSegmentTransfer.copy(ByteArrayInputStream(bytes), it, bytes.size.toLong())
    }

    @Test fun `failed body leaves no eligible segment and checkpoint remains unchanged`() {
        val session = session()
        val scratch = File(session.directory, "download")
        val failing = object : InputStream() {
            var read = 0
            override fun read(): Int { if (read++ < 50) return 0x47; throw IOException("connection lost") }
        }
        try { HlsSegmentTransfer.copy(failing, scratch); fail("expected incomplete download") } catch (_: IOException) { }
        assertFalse(scratch.exists())
        assertEquals(-1L, session.lastSequence)
        assertEquals(0L, session.capturedBytes)
        assertEquals(0, session.gapCount)
    }

    @Test fun `a failed segment retries same sequence with rotated URI before durable commit`() = runBlocking {
        val session = session()
        val requests = mutableListOf<String>()
        var reloads = 0
        val result = HlsSegmentRetry.capture(100, listOf(segment(100, "expired")),
            segmentOf = { list, sequence -> list.firstOrNull { it.sequence == sequence } },
            refresh = { reloads++; listOf(segment(100, "fresh"), segment(101)) },
            capture = { _, current ->
                requests += current.uri
                if (current.uri.endsWith("expired")) false else {
                    session.commit(current, downloaded(session, byteArrayOf(1, 2, 3))); true
                }
            }, wait = { },
        )
        assertTrue(result)
        assertEquals(listOf("100.ts?token=expired", "100.ts?token=fresh"), requests)
        assertEquals(1, reloads)
        assertEquals(100L, session.lastSequence)
        assertEquals(6_000L, session.capturedDurationMs)
        assertEquals(0, session.gapCount)
        val restored = HlsCaptureSession(session.directory)
        assertEquals(session.lastSequence, restored.lastSequence)
        assertEquals(session.capturedDurationMs, restored.capturedDurationMs)
        assertFalse(restored.commit(segment(100, "rotated-again"), downloaded(restored, byteArrayOf(1, 2, 3))))
        assertEquals(1, restored.entries.size)
    }

    @Test fun `retry budget finite and exhausted segment is explicitly recorded as gap once`() = runBlocking {
        val session = session()
        var attempts = 0
        var refreshes = 0
        val result = HlsSegmentRetry.capture(9, listOf(segment(9)),
            segmentOf = { list, sequence -> list.firstOrNull { it.sequence == sequence } },
            refresh = { refreshes++; listOf(segment(9, "rotated$refreshes")) },
            capture = { _, _ -> attempts++; false }, wait = { },
        )
        assertFalse(result)
        assertEquals(3, attempts)
        assertEquals(2, refreshes)
        assertEquals(-1L, session.lastSequence)
        session.skip(9, 6_000)
        session.skip(9, 6_000)
        val restored = HlsCaptureSession(session.directory)
        assertEquals(9L, restored.lastSequence)
        assertEquals(6_000L, restored.missingDurationMs)
        assertEquals(1, restored.gapCount)
        assertEquals(0L, restored.capturedDurationMs)
    }

    @Test fun `expired sequence disappears after refresh without requesting next sequence accidentally`() = runBlocking {
        var attempts = 0
        val result = HlsSegmentRetry.capture(4, listOf(segment(4)),
            segmentOf = { list, sequence -> list.firstOrNull { it.sequence == sequence } },
            refresh = { listOf(segment(5)) }, capture = { _, _ -> attempts++; false }, wait = { },
        )
        assertFalse(result)
        assertEquals(1, attempts)
    }

    @Test fun `cancellation stops retry and does not mark an uncommitted segment captured`() = runBlocking {
        val session = session()
        var requests = 0
        try {
            HlsSegmentRetry.capture(4, listOf(segment(4)),
                segmentOf = { list, sequence -> list.firstOrNull { it.sequence == sequence } },
                refresh = { fail("must not refresh after cancellation"); emptyList() },
                capture = { _, _ -> requests++; false }, wait = { throw CancellationException("stopped") },
            )
            fail("must propagate cancellation")
        } catch (_: CancellationException) { }
        assertEquals(1, requests)
        assertEquals(-1L, session.lastSequence)
        assertEquals(0L, session.capturedDurationMs)
    }

    @Test fun `crash after segment record but before summary update still restores exact capture`() {
        val session = session()
        session.container = "ts"
        session.persist()
        val priorSummary = session.journal.readText()
        session.commit(segment(70), downloaded(session, byteArrayOf(7, 8, 9)))
        session.journal.writeText(priorSummary)
        File(session.directory, "segment.download").writeBytes(byteArrayOf(0, 0))
        File(session.directory, "segment-71.bin").writeBytes(byteArrayOf(4, 5)) // No durable record.
        val restored = HlsCaptureSession(session.directory)
        assertEquals(70L, restored.lastSequence)
        assertEquals(3L, restored.capturedBytes)
        assertEquals(6_000L, restored.capturedDurationMs)
        assertFalse(File(session.directory, "segment-71.bin").exists())
        assertFalse(File(session.directory, "segment.download").exists())
    }

    @Test fun `identical payload in different declared sequences is not mistaken for a missing segment`() {
        val session = session()
        val payload = byteArrayOf(1, 2, 3)
        assertTrue(session.commit(segment(1), downloaded(session, payload)))
        assertTrue(session.commit(segment(2), downloaded(session, payload)))
        assertEquals(2, session.entries.size)
        assertEquals(12_000L, session.capturedDurationMs)
        assertEquals(0, session.gapCount)
    }

    @Test fun `database checkpoint remains bounded as recording grows`() {
        val session = session()
        for (sequence in 1L..80L) session.commit(segment(sequence), downloaded(session, byteArrayOf(sequence.toByte())))
        assertTrue(session.checkpoint().length < 512)
        assertTrue(session.journal.length() < 512)
        assertEquals(80, session.directory.listFiles()!!.count { it.name.startsWith("record-") })
        assertEquals(80, HlsCaptureSession(session.directory).entries.size)
    }

    @Test fun `known content length prevents a silently truncated successful HTTP body`() {
        val session = session()
        val scratch = File(session.directory, "download")
        try { HlsSegmentTransfer.copy(ByteArrayInputStream(byteArrayOf(1)), scratch, 2L); fail("truncated") }
        catch (_: IllegalArgumentException) { }
        assertFalse(scratch.exists())
    }

    @Test fun `cancellation during body copy deletes scratch without exposing partial bytes`() {
        val session = session()
        val scratch = File(session.directory, "download")
        var checks = 0
        try {
            HlsSegmentTransfer.copy(ByteArrayInputStream(ByteArray(256 * 1024)), scratch) {
                if (++checks == 2) throw CancellationException("cancelled")
            }
            fail("must cancel")
        } catch (_: CancellationException) { }
        assertFalse(scratch.exists())
        assertEquals(0L, session.capturedBytes)
    }
    @Test fun `body cannot write beyond the interval reserved for its declared length`() {
        val session = session()
        val scratch = File(session.directory, "download")
        var allowedWrites = 0
        try {
            HlsSegmentTransfer.copy(ByteArrayInputStream(ByteArray(188)), scratch, 100,
                beforeWrite = { _, _ -> allowedWrites++ })
            fail("oversized response")
        } catch (_: IllegalArgumentException) { }
        assertEquals(0, allowedWrites)
        assertFalse(scratch.exists())
    }

    @Test fun `changed init is refused and durable prior init remains untouched`() {
        val session = session()
        session.container = "fmp4"
        session.commitInit(downloaded(session, byteArrayOf(1, 2, 3)))
        val prior = session.initHash
        try { session.commitInit(downloaded(session, byteArrayOf(4, 5, 6))); fail("changed codec initialization") }
        catch (_: IllegalStateException) { }
        val restored = HlsCaptureSession(session.directory)
        assertEquals(prior, restored.initHash)
        assertArrayEquals(byteArrayOf(1, 2, 3), restored.initFile.readBytes())
    }
}
