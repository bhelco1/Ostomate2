package com.ostomate.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ostomate.app.domain.SupplyKind
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplyTypeDao {
    @Insert
    suspend fun insert(supplyType: SupplyTypeEntity): Long

    @Update
    suspend fun update(supplyType: SupplyTypeEntity)

    @Query("SELECT * FROM supply_types WHERE NOT archived ORDER BY sortOrder")
    fun observeActive(): Flow<List<SupplyTypeEntity>>

    @Query("SELECT * FROM supply_types ORDER BY sortOrder")
    suspend fun getAll(): List<SupplyTypeEntity>

    @Query("SELECT * FROM supply_types WHERE id = :id")
    suspend fun getById(id: Long): SupplyTypeEntity?

    @Query("SELECT * FROM supply_types WHERE kind = :kind AND NOT archived LIMIT 1")
    suspend fun getByKind(kind: SupplyKind): SupplyTypeEntity?

    @Query("UPDATE supply_types SET onHand = :onHand WHERE id = :id")
    suspend fun setOnHand(
        id: Long,
        onHand: Int,
    )

    // MAX(...,0): you cannot physically hold -1 bags. Logging a change with 0 on hand used to
    // write -1 to the DB (and into backups), which then drove "0 days remaining" and a reorder
    // warning off nonsense data. Undo does not rely on the inverse of this — it restores the
    // exact prior count via undoLog() — so clamping here cannot invent inventory.
    @Query("UPDATE supply_types SET onHand = MAX(onHand - 1, 0) WHERE id = :id")
    suspend fun decrementOnHand(id: Long)

    @Query("UPDATE supply_types SET onHand = onHand + 1 WHERE id = :id")
    suspend fun incrementOnHand(id: Long)

    @Query("UPDATE supply_types SET warnThresholdDays = :days WHERE id = :id")
    suspend fun setWarnThreshold(
        id: Long,
        days: Int,
    )

    @Query("UPDATE supply_types SET archived = 1 WHERE id = :id")
    suspend fun archive(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM supply_types")
    suspend fun maxSortOrder(): Int
}
