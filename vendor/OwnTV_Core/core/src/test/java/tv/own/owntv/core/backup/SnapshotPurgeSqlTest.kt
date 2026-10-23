package tv.own.owntv.core.backup

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.model.MediaType

/** Execute the production DAO annotations on SQLite; never test a hand-copied DELETE. */
class SnapshotPurgeSqlTest {
    private val tables = mapOf("order" to "content_order", "member" to "custom_category_members")

    private fun database(test: (Connection) -> Unit) {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().use { q ->
                q.execute("PRAGMA foreign_keys=ON")
                q.execute("CREATE TABLE profiles(id INTEGER PRIMARY KEY)")
                q.execute("INSERT INTO profiles VALUES (1),(2)")
                for (table in listOf("channels", "movies", "series")) q.execute("CREATE TABLE $table(id INTEGER PRIMARY KEY, sourceId INTEGER)")
                // Target tables and indexes use the current exported Room schema.
                val entities = JSONObject(File("schemas/tv.own.owntv.core.database.OwnTVDatabase/52.json").readText())
                    .getJSONObject("database").getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    if (table !in tables.values) continue
                    q.execute(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indexes = entity.getJSONArray("indices")
                    for (j in 0 until indexes.length()) q.execute(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            test(db)
        }
    }

    private fun insert(db: Connection, kind: String, profile: Long, type: MediaType, id: Long, folder: String = "folder") {
        db.prepareStatement("INSERT INTO ${tables.getValue(kind)} (profileId,mediaType,contextKey,itemId,position) VALUES (?,?,?,?,0)").use {
            it.setLong(1, profile); it.setString(2, type.name); it.setString(3, folder); it.setLong(4, id)
            it.executeUpdate()
        }
    }

    private fun purge(db: Connection, rows: List<SnapshotRow>): Int {
        var count = 0
        for (b in snapshotPurgeBatches(rows, tables.keys)) {
            val dao = if (b.kind == "order") "ContentOrderDao" else "CustomCategoryDao"
            val source = File("src/main/java/tv/own/owntv/core/database/dao/$dao.kt").readText()
            val annotation = source.substringBefore("suspend fun purgeSnapshotOrphans(").substringAfterLast("@Query(")
            val sql = Regex("\"([^\"]*)\"").findAll(annotation).joinToString("") { it.groupValues[1] }
            assertTrue("production annotation not found", sql.startsWith("DELETE FROM ${tables.getValue(b.kind)} "))
            val args = ArrayList<Any>()
            val bound = Regex(":(profileId|type|itemIds)\\b").replace(sql) { match ->
                when (match.groupValues[1]) {
                    "profileId" -> { args += b.profileId; "?" }
                    "type" -> { args += b.type.name; "?" }
                    else -> { args.addAll(b.itemIds); b.itemIds.joinToString(",") { "?" } }
                }
            }
            assertTrue("old SQLite binding ceiling", args.size <= 999)
            db.prepareStatement(bound).use { statement ->
                args.forEachIndexed { i, arg -> statement.setObject(i + 1, arg) }
                count += statement.executeUpdate()
            }
        }
        return count
    }

    private fun count(db: Connection, kind: String): Int = db.createStatement().use { q ->
        q.executeQuery("SELECT COUNT(*) FROM ${tables.getValue(kind)}").use { r -> r.next(); r.getInt(1) }
    }

    @Test fun `purge retains valid items other profiles other types and uncaptured orphans`() = database { db ->
        db.createStatement().use { it.execute("INSERT INTO channels VALUES (10,1),(20,2)") }
        for (kind in tables.keys) {
            insert(db, kind, 1, MediaType.LIVE, 10) // valid current source
            insert(db, kind, 1, MediaType.LIVE, 20) // valid other source
            insert(db, kind, 1, MediaType.LIVE, 99, "first")
            insert(db, kind, 1, MediaType.LIVE, 99, "second") // same captured orphan in two folders
            insert(db, kind, 1, MediaType.LIVE, 100) // orphan outside the snapshot
            insert(db, kind, 2, MediaType.LIVE, 99) // another profile
            insert(db, kind, 1, MediaType.MOVIE, 99) // same numeric id in another media type
            assertEquals(2, purge(db, listOf(10L, 20L, 99L, 99L).map { SnapshotRow(kind, 1, MediaType.LIVE, it) }))
            assertEquals(5, count(db, kind))
        }
    }

    @Test fun `media existence is checked in its own content table`() = database { db ->
        db.createStatement().use { it.execute("INSERT INTO movies VALUES (42,1)") }
        for (kind in tables.keys) {
            val rows = listOf(MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES).map { type ->
                insert(db, kind, 1, type, 42)
                SnapshotRow(kind, 1, type, 42)
            }
            assertEquals(2, purge(db, rows))
            assertEquals(1, count(db, kind))
        }
    }

    @Test fun `more than 999 snapshot ids delete in bounded batches and empty snapshot is harmless`() = database { db ->
        val rows = tables.keys.flatMap { kind -> (1L..2_005L).map { id ->
            insert(db, kind, 1, MediaType.LIVE, id)
            SnapshotRow(kind, 1, MediaType.LIVE, id)
        } }
        assertEquals(0, purge(db, emptyList()))
        assertEquals(2_005, count(db, "member"))
        assertEquals(4_010, purge(db, rows + rows))
        assertEquals(0, count(db, "member")); assertEquals(0, count(db, "order"))
    }
}
