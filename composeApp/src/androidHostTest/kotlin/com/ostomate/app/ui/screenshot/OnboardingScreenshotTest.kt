package com.ostomate.app.ui.screenshot

import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.domain.ApplianceType
import com.ostomate.app.ui.InMemoryDataStore
import com.ostomate.app.ui.onboarding.OnboardingScreen
import com.ostomate.app.ui.onboarding.OnboardingStep
import com.ostomate.app.ui.onboarding.OnboardingViewModel
import org.junit.Test

class OnboardingScreenshotTest : ScreenshotTest() {
    private fun viewModel() =
        OnboardingViewModel(
            supplyRepository = supplyRepository,
            settingsRepository = SettingsRepository(InMemoryDataStore()),
        )

    /** Step 1 of 4 — the first thing a new user ever sees. */
    @Test
    fun onboardingApplianceTypeStep() {
        val vm = viewModel()
        awaitState(vm.uiState) { it.step == OnboardingStep.APPLIANCE_TYPE }

        capture("onboarding_appliance_type") {
            OnboardingScreen(onDone = {}, viewModel = vm)
        }
    }

    /**
     * The counts step — BUG-03's screen. Its two text fields sit above the Next button, which
     * is what the on-screen keyboard used to cover on iPhone; a regression in its padding,
     * scroll or imePadding chain shows up here as a moved Next button.
     */
    @Test
    fun onboardingCountsStep() {
        val vm = viewModel()
        vm.setApplianceType(ApplianceType.TWO_PIECE)
        vm.nextStep()
        vm.nextStep()
        vm.setBagCount("24")
        vm.setFlangeCount("12")
        awaitState(vm.uiState) { it.step == OnboardingStep.COUNTS && it.flangeCount == "12" }

        capture("onboarding_counts") {
            OnboardingScreen(onDone = {}, viewModel = vm)
        }
    }

    /** Final step: the QR explainer, which is dense text plus two buttons. */
    @Test
    fun onboardingQrExplainerStep() {
        val vm = viewModel()
        vm.setApplianceType(ApplianceType.ONE_PIECE)
        vm.nextStep()
        vm.nextStep()
        awaitState(vm.uiState) { it.step == OnboardingStep.QR_EXPLAINER }

        capture("onboarding_qr_explainer") {
            OnboardingScreen(onDone = {}, viewModel = vm)
        }
    }
}
