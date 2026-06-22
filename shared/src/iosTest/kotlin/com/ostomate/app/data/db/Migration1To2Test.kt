package com.ostomate.app.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Drives MIGRATION_1_2 directly against an in-memory SQLite connection. This runs
 * on both the JVM host (gated on PRs) and the iOS simulator, unlike Room's
 * MigrationTestHelper, whose Android artifact is instrumentation-only.
 *
 * Trade-off: this validates the migration's data transformation, not that the
 * resulting schema matches the exported v2 JSON (MigrationTestHelper's extra
 * check). Schema-JSON drift is caught at build time by Room's `exportSchema`.
 */
class Migration1To2Test {
    // Single in-memory DB held open across the migration (same connection = same DB).
    private val connection: SQLiteConnection = BundledSQLiteDriver().open(":memory:")

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    @Test
    fun migrate1To2_seedsCatalogAndRemapsSpikeRows() {
        // v1 (spike) schema: change_events(id, supply TEXT, timestampMillis).
        connection.execSQL(
            "CREATE TABLE change_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "supply TEXT NOT NULL, timestampMillis INTEGER NOT NULL)",
        )
        connection.execSQL("INSERT INTO change_events (supply, timestampMillis) VALUES ('bag', 1000)")
        connection.execSQL("INSERT INTO change_events (supply, timestampMillis) VALUES ('flange', 2000)")

        MIGRATION_1_2.migrate(connection)

        // The two v1 defaults are seeded with v1's constants (box 30/5, 14-day warning).
        val supplies =
            connection.query(
                "SELECT name, kind, boxSize, warnThresholdDays FROM supply_types ORDER BY sortOrder",
            ) { stmt ->
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
        connection.query(
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
