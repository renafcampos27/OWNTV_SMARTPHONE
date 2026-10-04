package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DiagnosticWriterTest {
    @Test fun blockedStorageDoesNotBlockProducerAndQueueIsBounded() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writer = DiagnosticWriter(2) { entered.countDown(); release.await() }
        try {
            writer.offer("first")
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            repeat(100) { writer.offer("event$it") }
            assertEquals(98L, writer.dropped.get())
        } finally { release.countDown(); writer.close() }
    }
    @Test fun writerSurvivesStorageFailureAndPreservesOrder() {
        val failed = CountDownLatch(1)
        val recovered = CountDownLatch(1)
        val lines = mutableListOf<String>()
        var first = true
        val writer = DiagnosticWriter { batch ->
            if (first) { first = false; failed.countDown(); throw java.io.IOException() }
            lines.addAll(batch); recovered.countDown()
        }
        try {
            writer.offer("bad")
            assertTrue(failed.await(2, TimeUnit.SECONDS))
            writer.offer("good")
            assertTrue(recovered.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("good"), lines)
            assertEquals(1L, writer.failures.get())
        } finally { writer.close() }
    }
    @Test fun rotationIsByteBoundedWithLongUtf8Lines() {
        val dir = Files.createTempDirectory("diagnostic-test").toFile()
        val file = dir.resolve("log.txt")
        try {
            repeat(50) { n ->
                appendDiagnosticBatch(file, listOf("$n " + "á".repeat(200)), 1024)
                assertTrue(file.length() <= 1024)
                assertFalse(file.readText().contains('\uFFFD'))
            }
            assertTrue(file.readText().contains("49 "))
        } finally { file.delete(); dir.delete() }
    }
    @Test fun persistedExportReadsHistoryWithoutInMemoryEvents() {
        val file = Files.createTempFile("diagnostic-export", ".log").toFile()
        try {
            appendDiagnosticBatch(file, listOf("choice source=1", "playback_sample state=LOADING", "http_end"), 4096)
            val exported = readDiagnosticTail(file, 4096)
            assertTrue(exported.contains("state=LOADING"))
            assertTrue(exported.contains("http_end"))
        } finally { file.delete() }
    }
    @Test fun persistedExportBoundsReadsAndKeepsCompleteUtf8Lines() {
        val file = Files.createTempFile("diagnostic-tail", ".log").toFile()
        try {
            file.writeText("antigo".repeat(1000) + "\ntransmissão a carregar\nfim\n")
            assertEquals("transmissão a carregar\nfim\n", readDiagnosticTail(file, 100))
        } finally { file.delete() }
        assertEquals("", readDiagnosticTail(file, 100))
    }

}
