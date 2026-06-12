package com.ostimate.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "change_events")
data class ChangeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supply: String,
    val timestampMillis: Long,
)
