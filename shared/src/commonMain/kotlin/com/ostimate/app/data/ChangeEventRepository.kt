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

    suspend fun logChange(supplyTypeId: Long): ChangeEventEntity {
        val now = currentTimeMillis()
        val event = ChangeEventEntity(
            supplyTypeId = supplyTypeId,
            timestampMillis = now,
            createdAtMillis = now,
        )
        val id = eventDao.insert(event)
        return event.copy(id = id)
    }

    suspend fun delete(event: ChangeEventEntity) = eventDao.delete(event)

    /** Returns the name of the supply that was logged, or null if the URI was not a valid log link. */
    suspend fun handleDeepLink(uri: String): String? {
        val item = DeepLinkParser.parse(uri) ?: return null
        // The parser allowlist ("bag"/"flange") maps onto the seeded default supplies.
        val kind = when (item) {
            "bag" -> SupplyKind.BAG
            "flange" -> SupplyKind.FLANGE
            else -> return null
        }
        val supply = supplyTypeDao.getByKind(kind) ?: return null
        logChange(supply.id)
        return supply.name
    }
}
