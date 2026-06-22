package com.ostomate.app.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Database-agnostic migration assertions. Each scenario opens a fresh in-memory
 * connection from the supplied [SQLiteDriver] (bundled native on iOS, framework +
 * Robolectric on the JVM host), builds the old schema by hand, runs the migration
 * directly, and validates the result.
 *
 * Trade-off vs Room's MigrationTestHelper (instrumentation-only on Android): this
 * validates the data transformation, not that the resulting schema matches the
 * exported JSON. Schema-JSON drift is caught at build time by Room's exportSchema.
 */
object MigrationScenarios {
    fun migrate1To2SeedsCatalogAndRemapsSpikeRows(driver: SQLiteDriver) {
        val connection = driver.open(":memory:")
        try {
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
        } finally {
            connection.close()
        }
    }

    fun migrate2To3AddsColorIndexColumnWithNullDefault(driver: SQLiteDriver) {
        val connection = driver.open(":memory:")
        try {
            // v2 supply_types schema (pre-colorIndex).
            connection.execSQL(
                "CREATE TABLE supply_types (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "name TEXT NOT NULL, kind TEXT NOT NULL, " +
                    "boxSize INTEGER NOT NULL, warnThresholdDays INTEGER NOT NULL, " +
                    "onHand INTEGER NOT NULL, sortOrder INTEGER NOT NULL, archived INTEGER NOT NULL)",
            )
            connection.execSQL(
                "INSERT INTO supply_types (name, kind, boxSize, warnThresholdDays, onHand, sortOrder, archived) " +
                    "VALUES ('Bag', 'BAG', 30, 14, 5, 0, 0), ('Flange', 'FLANGE', 5, 14, 2, 1, 0)",
            )

            MIGRATION_2_3.migrate(connection)

            connection.query("SELECT name, colorIndex FROM supply_types ORDER BY sortOrder") { stmt ->
                assertTrue(stmt.step())
                assertEquals("Bag", stmt.getText(0))
                assertTrue(stmt.isNull(1), "colorIndex should be NULL for migrated Bag row")

                assertTrue(stmt.step())
                assertEquals("Flange", stmt.getText(0))
                assertTrue(stmt.isNull(1), "colorIndex should be NULL for migrated Flange row")
            }
        } finally {
            connection.close()
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
