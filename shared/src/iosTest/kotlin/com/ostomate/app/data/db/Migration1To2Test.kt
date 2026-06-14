package com.ostimate.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.posix.getenv
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class Migration1To2Test {
    // Set on KotlinNativeSimulatorTest tasks in shared/build.gradle.kts — the
    // schema JSONs live in the source tree, not the simulator sandbox.
    private val schemasDir =
        requireNotNull(getenv("OSTIMATE_SCHEMAS_PATH")?.toKString()) {
            "OSTIMATE_SCHEMAS_PATH env var not set (see shared/build.gradle.kts)"
        }

    private val dbFile = NSTemporaryDirectory() + "migration-1-2-test.db"

    private val helper =
        MigrationTestHelper(
            schemaDirectoryPath = schemasDir,
            fileName = dbFile,
            driver = BundledSQLiteDriver(),
            databaseClass = OstimateDatabase::class,
        )

    @AfterTest
    fun tearDown() {
        helper.finished()
        FileSystem.SYSTEM.delete(dbFile.toPath(), mustExist = false)
    }

    @Test
    fun migrate1To2_seedsCatalogAndRemapsSpikeRows() {
        val v1 = helper.createDatabase(version = 1)
        v1.execSQL("INSERT INTO change_events (supply, timestampMillis) VALUES ('bag', 1000)")
        v1.execSQL("INSERT INTO change_events (supply, timestampMillis) VALUES ('flange', 2000)")
        v1.close()

        val v2 = helper.runMigrationsAndValidate(version = 2, migrations = listOf(MIGRATION_1_2))

        // The two v1 defaults are seeded with v1's constants (box 30/5, 14-day warning).
        val supplies =
            v2.query("SELECT name, kind, boxSize, warnThresholdDays FROM supply_types ORDER BY sortOrder") { stmt ->
                buildList {
                    while (stmt.step()) {
                        add(listOf(stmt.getText(0), stmt.getText(1), stmt.getLong(2), stmt.getLong(3)))
                    }
                }
            }
        assertEquals(
            listOf(
                listOf("Bag", "BAG", 30L, 14L),
                listOf("Flange", "FLANGE", 5L, 14L),
            ),
            supplies,
        )

        // Spike rows now reference the catalog; createdAt backfilled, never edited.
        v2.query(
            """
            SELECT s.kind, e.timestampMillis, e.createdAtMillis, e.editedAtMillis IS NULL
            FROM change_events e JOIN supply_types s ON s.id = e.supplyTypeId
            ORDER BY e.timestampMillis
            """,
        ) { stmt ->
            assertTrue(stmt.step())
            assertEquals("BAG", stmt.getText(0))
            assertEquals(1000L, stmt.getLong(1))
            assertEquals(1000L, stmt.getLong(2))
            assertEquals(1L, stmt.getLong(3))

            assertTrue(stmt.step())
            assertEquals("FLANGE", stmt.getText(0))
            assertEquals(2000L, stmt.getLong(1))
            assertEquals(2000L, stmt.getLong(2))

            assertFalse(stmt.step())
        }
        v2.close()
    }

    private inline fun <T> SQLiteConnection.query(
        sql: String,
        block: (SQLiteStatement) -> T,
    ): T {
        val statement = prepare(sql)
        try {
            return block(statement)
        } finally {
            statement.close()
        }
    }
}
