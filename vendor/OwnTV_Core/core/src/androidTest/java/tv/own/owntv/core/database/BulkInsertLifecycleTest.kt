package tv.own.owntv.core.database

import androidx.room.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual helper and Room's bundled driver, including interrupted imports. */
@RunWith(AndroidJUnit4::class)
class BulkInsertLifecycleTest {
    private lateinit var db: OwnTVDatabase
    private lateinit var bulk: BulkInsertHelper

    @Before fun setup() { db = ownTVTestDatabase(); bulk = BulkInsertHelper(db) }
    @After fun close() { db.close() }

    private suspend fun schemaObjects(type: String): Set<String> = db.useWriterConnection { connection ->
        connection.usePrepared("SELECT name FROM sqlite_master WHERE type=? AND tbl_name='channels'") { statement ->
            statement.bindText(1, type)
            buildSet { while (statement.step()) add(statement.getText(0)) }
        }
    }

    @Test fun ftsOnlyKeepsIndexesDuringImportAndAfterSuccess() = runBlocking {
        val indexes = schemaObjects("index")
        val triggers = schemaObjects("trigger")
        assertTrue(indexes.isNotEmpty())
        bulk.withOptimizedBulkInsert("channels", "channels_fts", eligible = true, ftsOnly = true) {
            assertEquals(indexes, schemaObjects("index"))
        }
        assertEquals(indexes, schemaObjects("index"))
        assertEquals(triggers, schemaObjects("trigger"))
    }

    @Test fun ftsOnlyRestoresTriggerAfterFailure() = runBlocking {
        val indexes = schemaObjects("index")
        val triggers = schemaObjects("trigger")
        val result = runCatching {
            bulk.withOptimizedBulkInsert("channels", "channels_fts", eligible = true, ftsOnly = true) {
                assertEquals(indexes, schemaObjects("index"))
                error("interrupted import")
            }
        }
        assertTrue(result.isFailure)
        assertEquals(indexes, schemaObjects("index"))
        assertEquals(triggers, schemaObjects("trigger"))
    }

    @Test fun cancelledImportRestoresSchema() = runBlocking {
        val indexes = schemaObjects("index")
        val triggers = schemaObjects("trigger")
        val inside = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val writer = launch {
            bulk.withOptimizedBulkInsert("channels", "channels_fts", eligible = true, ftsOnly = true) {
                inside.complete(Unit); never.await()
            }
        }
        inside.await(); writer.cancelAndJoin()
        assertEquals(indexes, schemaObjects("index"))
        assertEquals(triggers, schemaObjects("trigger"))
    }

    @Test fun cancellationAtRegistrationNeverRunsImportOrLeavesDroppedSchema() = runBlocking {
        val indexes = schemaObjects("index")
        val triggers = schemaObjects("trigger")
        var ranImport = false
        val writer = launch(start = CoroutineStart.UNDISPATCHED) {
            cancel()
            bulk.withOptimizedBulkInsert("channels", "channels_fts", eligible = true, ftsOnly = true) {
                ranImport = true
            }
        }
        writer.join()
        assertTrue(writer.isCancelled)
        assertFalse(ranImport)
        assertEquals(indexes, schemaObjects("index"))
        assertEquals(triggers, schemaObjects("trigger"))
    }

    @Test fun completeBulkModeRestoresCanonicalIndexes() = runBlocking {
        val before = schemaObjects("index")
        bulk.withOptimizedBulkInsert("channels", "channels_fts", eligible = true) {
            assertTrue(schemaObjects("index").size < before.size)
        }
        assertTrue(schemaObjects("index").containsAll(before))
    }

    @Test fun triggerIsRestoredOnlyAfterLastConcurrentWriter() = runBlocking {
        val triggers = schemaObjects("trigger")
        val firstInside = CompletableDeferred<Unit>()
        val secondInside = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val releaseSecond = CompletableDeferred<Unit>()
        val first = launch {
            bulk.withOptimizedBulkInsert("channels", "channels_fts", eligible = true, ftsOnly = true) {
                firstInside.complete(Unit); releaseFirst.await()
            }
        }
        firstInside.await()
        val second = launch {
            bulk.withOptimizedBulkInsert("channels", "channels_fts", eligible = false) {
                secondInside.complete(Unit); releaseSecond.await()
            }
        }
        secondInside.await(); releaseFirst.complete(Unit); first.join()
        assertFalse(schemaObjects("trigger").containsAll(triggers))
        releaseSecond.complete(Unit); second.join()
        assertEquals(triggers, schemaObjects("trigger"))
    }
}
