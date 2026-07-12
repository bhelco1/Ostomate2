package com.ostomate.app.data

import com.ostomate.app.data.db.ChangeEventDao
import com.ostomate.app.data.db.ChangeEventEntity
import com.ostomate.app.data.db.ChangeEventWithSupply
import com.ostomate.app.data.db.SupplyTypeDao
import com.ostomate.app.domain.DeepLinkParser
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.currentTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val DEEP_LINK_DEBOUNCE_MS = 3_000L
private const val CONFIRM_REPEAT_WINDOW_MS = 10 * 60 * 1000L

/** The result of resolving a deep link, so the UI can log, confirm, or ignore accordingly. */
sealed interface DeepLinkOutcome {
    data class Logged(val supplyName: String) : DeepLinkOutcome

    data class NeedsConfirmation(val supplyId: Long, val supplyName: String, val minutesAgo: Int) : DeepLinkOutcome

    data object Suppressed : DeepLinkOutcome // phantom / within 3s debounce

    data object Invalid : DeepLinkOutcome // unrecognized link
}

class ChangeEventRepository(
    private val eventDao: ChangeEventDao,
    private val supplyTypeDao: SupplyTypeDao,
    private val clock: () -> Long = { currentTimeMillis() },
) {
    private val lastScanMillis = mutableMapOf<String, Long>()
    private val scanMutex = Mutex()

    fun observeEvents(): Flow<List<ChangeEventWithSupply>> = eventDao.observeAllWithSupply()

    fun observeBySupply(supplyTypeId: Long): Flow<List<ChangeEventWithSupply>> = eventDao.observeBySupply(supplyTypeId)

    suspend fun logChange(supplyTypeId: Long): ChangeEventEntity = logChangeAt(supplyTypeId, clock())

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
     * Whole minutes since the most recent change for [supplyId] if it falls within
     * [CONFIRM_REPEAT_WINDOW_MS], else null. Used to confirm a genuine rapid-repeat log
     * instead of silently recording a second change.
     */
    suspend fun wasLoggedWithinWindow(
        supplyId: Long,
        now: Long = clock(),
    ): Int? {
        val last = eventDao.latestTimestampForSupply(supplyId) ?: return null
        val delta = now - last
        if (delta < 0 || delta >= CONFIRM_REPEAT_WINDOW_MS) return null
        return (delta / 60_000L).toInt()
    }

    /** Logs a change unconditionally (used to confirm a rapid repeat) and returns the supply name. */
    suspend fun forceLogChange(supplyTypeId: Long): String {
        logChange(supplyTypeId)
        return supplyTypeDao.getById(supplyTypeId)?.name.orEmpty()
    }

    /**
     * Resolves a scanned/opened log link into an outcome the UI acts on: log immediately,
     * ask to confirm a rapid repeat, silently suppress a phantom re-fire, or reject an
     * unrecognized link. The 3s atomic debounce is applied before the repeat-window check.
     */
    suspend fun handleDeepLink(uri: String): DeepLinkOutcome {
        val item = DeepLinkParser.parse(uri) ?: return DeepLinkOutcome.Invalid
        val allowed =
            scanMutex.withLock {
                val now = clock()
                val last = lastScanMillis[item] ?: 0L
                if (now - last < DEEP_LINK_DEBOUNCE_MS) {
                    false
                } else {
                    lastScanMillis[item] = now
                    true
                }
            }
        if (!allowed) return DeepLinkOutcome.Suppressed
        val supply =
            when {
                item == "bag" -> supplyTypeDao.getByKind(SupplyKind.BAG)
                item == "flange" -> supplyTypeDao.getByKind(SupplyKind.FLANGE)
                else -> {
                    val id = DeepLinkParser.parseCustomId(item) ?: return DeepLinkOutcome.Invalid
                    supplyTypeDao.getById(id)
                }
            } ?: return DeepLinkOutcome.Invalid
        val minutesAgo = wasLoggedWithinWindow(supply.id)
        if (minutesAgo != null) {
            return DeepLinkOutcome.NeedsConfirmation(supply.id, supply.name, minutesAgo)
        }
        logChange(supply.id)
        return DeepLinkOutcome.Logged(supply.name)
    }
}
