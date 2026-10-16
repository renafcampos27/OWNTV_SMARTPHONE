package tv.own.owntv.mobile.backup

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persist only operation identity and non-secret choices, never passwords or source credentials. */
internal data class DocumentExportTicket(
    val kind: Kind,
    val profileIds: Set<Long>,
    val sections: Set<String> = emptySet(),
    val recordingId: Long? = null,
    val encrypted: Boolean = false,
    val target: String? = null,
    val nonce: String = UUID.randomUUID().toString(),
) {
    enum class Kind { BACKUP, RECORDING, RECORDING_PARTS }

    fun encode(): String = JSONObject().put("version", 1).put("nonce", nonce).put("kind", kind.name)
        .put("profiles", JSONArray(profileIds.toList())).put("sections", JSONArray(sections.toList()))
        .put("recording", recordingId).put("protected", encrypted).put("target", target).toString()

    companion object {
        fun decode(raw: String?): DocumentExportTicket? = runCatching {
            val json = JSONObject(raw ?: return null)
            require(json.getInt("version") == 1)
            val nonce = json.getString("nonce").also(UUID::fromString)
            val kind = Kind.valueOf(json.getString("kind"))
            val profiles = json.getJSONArray("profiles")
            require(profiles.length() in 1..256)
            val ids = (0 until profiles.length()).map { profiles.getLong(it).also { id -> require(id >= 0) } }.toSet()
            val sections = json.getJSONArray("sections")
            require(sections.length() <= 32)
            val names = (0 until sections.length()).map { sections.getString(it) }.toSet()
            val recording = if (json.has("recording")) json.getLong("recording") else null
            val target = if (json.has("target")) json.getString("target").also { require(it.startsWith("content://")) } else null
            if (kind == Kind.BACKUP) require(names.isNotEmpty() && recording == null)
            else require(ids.size == 1 && recording != null && recording > 0 && names.isEmpty())
            DocumentExportTicket(kind, ids, names, recording, json.getBoolean("protected"), target, nonce)
        }.getOrNull()
    }
}

/** Same holder after recreation; taking a ticket consumes it exactly once. */
internal class DocumentExportState(private val read: () -> String?, private val write: (String?) -> Unit) {
    fun peek(): DocumentExportTicket? = DocumentExportTicket.decode(read())
    fun begin(ticket: DocumentExportTicket): Boolean {
        if (peek() != null) return false
        write(ticket.encode())
        return true
    }
    fun target(uri: String): DocumentExportTicket? = peek()?.copy(target = uri)?.also { write(it.encode()) }
    fun take(kind: DocumentExportTicket.Kind): DocumentExportTicket? {
        val ticket = peek()?.takeIf { it.kind == kind } ?: return null
        write(null)
        return ticket
    }
    fun clear() = write(null)
}
