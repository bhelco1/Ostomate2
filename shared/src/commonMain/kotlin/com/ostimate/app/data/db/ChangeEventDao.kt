package com.ostimate.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChangeEventDao {
    @Insert
    suspend fun insert(event: ChangeEventEntity): Long

    @Delete
    suspend fun delete(event: ChangeEventEntity)

    @Query("SELECT * FROM change_events ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<ChangeEventEntity>>

    @Query("SELECT COUNT(*) FROM change_events")
    suspend fun count(): Long
}
