package com.ostomate.app.ui.accessibility

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.diagnostics.DiagnosticLog
import com.ostomate.app.data.diagnostics.InMemoryDiagnosticLogStore
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.domain.NotificationScheduler
import com.ostomate.app.domain.SupplyKind
import com.ostomate.app.platform.FeedbackHelper
import com.ostomate.app.platform.FileSharer
import com.ostomate.app.ui.FakeBackupDao
import com.ostomate.app.ui.InMemoryDataStore
import com.ostomate.app.ui.RecordingCrashReporter
import com.ostomate.app.ui.RecordingNotifier
import com.ostomate.app.ui.home.HomeScreen
import com.ostomate.app.ui.home.HomeViewModel
import com.ostomate.app.ui.screenshot.DAY_MS
import com.ostomate.app.ui.screenshot.FIXED_MILLIS
import com.ostomate.app.ui.screenshot.FIXED_TODAY
import com.ostomate.app.ui.screenshot.ScreenshotTest
import com.ostomate.app.ui.settings.SettingsScreen
import com.ostomate.app.ui.settings.SettingsViewModel
import com.ostomate.app.ui.testSupply
import com.ostomate.app.ui.theme.OstomateTheme
import org.junit.After
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Automated accessibility check (2.5.9) on Home and Settings, read from Compose semantics on
 * the JVM host: every clickable has something for a screen reader to announce (spec 4.3).
 * Manual TalkBack/VoiceOver stays on the release checklist for how the screens actually sound.
 *
 * Not Google's Accessibility Test Framework: ui-test's enableAccessibilityChecks() logs
 * "not supported by Robolectric" and checks nothing. That is why
 * [checkFlagsAnUnlabelledButton] exists: the check must be seen to fail on a bad control.
 *
 * No touch-target size check, deliberately. Compose's hit testing extends every pointer
 * target to 48dp when nothing overlaps (touchBoundsInRoot always reads >= 48dp), so a 48dp
 * rule on laid-out size only flagged Material 3's standard 40dp buttons, which are already
 * 48dp to a finger (tried 2026-10-08: the overflow and text buttons on Home and Settings).
 *
 * Extends [ScreenshotTest] for its fake data layer, seeding and state priming only; nothing
 * here records an image.
 */
@OptIn(ExperimentalTestApi::class)
class AccessibilityTest : ScreenshotTest() {
    @After
    fun stopKoinIfStarted() = stopKoin()

    @Test
    fun homeWithSuppliesIsAccessible() {
        seed {
            val (bagId, flangeId) =
                supplyDao.seed(
                    testSupply(name = "Bag", kind = SupplyKind.BAG, onHand = 24, warnThresholdDays = 7),
                    testSupply(name = "Flange", kind = SupplyKind.FLANGE, onHand = 12, sortOrder = 1),
                )
            repeat(3) { i -> eventRepository.logChangeAt(bagId, FIXED_MILLIS - (3 - i) * 2 * DAY_MS) }
            repeat(3) { i -> eventRepository.logChangeAt(flangeId, FIXED_MILLIS - (3 - i) * 3 * DAY_MS) }
        }
        val vm =
            HomeViewModel(
                eventRepository = eventRepository,
                supplyRepository = supplyRepository,
                notificationScheduler = NotificationScheduler(RecordingNotifier()),
            )
        awaitState(vm.uiState) { state -> state.supplies.size == 2 }

        runComposeUiTest {
            setContent { OstomateTheme { HomeScreen(viewModel = vm, today = FIXED_TODAY) } }
            assertNoViolations()
        }
    }

    @Test
    fun settingsIsAccessible() {
        startKoin {
            modules(
                module {
                    single { FileSharer() }
                    single { FeedbackHelper(ApplicationProvider.getApplicationContext()) }
                },
            )
        }
        val settingsRepository = SettingsRepository(InMemoryDataStore())
        val vm =
            SettingsViewModel(
                settingsRepository = settingsRepository,
                backupRepository =
                    BackupRepository(FakeBackupDao(supplyDao, eventDao), eventDao, supplyDao, settingsRepository),
                crashReporter = RecordingCrashReporter(),
                diagnosticLog = DiagnosticLog(InMemoryDiagnosticLogStore()),
            )

        runComposeUiTest {
            setContent { OstomateTheme { SettingsScreen(viewModel = vm) } }
            assertNoViolations()
        }
    }

    @Test
    fun checkFlagsAnUnlabelledButton() {
        runComposeUiTest {
            setContent { Box(Modifier.size(48.dp).clickable {}) }
            assertEquals(1, unlabelledClickables().size, "label check missed an unlabelled button")
        }
    }

    private fun ComposeUiTest.clickables(): List<SemanticsNode> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()

    /** Nothing to announce: no own or merged text, content description or click label. */
    private fun ComposeUiTest.unlabelledClickables(): List<SemanticsNode> =
        clickables().filter { node ->
            val c = node.config
            c.getOrNull(SemanticsProperties.Text).isNullOrEmpty() &&
                c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                c.getOrNull(SemanticsActions.OnClick)?.label.isNullOrEmpty() &&
                c.getOrNull(SemanticsProperties.EditableText) == null
        }

    private fun ComposeUiTest.assertNoViolations() {
        val unlabelled = unlabelledClickables()
        assertTrue(unlabelled.isEmpty(), "Nothing to announce: ${unlabelled.map { it.config }}")
    }
}
