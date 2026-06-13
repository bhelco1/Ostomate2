package com.ostimate.app.data

import com.ostimate.app.data.db.SupplyTypeDao
import com.ostimate.app.data.db.SupplyTypeEntity
import com.ostimate.app.domain.SupplyKind
import kotlinx.coroutines.flow.Flow

class SupplyRepository(private val dao: SupplyTypeDao) {
    fun observeSupplies(): Flow<List<SupplyTypeEntity>> = dao.observeActive()

    suspend fun setOnHand(
        id: Long,
        onHand: Int,
    ) = dao.setOnHand(id, onHand)

    suspend fun setWarnThreshold(
        id: Long,
        days: Int,
    ) = dao.setWarnThreshold(id, days)

    suspend fun archive(id: Long) = dao.archive(id)

    suspend fun addCustomSupply(
        name: String,
        boxSize: Int,
        warnThresholdDays: Int,
        colorIndex: Int,
    ) {
        val nextSort = dao.maxSortOrder() + 1
        dao.insert(
            SupplyTypeEntity(
                name = name,
                kind = SupplyKind.CUSTOM,
                boxSize = boxSize,
                warnThresholdDays = warnThresholdDays,
                onHand = 0,
                sortOrder = nextSort,
                colorIndex = colorIndex,
            ),
        )
    }

    suspend fun update(supplyType: SupplyTypeEntity) = dao.update(supplyType)
}
