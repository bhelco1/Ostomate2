package com.ostimate.app.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(entities = [ChangeEventEntity::class], version = 1, exportSchema = true)
@ConstructedBy(OstimateDatabaseConstructor::class)
abstract class OstimateDatabase : RoomDatabase() {
    abstract fun changeEventDao(): ChangeEventDao

    companion object {
        const val FILE_NAME = "ostimate_database.db"
    }
}

// Room's KSP compiler generates the per-platform actuals for this constructor.
@Suppress("KotlinNoActualForExpect", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object OstimateDatabaseConstructor : RoomDatabaseConstructor<OstimateDatabase> {
    override fun initialize(): OstimateDatabase
}

fun buildDatabase(builder: RoomDatabase.Builder<OstimateDatabase>): OstimateDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
