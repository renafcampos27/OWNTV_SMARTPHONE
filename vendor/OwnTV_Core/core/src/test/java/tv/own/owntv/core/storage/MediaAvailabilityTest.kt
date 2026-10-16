package tv.own.owntv.core.storage

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaAvailabilityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun missingFileDoesNotCreateDestination() {
        val missing = File(folder.root, "uncreated/final.ts")
        assertEquals(MediaAvailability.MISSING, MediaAvailabilityProbe.inspectPath(missing))
        assertFalse(requireNotNull(missing.parentFile).exists())
    }

    @Test fun removingAndRestoringFileChangesAvailabilityWithoutChangingItsIdentity() {
        val file = folder.newFile("recording.ts").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(MediaAvailability.AVAILABLE, MediaAvailabilityProbe.inspectPath(file, true))
        assertTrue(file.delete())
        assertEquals(MediaAvailability.MISSING, MediaAvailabilityProbe.inspectPath(file))
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(MediaAvailability.AVAILABLE, MediaAvailabilityProbe.inspectPath(file, true))
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }

    @Test fun directoryCannotBePlayedAsRecording() {
        assertEquals(MediaAvailability.UNREADABLE, MediaAvailabilityProbe.inspectPath(folder.root))
    }
}
