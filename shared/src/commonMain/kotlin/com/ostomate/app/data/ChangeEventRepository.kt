package com.ostomate.app.data

import com.ostomate.app.data.db.ChangeEventDao
import com.ostomate.app.data.db.ChangeEventEntity
import com.ostomate.app.data.db.ChangeEventWithSupply
import com.ostomate.app.data.db.SupplyTypeDao
import com.ostomate.app.domain.DeepLinkParser
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow

private const val DEEP_LINK_DEBOUNCE_MS = 3_000L

class ChangeEventRepository(
    private val eventDao: ChangeEventDao,
    private val supplyTypeDao: SupplyTypeDao,
) {
    private val lastScanMillis = mutableMapOf<String, Long>()
    fun observeEvents(): Flow<List<ChangeEventWithSupply>> = eventDao.observeAllWithSupply()

    fun observeBySupply(supplyTypeId: Long): Flow<List<ChangeEventWithSupply>> = eventDao.observeBySupply(supplyTypeId)

    suspend fun logChange(supplyTypeId: Long): ChangeEventEntity = logChangeAt(supplyTypeId, currentTimeMillis())

    suspend fun logChangeAt(
        supplyTypeId: Long,
        timestampMillis: Long,
        createdAtMillis: Long = timestampMillis,
    ): ChangeEventEntity {
        val event =
            ChangeEventEntity(
                supplyTypeId = supplyTypeId,
                timestampMillis = timestampMillis,
                createdAtMillis = createdAtMillis,
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

    /**
     * Returns the name of the supply that was logged, or null if the URI was not a valid
     * log link or a duplicate scan within the debounce window.
     */
    suspend fun handleDeepLink(uri: String): String? {
        val item = DeepLinkParser.parse(uri) ?: return null
        val now = currentTimeMillis()
        val last = lastScanMillis[item] ?: 0L
        if (now - last < DEEP_LINK_DEBOUNCE_MS) return null
        lastScanMillis[item] = now
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
