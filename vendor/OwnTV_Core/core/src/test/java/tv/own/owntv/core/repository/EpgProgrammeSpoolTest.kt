package tv.own.owntv.core.repository

import java.io.RandomAccessFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

class EpgProgrammeSpoolTest {
    @get:Rule val folder = TemporaryFolder()
    private fun row(index: Int) = EpgProgrammeEntity(sourceId = -2, epgChannelId = "rtp1", startMs = index.toLong(),
        stopMs = index + 10L, title = "Programa $index", description = "Descrição\ncom acentos e \"aspas\"",
        categories = "Notícias", year = 2026, rating = "12", lengthMin = 30, episode = "1.2")

    @Test fun metadataRoundTripsInBoundedBatchesAndScratchIsDeleted() = runBlocking {
        val rows = (1..1_001).map(::row) + row(1_002).copy(description = null, categories = null, year = null, rating = null, lengthMin = null, episode = null)
        val restored = mutableListOf<EpgProgrammeEntity>()
        EpgProgrammeSpool(folder.root).use { spool ->
            rows.forEach(spool::append); spool.finish()
            spool.consume(17) { batch -> assertTrue(batch.size <= 17); restored += batch }
        }
        assertEquals(rows, restored)
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test fun truncatedScratchIsRejectedRatherThanAcceptedAsComplete() = runBlocking {
        EpgProgrammeSpool(folder.root).use { spool ->
            spool.append(row(1)); spool.append(row(2)); spool.finish()
            RandomAccessFile(folder.root.listFiles()!!.single(), "rw").use { it.setLength(it.length() - 3) }
            assertTrue(runCatching { spool.consume(10) { } }.isFailure)
        }
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }
}
