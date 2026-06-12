package com.ostimate.app.data

import com.ostimate.app.data.db.ChangeEventDao
import com.ostimate.app.data.db.ChangeEventEntity
import com.ostimate.app.domain.DeepLinkParser
import com.ostimate.app.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow

class ChangeEventRepository(private val dao: ChangeEventDao) {

    fun observeEvents(): Flow<List<ChangeEventEntity>> = dao.observeAll()

    suspend fun logChange(supply: String): ChangeEventEntity {
        val event = ChangeEventEntity(supply = supply, timestampMillis = currentTimeMillis())
        val id = dao.insert(event)
        return event.copy(id = id)
    }

    suspend fun delete(event: ChangeEventEntity) = dao.delete(event)

    /** Returns the supply that was logged, or null if the URI was not a valid log link. */
    suspend fun handleDeepLink(uri: String): String? =
        DeepLinkParser.parse(uri)?.also { logChange(it) }
}
