package com.ostomate.app.ui.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import com.ostomate.app.ui.screenshot.ScreenshotTest
import com.ostomate.app.ui.theme.OstomateTheme
import org.junit.Test

/**
 * The in-app policy must say what the published one (docs/privacy.html) says: two optional
 * outbound paths, crash reports and the Android daily Drive backup. Extends [ScreenshotTest]
 * for its Robolectric setup only; nothing is captured.
 */
@OptIn(ExperimentalTestApi::class)
class PrivacyPolicyScreenTest : ScreenshotTest() {
    @Test
    fun describesTheOptionalDriveBackup() {
        runComposeUiTest {
            setContent { OstomateTheme { PrivacyPolicyScreen(onBack = {}) } }

            onNodeWithText("Daily Google Drive backup (optional, Android)").performScrollTo().assertExists()
            onNodeWithText("drive.file", substring = true).performScrollTo().assertExists()
            onNodeWithText("never sent to the developer", substring = true).performScrollTo().assertExists()
            onNodeWithText("never sent to any server", substring = true).assertDoesNotExist()
            onNodeWithText("does not upload backups anywhere automatically", substring = true).assertDoesNotExist()
        }
    }

    // Reports carry device details as well as the stack trace; "only crash stack traces are
    // sent" was false. They carry no identifier because the beforeSend scrubbers strip it.
    @Test
    fun saysWhatACrashReportAndTheFeedbackEmailContain() {
        runComposeUiTest {
            setContent { OstomateTheme { PrivacyPolicyScreen(onBack = {}) } }

            onNodeWithText("only crash stack traces are sent", substring = true).assertDoesNotExist()
            onNodeWithText("the device maker and model", substring = true).performScrollTo().assertExists()
            onNodeWithText("no identifier for you, your device", substring = true).performScrollTo().assertExists()
            onNodeWithText("configured not to store it", substring = true).performScrollTo().assertExists()
            onNodeWithText("Feedback email").performScrollTo().assertExists()
            onNodeWithText("nothing is sent unless you press send", substring = true).performScrollTo().assertExists()
        }
    }
}
