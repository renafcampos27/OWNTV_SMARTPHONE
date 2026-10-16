package tv.own.owntv.mobile.backup

import org.junit.Assert.*
import org.junit.Test

class DocumentExportTicketTest {
    @Test fun processRecreationPreservesChoicesAndDestinationWithoutASecret() {
        var saved: String? = null
        val before = DocumentExportState({ saved }, { saved = it })
        val request = DocumentExportTicket(DocumentExportTicket.Kind.BACKUP, setOf(1, 2), setOf("SOURCES", "SETTINGS"), encrypted = true)
        assertTrue(before.begin(request))
        val recreated = DocumentExportState({ saved }, { saved = it })
        assertEquals(request, recreated.peek())
        val destination = recreated.target("content://documents/backup")!!
        assertTrue(destination.encrypted)
        assertEquals(request.nonce, destination.nonce)
        assertEquals(setOf("version", "nonce", "kind", "profiles", "sections", "protected", "target"),
            org.json.JSONObject(saved!!).keys().asSequence().toSet())
        assertEquals(destination, recreated.take(DocumentExportTicket.Kind.BACKUP))
        assertNull(recreated.take(DocumentExportTicket.Kind.BACKUP))
    }

    @Test fun recordingIsRecoveredByIdentityAndProfileNotAMenuSnapshot() {
        val ticket = DocumentExportTicket(DocumentExportTicket.Kind.RECORDING_PARTS, setOf(3), recordingId = 42)
        assertEquals(ticket, DocumentExportTicket.decode(ticket.encode()))
    }

    @Test fun anotherExportCannotReplaceAnInflightPickerAndWrongCallbackCannotConsumeIt() {
        var saved: String? = null
        val state = DocumentExportState({ saved }, { saved = it })
        val ticket = DocumentExportTicket(DocumentExportTicket.Kind.RECORDING, setOf(1), recordingId = 4)
        assertTrue(state.begin(ticket))
        assertFalse(state.begin(ticket.copy(recordingId = 5)))
        assertNull(state.take(DocumentExportTicket.Kind.RECORDING_PARTS))
        assertEquals(ticket, state.peek())
        state.clear()
        assertNull(state.peek())
        assertTrue(state.begin(ticket.copy(recordingId = 5)))
    }

    @Test fun corruptedOrUnsupportedRequestsAreNotExecuted() {
        assertNull(DocumentExportTicket.decode(null))
        assertNull(DocumentExportTicket.decode("not-json"))
        val ticket = DocumentExportTicket(DocumentExportTicket.Kind.RECORDING, setOf(1), recordingId = 4)
        assertNull(DocumentExportTicket.decode(ticket.encode().replace("\"version\":1", "\"version\":999")))
        assertNull(DocumentExportTicket.decode(ticket.copy(profileIds = emptySet()).encode()))
        assertNull(DocumentExportTicket.decode(ticket.copy(recordingId = -1).encode()))
        assertNull(DocumentExportTicket.decode(ticket.copy(target = "file:///private").encode()))
    }
}
