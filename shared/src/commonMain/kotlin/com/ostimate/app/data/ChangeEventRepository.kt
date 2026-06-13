package com.ostimate.app.data

import com.ostimate.app.data.db.ChangeEventDao
import com.ostimate.app.data.db.ChangeEventEntity
import com.ostimate.app.data.db.ChangeEventWithSupply
import com.ostimate.app.data.db.SupplyTypeDao
import com.ostimate.app.domain.DeepLinkParser
import com.ostimate.app.domain.SupplyKind
import com.ostimate.app.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow

class ChangeEventRepository(
    private val eventDao: ChangeEventDao,
    private val supplyTypeDao: SupplyTypeDao,
) {
    fun observeEvents(): Flow<List<ChangeEventWithSupply>> = eventDao.observeAllWithSupply()

    fun observeBySupply(supplyTypeId: Long): Flow<List<ChangeEventWithSupply>> = eventDao.observeBySupply(supplyTypeId)

    suspend fun logChange(supplyTypeId: Long): ChangeEventEntity = logChangeAt(supplyTypeId, currentTimeMillis())

    suspend fun logChangeAt(
        supplyTypeId: Long,
        timestampMillis: Long,
    ): ChangeEventEntity {
        val now = currentTimeMillis()
        val event =
            ChangeEventEntity(
                supplyTypeId = supplyTypeId,
                timestampMillis = timestampMillis,
                createdAtMillis = now,
            )
        val id = eventDao.insert(event)
        supplyTypeDao.decrementOnHand(supplyTypeId)
        return event.copy(id = id)
    }

    /** Deletes an event and returns one unit to inventory. */
    suspend fun delete(event: ChangeEventEntity) {
        eventDao.delete(event)
        supplyTypeDao.incrementOnHand(event.supplyTypeId)
    }

    /** Re-inserts a previously deleted event (undo-delete) and takes one unit from inventory. */
    suspend fun reinsert(event: ChangeEventEntity): ChangeEventEntity {
        val newId = eventDao.insert(event.copy(id = 0))
        supplyTypeDao.decrementOnHand(event.supplyTypeId)
        return event.copy(id = newId)
    }

    suspend fun update(event: ChangeEventEntity) {
        eventDao.update(event.copy(editedAtMillis = currentTimeMillis()))
    }

    /** Returns the name of the supply that was logged, or null if the URI was not a valid log link. */
    suspend fun handleDeepLink(uri: String): String? {
        val item = DeepLinkParser.parse(uri) ?: return null
        val supply =
            when {
                item == "bag" -> supplyTypeDao.getByKind(SupplyKind.BAG)
                item == "flange" -> supplyTypeDao.getByKind(SupplyKind.FLANGE)
                else -> {
                    val id = DeepLinkParser.parseCustomId(item) ?: return null
                    supplyTypeDao.getById(id)
                }
            } ?: return null
        logChange(supply.id)
        return supply.name
    }
}
