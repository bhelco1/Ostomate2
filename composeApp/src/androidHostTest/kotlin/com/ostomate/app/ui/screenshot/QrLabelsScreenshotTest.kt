package com.ostomate.app.ui.screenshot

import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.FileSharer
import com.ostomate.app.ui.FakeBiometricAuth
import com.ostomate.app.ui.InMemoryDataStore
import com.ostomate.app.ui.settings.ManageSuppliesViewModel
import com.ostomate.app.ui.settings.QrLabelsScreen
import com.ostomate.app.ui.testSupply
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * QrLabelsScreen resolves its FileSharer through `koinInject`, so this is the one screen that
 * needs a Koin graph. The QR bitmaps themselves are a pure function of the deep-link URL, so
 * they are stable across runs.
 */
class QrLabelsScreenshotTest : ScreenshotTest() {
    @Before
    fun startKoinForFileSharer() {
        startKoin { modules(module { single { FileSharer() } }) }
    }

    @After
    fun stopKoinAfterTest() {
        stopKoin()
    }

    private fun viewModel() =
        ManageSuppliesViewModel(
            supplyRepository = supplyRepository,
            settingsRepository = SettingsRepository(InMemoryDataStore()),
            biometricAuth = FakeBiometricAuth(),
        )

    /** Two-column grid of QR cards — the layout that gets printed onto supply boxes. */
    @Test
    fun qrLabelsGrid() {
        seed {
            supplyDao.seed(
                testSupply(name = "Bag", kind = SupplyKind.BAG, onHand = 24),
                testSupply(name = "Flange", kind = SupplyKind.FLANGE, onHand = 12, sortOrder = 1),
                testSupply(name = "Barrier Ring", kind = SupplyKind.CUSTOM, onHand = 8, sortOrder = 2, colorIndex = 2),
            )
        }

        val vm = viewModel()
        awaitState(vm.uiState) { it.supplies.size == 3 }

        capture("qr_labels_grid") {
            QrLabelsScreen(onBack = {}, viewModel = vm)
        }
    }

    /** No supplies yet — centred empty-state copy under the top bar. */
    @Test
    fun qrLabelsEmpty() {
        val vm = viewModel()
        awaitState(vm.uiState) { it.supplies.isEmpty() }

        capture("qr_labels_empty") {
            QrLabelsScreen(onBack = {}, viewModel = vm)
        }
    }
}
