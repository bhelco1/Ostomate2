package com.ostomate.app.data.db

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class Migration2To3Test {
    private val schemasDir =
        requireNotNull(getenv("OSTOMATE_SCHEMAS_PATH")?.toKString()) {
            "OSTOMATE_SCHEMAS_PATH env var not set (see shared/build.gradle.kts)"
        }

    private val dbFile = NSTemporaryDirectory() + "migration-2-3-test.db"

    private val helper =
        MigrationTestHelper(
            schemaDirectoryPath = schemasDir,
            fileName = dbFile,
            driver = BundledSQLiteDriver(),
            databaseClass = OstomateDatabase::class,
        )

    @AfterTest
    fun tearDown() {
        helper.finished()
        FileSystem.SYSTEM.delete(dbFile.toPath(), mustExist = false)
    }

    @Test
    fun migrate2To3_addsColorIndexColumnWithNullDefault() {
        val v2 = helper.createDatabase(version = 2)
        v2.execSQL(
            "INSERT INTO supply_types (name, kind, boxSize, warnThresholdDays, onHand, sortOrder, archived) " +
                "VALUES ('Bag', 'BAG', 30, 14, 5, 0, 0), ('Flange', 'FLANGE', 5, 14, 2, 1, 0)",
        )
        v2.close()

        val v3 = helper.runMigrationsAndValidate(version = 3, migrations = listOf(MIGRATION_2_3))

        v3.query("SELECT name, colorIndex FROM supply_types ORDER BY sortOrder") { stmt ->
            assertTrue(stmt.step())
            assertEquals("Bag", stmt.getText(0))
            assertTrue(stmt.isNull(1), "colorIndex should be NULL for migrated Bag row")

            assertTrue(stmt.step())
            assertEquals("Flange", stmt.getText(0))
            assertTrue(stmt.isNull(1), "colorIndex should be NULL for migrated Flange row")
        }
        v3.close()
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
