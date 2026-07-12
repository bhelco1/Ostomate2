package com.ostomate.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Destructive wipe-and-replace used by a full-state backup restore (FEAT-00). */
@Dao
interface BackupDao {
    @Query("DELETE FROM change_events")
    suspend fun deleteAllEvents()

    @Query("DELETE FROM supply_types")
    suspend fun deleteAllSupplies()

    @Insert
    suspend fun insertSupplies(supplies: List<SupplyTypeEntity>)

    @Insert
    suspend fun insertEvents(events: List<ChangeEventEntity>)

    /**
     * Replaces all catalog + event rows in one transaction, preserving ids and FKs. Events are
     * deleted before supplies and supplies inserted before events, to satisfy the RESTRICT FK.
     */
    @Transaction
    suspend fun replaceAll(
        supplies: List<SupplyTypeEntity>,
        events: List<ChangeEventEntity>,
    ) {
        deleteAllEvents()
        deleteAllSupplies()
        insertSupplies(supplies)
        insertEvents(events)
    }
}
