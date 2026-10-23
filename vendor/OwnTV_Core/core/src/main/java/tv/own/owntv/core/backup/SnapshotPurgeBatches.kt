package tv.own.owntv.core.backup

import tv.own.owntv.core.model.MediaType

/** Only the old local ids captured by a successful source-sync snapshot may be purged. */
internal data class SnapshotRow(val kind: String, val profileId: Long, val type: MediaType, val itemId: Long)

internal data class SnapshotPurgeBatch(val kind: String, val profileId: Long, val type: MediaType, val itemIds: List<Long>)

/** Deduplicate per operation/profile/type, leaving room for the other bindings on SQLite's 999 limit. */
internal fun snapshotPurgeBatches(rows: List<SnapshotRow>, kinds: Set<String>, chunk: Int = 900): List<SnapshotPurgeBatch> {
    require(chunk in 1..900)
    return rows.asSequence().filter { it.kind in kinds }
        .groupBy { Triple(it.kind, it.profileId, it.type) }
        .flatMap { (key, group) ->
            group.map { it.itemId }.distinct().chunked(chunk)
                .map { SnapshotPurgeBatch(key.first, key.second, key.third, it) }
        }
}
