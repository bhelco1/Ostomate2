package com.ostomate.app.ui

import com.ostomate.app.data.db.SupplyTypeEntity
import com.ostomate.app.domain.SupplyKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * viewModelScope launches on Dispatchers.Main, so every ViewModel test swaps in a
 * test dispatcher; runTest picks up its scheduler automatically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class MainDispatcherTest {
    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }
}

/**
 * uiState flows use SharingStarted.WhileSubscribed, so they only compute while
 * collected. Keeps a collector alive for the rest of the test (backgroundScope
 * cancels it automatically at test end).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.keepSubscribed(flow: StateFlow<*>) {
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
}

fun testSupply(
    id: Long = 0,
    name: String = "Bag",
    kind: SupplyKind = SupplyKind.BAG,
    boxSize: Int = 10,
    warnThresholdDays: Int = 7,
    onHand: Int = 10,
    sortOrder: Int = 0,
    archived: Boolean = false,
    colorIndex: Int? = null,
) = SupplyTypeEntity(
    id = id,
    name = name,
    kind = kind,
    boxSize = boxSize,
    warnThresholdDays = warnThresholdDays,
    onHand = onHand,
    sortOrder = sortOrder,
    archived = archived,
    colorIndex = colorIndex,
)
