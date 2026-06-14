package com.ostomate.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ostomate.app.domain.SupplyKind

/**
 * A supply catalog row (v2 data model in 02-architecture.md). Supply types are
 * rows, not an enum (N2): box size and warning threshold are per-supply, and
 * rows are archived rather than deleted so history keeps its referent.
 */
@Entity(tableName = "supply_types")
data class SupplyTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: SupplyKind,
    val boxSize: Int,
    val warnThresholdDays: Int,
    val onHand: Int = 0,
    val sortOrder: Int,
    val archived: Boolean = false,
    /** Color palette index (0–7) for CUSTOM kind. Null uses the default palette[0]. */
    val colorIndex: Int? = null,
)
