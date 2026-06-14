package com.ostomate.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One logged supply change (v2 data model in 02-architecture.md). Events are
 * editable with audit timestamps (N1): `createdAtMillis` is set once,
 * `editedAtMillis` stays null until the first edit. `tags` is a comma-separated
 * list (N4); both stay null until those features land.
 */
@Entity(
    tableName = "change_events",
    foreignKeys = [
        ForeignKey(
            entity = SupplyTypeEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplyTypeId"],
            // Supply types are archived, never deleted, while events reference them.
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("supplyTypeId")],
)
data class ChangeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplyTypeId: Long,
    val timestampMillis: Long,
    val note: String? = null,
    val tags: String? = null,
    val createdAtMillis: Long,
    val editedAtMillis: Long? = null,
)
