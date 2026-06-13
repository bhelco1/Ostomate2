package com.ostimate.app.data

import com.ostimate.app.data.db.SupplyTypeDao
import com.ostimate.app.data.db.SupplyTypeEntity
import kotlinx.coroutines.flow.Flow

class SupplyRepository(private val dao: SupplyTypeDao) {

    fun observeSupplies(): Flow<List<SupplyTypeEntity>> = dao.observeActive()

    suspend fun setOnHand(id: Long, onHand: Int) = dao.setOnHand(id, onHand)

    suspend fun update(supplyType: SupplyTypeEntity) = dao.update(supplyType)
}
