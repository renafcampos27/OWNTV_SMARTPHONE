package tv.own.owntv.core.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Runs the production migration methods against SQLite, not a hand-copied SQL approximation. */
class PlaybackDatabaseMigrationTest {
    private val currentVersion = OwnTVDatabase.ALL_MIGRATIONS.maxOf { it.endVersion }

    private fun schema(version: Int, upstreamMobile: Boolean = false): JSONObject = JSONObject(
        File(if (upstreamMobile) "schemas/upstream-mobile/$version.json" else "schemas/tv.own.owntv.core.database.OwnTVDatabase/$version.json").readText(),
    ).getJSONObject("database")

    private fun bootstrap(connection: Connection, version: Int, upstreamMobile: Boolean = false) {
        val entities = schema(version, upstreamMobile).getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            connection.createStatement().use { it.execute(entity.getString("createSql").replace("\${TABLE_NAME}", table)) }
            val indexes = entity.optJSONArray("indices") ?: continue
            for (n in 0 until indexes.length()) {
                connection.createStatement().use { it.execute(indexes.getJSONObject(n).getString("createSql").replace("\${TABLE_NAME}", table)) }
            }
        }
    }

    /** The migration driver only needs prepare/step/getText/close; unexpected use fails loudly. */
    private fun driver(connection: Connection): SQLiteConnection = Proxy.newProxyInstance(
        SQLiteConnection::class.java.classLoader, arrayOf(SQLiteConnection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "prepare" -> {
                val stmt = connection.prepareStatement(args!![0] as String)
                var executed = false
                var rows: ResultSet? = null
                Proxy.newProxyInstance(SQLiteStatement::class.java.classLoader, arrayOf(SQLiteStatement::class.java)) { _, m, a ->
                    when (m.name) {
                        "step" -> { if (!executed) { executed = true; if (stmt.execute()) rows = stmt.resultSet }; rows?.next() ?: false }
                        "getColumnNames" -> (1..stmt.metaData.columnCount).map { stmt.metaData.getColumnName(it) }
                        "getColumnCount" -> stmt.metaData.columnCount
                        "getColumnName" -> stmt.metaData.getColumnName((a!![0] as Int) + 1)
                        "getText" -> rows!!.getString((a!![0] as Int) + 1)
                        "getLong" -> rows!!.getLong((a!![0] as Int) + 1)
                        "getInt" -> rows!!.getInt((a!![0] as Int) + 1)
                        "bindText" -> { stmt.setString(a!![0] as Int, a[1] as String); null }
                        "close" -> { stmt.close(); null }
                        else -> error("Unsupported migration statement method: ${m.name}")
                    }
                }
            }
            "close" -> null
            else -> error("Unsupported migration connection method: ${method.name}")
        }
    } as SQLiteConnection

    private fun verifyCurrentSchema(connection: Connection, targetVersion: Int = currentVersion) {
        val entities = schema(targetVersion).getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            val actual = mutableMapOf<String, Pair<String, Boolean>>()
            connection.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA table_info(`$table`)").use { rows ->
                    while (rows.next()) actual[rows.getString("name")] = rows.getString("type") to (rows.getInt("notnull") != 0)
                }
            }
            val fields = entity.getJSONArray("fields")
            val expected = (0 until fields.length()).associate { n ->
                val f = fields.getJSONObject(n)
                f.getString("columnName") to (f.getString("affinity") to f.optBoolean("notNull", false))
            }
            if (entity.has("ftsOptions")) assertEquals("FTS columns in $table", expected.keys, actual.keys)
            else assertEquals("columns in $table", expected, actual)
            val indexes = entity.optJSONArray("indices") ?: continue
            for (n in 0 until indexes.length()) {
                val name = indexes.getJSONObject(n).getString("name")
                connection.prepareStatement("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name=?").use { q ->
                    q.setString(1, name)
                    q.executeQuery().use { rows -> assertTrue(rows.next()); assertEquals(name, 1, rows.getInt(1)) }
                }
            }
        }
    }

    @Test fun `failure stage migration preserves legacy unknowns and is idempotent`() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            bootstrap(connection, 48)
            connection.createStatement().use { q ->
                q.execute("INSERT INTO profiles (id,name,avatarColor,avatarId,isKids,createdAt) VALUES (1,'Primary',1,1,0,1)")
                q.execute("INSERT INTO recordings (id,profileId,sourceId,channelId,channelName,streamUrl,title," +
                    "programmeStartMs,programmeStopMs,startMs,stopMs,status,failure,filePath,bytes,createdAt,updatedAt) " +
                    "VALUES (9,1,7,42,'Channel','https://example.invalid/live','Programme',100,200,90,210," +
                    "'PARTIAL','NO_SPACE','/original.ts',12345,1,2)")
            }
            val driver = driver(connection)
            OwnTVDatabase.MIGRATION_48_49.migrate(driver)
            OwnTVDatabase.MIGRATION_49_50.migrate(driver)
            OwnTVDatabase.MIGRATION_48_49.migrate(driver)
            OwnTVDatabase.MIGRATION_49_50.migrate(driver)
            verifyCurrentSchema(connection, 50)
            connection.createStatement().use { q ->
                q.executeQuery("SELECT filePath,bytes,failure,captureFailure,finalizationFailure FROM recordings WHERE id=9").use { r ->
                    assertTrue(r.next())
                    assertEquals("/original.ts", r.getString(1)); assertEquals(12345L, r.getLong(2))
                    assertEquals("NO_SPACE", r.getString(3)); assertNull(r.getString(4)); assertNull(r.getString(5))
                }
                q.execute("UPDATE recordings SET captureFailure='NETWORK', finalizationFailure='NO_SPACE' WHERE id=9")
                OwnTVDatabase.MIGRATION_48_49.migrate(driver)
            OwnTVDatabase.MIGRATION_49_50.migrate(driver)
                q.executeQuery("SELECT captureFailure,finalizationFailure FROM recordings WHERE id=9").use { r ->
                    assertTrue(r.next()); assertEquals("NETWORK", r.getString(1)); assertEquals("NO_SPACE", r.getString(2))
                }
            }
        }
    }

    @Test fun `versions 43 through 47 migrate to the complete current schema preserving settings`() {
        Class.forName("org.sqlite.JDBC")
        for (version in 43..47) DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            bootstrap(connection, version)
            connection.createStatement().use {
                it.execute("INSERT INTO profiles (id,name,avatarColor,avatarId,isKids,createdAt) VALUES (1,'Primary',1,1,0,1)")
                it.execute("INSERT INTO playback_prefs (profileId,contentKey,zoomMode,volumeBoost,audioDelayMs,updatedAt) VALUES (1,'7:LIVE:rtp','FIT',125,175,123)")
                if (version >= 44) it.execute("INSERT INTO playback_quirks (contentKey,sourceId,mediaType,enginePin,audioOnly,audioDelayMs,updatedAt) VALUES ('7:LIVE:rtp',7,'LIVE','EXO',1,100,123)")
            }
            connection.autoCommit = false
            val driver = driver(connection)
            OwnTVDatabase.ALL_MIGRATIONS.filter { it.startVersion >= version }.forEach { it.migrate(driver) }
            connection.commit()
            verifyCurrentSchema(connection)
            connection.createStatement().use { q ->
                q.executeQuery("SELECT zoomMode,volumeBoost,audioDelayMs,updatedAt FROM playback_prefs").use {
                    assertTrue(it.next()); assertEquals("FIT", it.getString(1)); assertEquals(125, it.getInt(2)); assertEquals(175, it.getInt(3)); assertEquals(123L, it.getLong(4))
                }
                if (version >= 44) q.executeQuery("SELECT enginePin,audioOnly,audioDelayMs FROM playback_quirks").use {
                    assertTrue(it.next()); assertEquals("EXO", it.getString(1)); assertEquals(1, it.getInt(2)); assertEquals(100, it.getInt(3))
                }
                q.executeQuery("PRAGMA integrity_check").use { assertTrue(it.next()); assertEquals("ok", it.getString(1)) }
            }
        }
    }

    @Test fun `recording migration preserves files and permits separate recovery attempts`() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            bootstrap(connection, 47)
            connection.createStatement().use { q ->
                q.execute("INSERT INTO profiles (id,name,avatarColor,avatarId,isKids,createdAt) VALUES (1,'Primary',1,1,0,1)")
                q.execute("INSERT INTO recordings (id,profileId,sourceId,channelId,channelName,streamUrl,title," +
                    "programmeStartMs,programmeStopMs,startMs,stopMs,status,failure,filePath,bytes,createdAt,updatedAt) " +
                    "VALUES (9,1,7,42,'Channel','https://example.invalid/live','Programme',100,200,90,210," +
                    "'COMPLETED','NONE','/original.ts',12345,1,2)")
            }
            val driver = driver(connection)
            OwnTVDatabase.MIGRATION_47_48.migrate(driver)
            OwnTVDatabase.MIGRATION_48_49.migrate(driver)
            OwnTVDatabase.MIGRATION_49_50.migrate(driver)
            verifyCurrentSchema(connection, 50)
            connection.createStatement().use { q ->
                q.executeQuery("SELECT filePath,bytes,status,capturedDurationMs,missingDurationMs,gapCount,hlsCheckpoint,recoveryOfId,recoveryAttempt FROM recordings WHERE id=9").use { r ->
                    assertTrue(r.next()); assertEquals("/original.ts", r.getString(1)); assertEquals(12345L, r.getLong(2))
                    assertEquals("COMPLETED", r.getString(3)); assertEquals(0L, r.getLong(4)); assertEquals(0L, r.getLong(5))
                    assertEquals(0, r.getInt(6)); assertNull(r.getString(7)); assertNull(r.getObject(8)); assertEquals(0, r.getInt(9))
                }
                val copy = "INSERT INTO recordings (id,profileId,sourceId,channelId,channelName,streamUrl,title," +
                    "programmeStartMs,programmeStopMs,startMs,stopMs,status,failure,filePath,bytes,createdAt,updatedAt,recoveryOfId,recoveryAttempt) " +
                    "SELECT 10,profileId,sourceId,channelId,channelName,streamUrl,title,programmeStartMs,programmeStopMs," +
                    "startMs,stopMs,'SCHEDULED','NONE','/recovery.ts',0,createdAt,updatedAt,9,"
                try {
                    q.execute(copy + "0 FROM recordings WHERE id=9")
                    fail("The normal programme still has to be unique")
                } catch (_: java.sql.SQLException) {
                    // Attempt zero collides; the existing row must remain intact.
                }
                q.execute(copy + "1 FROM recordings WHERE id=9")
                q.execute("UPDATE recordings SET capturedDurationMs=15000,missingDurationMs=2000,gapCount=1,hlsCheckpoint='private' WHERE id=9")
            }
            // Defensive re-entry preserves new capture metadata as well as original files.
            OwnTVDatabase.MIGRATION_47_48.migrate(driver)
            OwnTVDatabase.MIGRATION_48_49.migrate(driver)
            OwnTVDatabase.MIGRATION_49_50.migrate(driver)
            connection.createStatement().use { q ->
                q.executeQuery("SELECT filePath,bytes,capturedDurationMs,missingDurationMs,gapCount,hlsCheckpoint FROM recordings WHERE id=9").use { r ->
                    assertTrue(r.next()); assertEquals("/original.ts", r.getString(1)); assertEquals(12345L, r.getLong(2))
                    assertEquals(15000L, r.getLong(3)); assertEquals(2000L, r.getLong(4)); assertEquals(1, r.getInt(5)); assertEquals("private", r.getString(6))
                }
                q.executeQuery("SELECT recoveryOfId,recoveryAttempt,filePath FROM recordings WHERE id=10").use { r ->
                    assertTrue(r.next()); assertEquals(9L, r.getLong(1)); assertEquals(1, r.getInt(2)); assertEquals("/recovery.ts", r.getString(3))
                }
                q.executeQuery("SELECT COUNT(*) FROM recordings").use { r -> assertTrue(r.next()); assertEquals(2, r.getInt(1)) }
                q.executeQuery("PRAGMA integrity_check").use { r -> assertTrue(r.next()); assertEquals("ok", r.getString(1)) }
            }
        }
    }

    @Test fun `reserve migration preserves every legacy source choice and inherited defaults`() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            bootstrap(connection, 46)
            val modes = listOf("LOW", "BALANCED", "STABLE", "CUSTOM", null)
            connection.prepareStatement(
                "INSERT INTO sources (id,name,type,url,syncLive,syncMovies,syncSeries,hlsSupported,preferHls," +
                    "livePrerollSecs,liveLatencyMode,liveLatencyCustomSecs,maxConnections,maxConnectionsProbedAt,createdAt) " +
                    "VALUES (?, 'Migration test', 'M3U', 'https://example.invalid/list',1,1,1,0,0,-1,?,?,0,0,123)",
            ).use { insert ->
                modes.forEachIndexed { index, mode ->
                    insert.setLong(1, index + 1L)
                    insert.setString(2, mode)
                    insert.setInt(3, if (mode == null) -1 else 23)
                    insert.executeUpdate()
                }
            }
            val driver = driver(connection)
            OwnTVDatabase.MIGRATION_46_47.migrate(driver)
            OwnTVDatabase.MIGRATION_47_48.migrate(driver)
            OwnTVDatabase.MIGRATION_48_49.migrate(driver)
            OwnTVDatabase.MIGRATION_49_50.migrate(driver)
            verifyCurrentSchema(connection, 50)
            connection.createStatement().use { query ->
                query.executeQuery("SELECT liveLatencyMode,liveLatencyCustomSecs,liveReserveMode,liveReserveCustomSecs,liveReserveExtraSecs FROM sources ORDER BY id").use { rows ->
                    for (mode in modes) {
                        assertTrue(rows.next())
                        assertEquals(mode, rows.getString(1))
                        assertEquals(if (mode == null) -1 else 23, rows.getInt(2))
                        assertEquals(mode, rows.getString(3))
                        assertEquals(if (mode == null) -1 else 23, rows.getInt(4))
                        assertEquals(if (mode == null) -1 else 2, rows.getInt(5))
                    }
                    assertFalse(rows.next())
                }
            }
            // Defensive re-entry must not recouple a reserve the user has since changed.
            connection.createStatement().use { it.execute("UPDATE sources SET liveReserveMode='STABLE',liveReserveCustomSecs=8,liveReserveExtraSecs=4 WHERE id=4") }
            OwnTVDatabase.MIGRATION_46_47.migrate(driver)
            connection.createStatement().use { query ->
                query.executeQuery("SELECT liveLatencyMode,liveReserveMode,liveReserveCustomSecs,liveReserveExtraSecs FROM sources WHERE id=4").use { rows ->
                    assertTrue(rows.next()); assertEquals("CUSTOM", rows.getString(1))
                    assertEquals("STABLE", rows.getString(2)); assertEquals(8, rows.getInt(3)); assertEquals(4, rows.getInt(4))
                }
            }
        }
    }
    @Test fun `archive pause migration is additive idempotent and preserves retained capture`() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            bootstrap(connection, 49)
            connection.createStatement().use { q ->
                q.execute("INSERT INTO profiles (id,name,avatarColor,avatarId,isKids,createdAt) VALUES (1,'Primary',1,1,0,1)")
                q.execute("INSERT INTO recordings (id,profileId,sourceId,channelId,channelName,streamUrl,title,programmeStartMs,programmeStopMs,startMs,stopMs,status,failure,filePath,bytes,capturedDurationMs,missingDurationMs,gapCount,recoveryAttempt,createdAt,updatedAt) VALUES (1,1,2,3,'SIC','','Programme',0,60000,120000,240000,'PARTIAL','NONE','/retained.ts',1234,6000,0,0,0,1,1)")
            }
            val driver = driver(connection)
            OwnTVDatabase.MIGRATION_49_50.migrate(driver)
            verifyCurrentSchema(connection, 50)
            connection.createStatement().use { q ->
                q.executeQuery("SELECT archivePaused FROM recordings").use { r -> assertTrue(r.next()); assertEquals(0, r.getInt(1)) }
                q.execute("UPDATE recordings SET archivePaused=1")
            }
            OwnTVDatabase.MIGRATION_49_50.migrate(driver)
            connection.createStatement().use { q ->
                q.executeQuery("SELECT archivePaused,bytes,capturedDurationMs,filePath FROM recordings").use { r ->
                    assertTrue(r.next()); assertEquals(1, r.getInt(1)); assertEquals(1234L, r.getLong(2))
                    assertEquals(6000L, r.getLong(3)); assertEquals("/retained.ts", r.getString(4))
                }
            }
        }
    }


    @Test fun `mobile 47 and custom 50 migrate with EPG metadata and reminders preserved`() {
        Class.forName("org.sqlite.JDBC")
        for (upstreamMobile in listOf(true, false)) DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val version = if (upstreamMobile) 47 else 50
            bootstrap(connection, version, upstreamMobile)
            connection.createStatement().use { q ->
                q.execute("INSERT INTO profiles (id,name,avatarColor,avatarId,isKids,createdAt) VALUES (1,'Primary',1,1,0,1)")
                q.execute("INSERT INTO epg_programmes (id,sourceId,epgChannelId,title,description,startMs,stopMs,contentHash) VALUES (7,9,'sic','Original','Description',1000,2000,123)")
                if (upstreamMobile) {
                    q.execute("UPDATE epg_programmes SET categories='News',year=2026,rating='12',lengthMin=30,episode='S1 E2' WHERE id=7")
                    q.execute("INSERT INTO programme_reminders (profileId,channelId,channelName,title,startMs,stopMs,leadMinutes,createdAt) VALUES (1,9,'SIC','Original',1000,2000,0,1)")
                }
            }
            val driver = driver(connection)
            OwnTVDatabase.ALL_MIGRATIONS.filter { it.startVersion >= version }.forEach { it.migrate(driver) }
            verifyCurrentSchema(connection)
            // Re-enter the union bridge to prove it does not overwrite restored metadata.
            OwnTVDatabase.MIGRATION_50_51.migrate(driver)
            connection.createStatement().use { q ->
                q.executeQuery("SELECT title,description,contentHash,categories,year,rating,lengthMin,episode FROM epg_programmes WHERE id=7").use { r ->
                    assertTrue(r.next()); assertEquals("Original",r.getString(1)); assertEquals("Description",r.getString(2)); assertEquals(123,r.getInt(3))
                    if (upstreamMobile) { assertEquals("News",r.getString(4)); assertEquals(2026,r.getInt(5)); assertEquals("12",r.getString(6)); assertEquals(30,r.getInt(7)); assertEquals("S1 E2",r.getString(8)) }
                    else { for (column in 4..8) assertNull(r.getObject(column)) }
                }
                q.executeQuery("SELECT COUNT(*) FROM programme_reminders").use { r -> assertTrue(r.next()); assertEquals(if (upstreamMobile) 1 else 0,r.getInt(1)) }
                q.executeQuery("PRAGMA integrity_check").use { r -> assertTrue(r.next()); assertEquals("ok",r.getString(1)) }
            }
        }
    }

}
