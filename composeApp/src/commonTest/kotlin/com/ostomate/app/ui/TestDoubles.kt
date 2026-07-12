package com.ostomate.app.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.ostomate.app.data.db.BackupDao
import com.ostomate.app.data.db.ChangeEventDao
import com.ostomate.app.data.db.ChangeEventEntity
import com.ostomate.app.data.db.ChangeEventWithSupply
import com.ostomate.app.data.db.SupplyTypeDao
import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.BiometricAuth
import com.ostomate.app.platform.BiometricResult
import com.ostomate.app.platform.CrashReporting
import com.ostomate.app.platform.ReminderNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * In-memory stand-ins at the DAO / platform-interface boundary. ViewModel tests
 * construct the real repositories on top of these, so repository logic (undo
 * increments, backup round-trips) is exercised, not mocked away.
 */
class FakeSupplyTypeDao : SupplyTypeDao {
    private val state = MutableStateFlow<List<SupplyTypeEntity>>(emptyList())
    private var nextId = 1L

    val supplies: StateFlow<List<SupplyTypeEntity>> = state

    suspend fun seed(vararg items: SupplyTypeEntity): List<Long> = items.map { insert(it) }

    override suspend fun insert(supplyType: SupplyTypeEntity): Long {
        val id = if (supplyType.id == 0L) nextId++ else supplyType.id
        state.value = state.value + supplyType.copy(id = id)
        return id
    }

    override suspend fun update(supplyType: SupplyTypeEntity) {
        state.value = state.value.map { if (it.id == supplyType.id) supplyType else it }
    }

    override fun observeActive(): Flow<List<SupplyTypeEntity>> =
        state.map { list -> list.filter { !it.archived }.sortedBy { it.sortOrder } }

    override suspend fun getAll(): List<SupplyTypeEntity> = state.value.sortedBy { it.sortOrder }

    override suspend fun getById(id: Long): SupplyTypeEntity? = state.value.find { it.id == id }

    override suspend fun getByKind(kind: SupplyKind): SupplyTypeEntity? =
        state.value.firstOrNull { it.kind == kind && !it.archived }

    override suspend fun setOnHand(
        id: Long,
        onHand: Int,
    ) = mutate(id) { it.copy(onHand = onHand) }

    override suspend fun decrementOnHand(id: Long) = mutate(id) { it.copy(onHand = it.onHand - 1) }

    override suspend fun incrementOnHand(id: Long) = mutate(id) { it.copy(onHand = it.onHand + 1) }

    override suspend fun setWarnThreshold(
        id: Long,
        days: Int,
    ) = mutate(id) { it.copy(warnThresholdDays = days) }

    override suspend fun archive(id: Long) = mutate(id) { it.copy(archived = true) }

    override suspend fun maxSortOrder(): Int = state.value.maxOfOrNull { it.sortOrder } ?: -1

    fun clear() {
        state.value = emptyList()
    }

    private fun mutate(
        id: Long,
        transform: (SupplyTypeEntity) -> SupplyTypeEntity,
    ) {
        state.value = state.value.map { if (it.id == id) transform(it) else it }
    }
}

class FakeChangeEventDao(private val supplyDao: FakeSupplyTypeDao) : ChangeEventDao {
    private val state = MutableStateFlow<List<ChangeEventEntity>>(emptyList())
    private var nextId = 1L

    val events: StateFlow<List<ChangeEventEntity>> = state

    override suspend fun insert(event: ChangeEventEntity): Long {
        val id = if (event.id == 0L) nextId++ else event.id
        state.value = state.value + event.copy(id = id)
        return id
    }

    override suspend fun update(event: ChangeEventEntity) {
        state.value = state.value.map { if (it.id == event.id) event else it }
    }

    override suspend fun delete(event: ChangeEventEntity) {
        state.value = state.value.filterNot { it.id == event.id }
    }

    override fun observeAllWithSupply(): Flow<List<ChangeEventWithSupply>> =
        combine(state, supplyDao.supplies) { events, supplies -> join(events, supplies) }

    override suspend fun getBySupplyType(supplyTypeId: Long): List<ChangeEventEntity> =
        state.value.filter { it.supplyTypeId == supplyTypeId }.sortedByDescending { it.timestampMillis }

    override fun observeBySupply(supplyTypeId: Long): Flow<List<ChangeEventWithSupply>> =
        combine(state, supplyDao.supplies) { events, supplies ->
            join(events.filter { it.supplyTypeId == supplyTypeId }, supplies)
        }

    override suspend fun getAllRaw(): List<ChangeEventEntity> = state.value

    override suspend fun latestTimestampForSupply(supplyTypeId: Long): Long? =
        state.value.filter { it.supplyTypeId == supplyTypeId }.maxOfOrNull { it.timestampMillis }

    override suspend fun count(): Long = state.value.size.toLong()

    override suspend fun countByTimestamp(millis: Long): Int = state.value.count { it.timestampMillis == millis }

    fun clear() {
        state.value = emptyList()
    }

    private fun join(
        events: List<ChangeEventEntity>,
        supplies: List<SupplyTypeEntity>,
    ): List<ChangeEventWithSupply> {
        val byId = supplies.associateBy { it.id }
        return events
            .sortedByDescending { it.timestampMillis }
            .mapNotNull { event ->
                val supply = byId[event.supplyTypeId] ?: return@mapNotNull null
                ChangeEventWithSupply(event = event, supplyName = supply.name, supplyKind = supply.kind)
            }
    }
}

/** Applies restore's wipe-and-replace against the in-memory fake DAOs. */
class FakeBackupDao(
    private val supplyDao: FakeSupplyTypeDao,
    private val eventDao: FakeChangeEventDao,
) : BackupDao {
    override suspend fun deleteAllEvents() = eventDao.clear()

    override suspend fun deleteAllSupplies() = supplyDao.clear()

    override suspend fun insertSupplies(supplies: List<SupplyTypeEntity>) {
        supplies.forEach { supplyDao.insert(it) }
    }

    override suspend fun insertEvents(events: List<ChangeEventEntity>) {
        events.forEach { eventDao.insert(it) }
    }
}

class RecordingNotifier : ReminderNotifier {
    data class Scheduled(val tag: String, val delaySeconds: Int, val title: String, val body: String)

    val scheduled = mutableListOf<Scheduled>()

    override fun schedule(
        tag: String,
        delaySeconds: Int,
        title: String,
        body: String,
    ) {
        scheduled += Scheduled(tag, delaySeconds, title, body)
    }
}

class FakeBiometricAuth(var nextResult: BiometricResult = BiometricResult.Success) : BiometricAuth {
    var promptCount = 0

    override fun authenticate(
        reason: String,
        onResult: (BiometricResult) -> Unit,
    ) {
        promptCount++
        onResult(nextResult)
    }
}

class RecordingCrashReporter : CrashReporting {
    val setEnabledCalls = mutableListOf<Boolean>()

    override fun init(
        dsn: String,
        enabled: Boolean,
    ) = Unit

    override fun setEnabled(enabled: Boolean) {
        setEnabledCalls += enabled
    }
}

class InMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value).toPreferences()
        state.value = updated
        return updated
    }
}
