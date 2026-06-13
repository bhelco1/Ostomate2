package com.ostimate.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ostimate.app.domain.SupplyKind
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

    @Query("UPDATE supply_types SET onHand = onHand - 1 WHERE id = :id")
    suspend fun decrementOnHand(id: Long)

    @Query("UPDATE supply_types SET onHand = onHand + 1 WHERE id = :id")
    suspend fun incrementOnHand(id: Long)
}
