package com.ostomate.app.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
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

    companion object {
        const val FILE_NAME = "ostomate_database.db"
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
 * Used by both the fresh-install callback and MIGRATION_1_2.
 */
internal val SEED_DEFAULT_SUPPLIES_SQL =
    """
    INSERT INTO supply_types (name, kind, boxSize, warnThresholdDays, onHand, sortOrder, archived)
    VALUES ('Bag', 'BAG', 30, 14, 0, 0, 0), ('Flange', 'FLANGE', 5, 14, 0, 1, 0)
    """.trimIndent()

/**
 * v1 (spike): change_events(id, supply TEXT, timestampMillis)
 * v2: supply_types catalog + change_events with FK, note/tags, audit timestamps.
 * Spike rows are remapped onto the seeded defaults via their supply string;
 * createdAt is backfilled from the event timestamp.
 */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `supply_types` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL, `kind` TEXT NOT NULL,
                    `boxSize` INTEGER NOT NULL, `warnThresholdDays` INTEGER NOT NULL,
                    `onHand` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL,
                    `archived` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            connection.execSQL(SEED_DEFAULT_SUPPLIES_SQL)
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `change_events_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `supplyTypeId` INTEGER NOT NULL,
                    `timestampMillis` INTEGER NOT NULL,
                    `note` TEXT, `tags` TEXT,
                    `createdAtMillis` INTEGER NOT NULL,
                    `editedAtMillis` INTEGER,
                    FOREIGN KEY(`supplyTypeId`) REFERENCES `supply_types`(`id`)
                        ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            connection.execSQL(
                """
                INSERT INTO change_events_new (id, supplyTypeId, timestampMillis, note, tags, createdAtMillis, editedAtMillis)
                SELECT e.id,
                       (SELECT s.id FROM supply_types s WHERE s.kind = UPPER(e.supply)),
                       e.timestampMillis, NULL, NULL, e.timestampMillis, NULL
                FROM change_events e
                WHERE UPPER(e.supply) IN ('BAG', 'FLANGE')
                """.trimIndent(),
            )
            connection.execSQL("DROP TABLE `change_events`")
            connection.execSQL("ALTER TABLE `change_events_new` RENAME TO `change_events`")
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_change_events_supplyTypeId` ON `change_events` (`supplyTypeId`)",
            )
        }
    }

/** v3: adds colorIndex (nullable INTEGER) to supply_types for custom supply palette selection. */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE `supply_types` ADD COLUMN `colorIndex` INTEGER")
        }
    }

fun buildDatabase(builder: RoomDatabase.Builder<OstomateDatabase>): OstomateDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .addCallback(SeedDefaultSuppliesCallback)
        .build()

private object SeedDefaultSuppliesCallback : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        connection.execSQL(SEED_DEFAULT_SUPPLIES_SQL)
    }
}
