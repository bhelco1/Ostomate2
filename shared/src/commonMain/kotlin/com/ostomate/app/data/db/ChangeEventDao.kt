package com.ostomate.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ostomate.app.domain.SupplyKind
import kotlinx.coroutines.flow.Flow

/** An event joined with the catalog fields the UI needs to render it. */
data class ChangeEventWithSupply(
    @Embedded val event: ChangeEventEntity,
    val supplyName: String,
    val supplyKind: SupplyKind,
)

@Dao
interface ChangeEventDao {
    @Insert
    suspend fun insert(event: ChangeEventEntity): Long

    @Update
    suspend fun update(event: ChangeEventEntity)

    @Delete
    suspend fun delete(event: ChangeEventEntity)

    @Query(
        """
        SELECT e.*, s.name AS supplyName, s.kind AS supplyKind
        FROM change_events e JOIN supply_types s ON s.id = e.supplyTypeId
        ORDER BY e.timestampMillis DESC
        """,
    )
    fun observeAllWithSupply(): Flow<List<ChangeEventWithSupply>>

    @Query("SELECT * FROM change_events WHERE supplyTypeId = :supplyTypeId ORDER BY timestampMillis DESC")
    suspend fun getBySupplyType(supplyTypeId: Long): List<ChangeEventEntity>

    @Query(
        """
        SELECT e.*, s.name AS supplyName, s.kind AS supplyKind
        FROM change_events e JOIN supply_types s ON s.id = e.supplyTypeId
        WHERE e.supplyTypeId = :supplyTypeId
        ORDER BY e.timestampMillis DESC
        """,
    )
    fun observeBySupply(supplyTypeId: Long): Flow<List<ChangeEventWithSupply>>

    @Query("SELECT COUNT(*) FROM change_events")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM change_events WHERE timestampMillis = :millis")
    suspend fun countByTimestamp(millis: Long): Int
}
