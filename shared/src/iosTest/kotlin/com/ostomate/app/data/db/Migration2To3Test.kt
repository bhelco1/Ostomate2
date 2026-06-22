package com.ostomate.app.data.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives MIGRATION_2_3 directly against an in-memory SQLite connection (runs on
 * JVM host + iOS simulator). See Migration1To2Test for the trade-off vs
 * Room's MigrationTestHelper.
 */
class Migration2To3Test {
    private val connection: SQLiteConnection = BundledSQLiteDriver().open(":memory:")

    @AfterTest
    fun tearDown() {
        connection.close()
    }

    @Test
    fun migrate2To3_addsColorIndexColumnWithNullDefault() {
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
