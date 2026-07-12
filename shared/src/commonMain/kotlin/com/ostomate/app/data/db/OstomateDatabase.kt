package com.ostomate.app.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [SupplyTypeEntity::class, ChangeEventEntity::class],
    version = 3,
    exportSchema = true,
)
@ConstructedBy(OstomateDatabaseConstructor::class)
abstract class OstomateDatabase : RoomDatabase() {
    abstract fun supplyTypeDao(): SupplyTypeDao

    abstract fun changeEventDao(): ChangeEventDao

    abstract fun backupDao(): BackupDao

    companion object {
        const val FILE_NAME = "ostomate_database.db"

        /** The @Database version above; stamped into backups so a restore can reject a mismatch. */
        const val SCHEMA_VERSION = 3
    }
}

// Room's KSP compiler generates the per-platform actuals for this constructor.
@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object OstomateDatabaseConstructor : RoomDatabaseConstructor<OstomateDatabase> {
    override fun initialize(): OstomateDatabase
}

/**
 * Seeds the two default supplies that mirror v1's hardcoded Bag/Flange
 * (box sizes 30/5 and 14-day warning threshold are v1's constants).
 * Used by the fresh-install onCreate callback below.
 */
internal val SEED_DEFAULT_SUPPLIES_SQL =
    """
    INSERT INTO supply_types (name, kind, boxSize, warnThresholdDays, onHand, sortOrder, archived)
    VALUES ('Bag', 'BAG', 30, 14, 0, 0, 0), ('Flange', 'FLANGE', 5, 14, 0, 1, 0)
    """.trimIndent()

/** v3: adds colorIndex (nullable INTEGER) to supply_types for custom supply palette selection. */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `supply_types` ADD COLUMN `colorIndex` INTEGER")
        }
    }

// driver defaults to the bundled native SQLite (production, iOS tests). The JVM host
// test passes AndroidSQLiteDriver instead: the bundled driver ships only Android-ABI
// natives and can't load on a desktop JVM, whereas the framework driver is backed by
// Robolectric's SQLite.
fun buildDatabase(
    builder: RoomDatabase.Builder<OstomateDatabase>,
    driver: SQLiteDriver = BundledSQLiteDriver(),
): OstomateDatabase =
    builder
        .setDriver(driver)
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_2_3)
        .addCallback(SeedDefaultSuppliesCallback)
        .build()

private object SeedDefaultSuppliesCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        connection.execSQL(SEED_DEFAULT_SUPPLIES_SQL)
    }
}
