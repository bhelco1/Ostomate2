package com.ostimate.app.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual class FeedbackHelper {
    actual fun launch() {
        val appVersion =
            try {
                platform.Foundation.NSBundle.mainBundle.infoDictionary
                    ?.get("CFBundleShortVersionString") as? String ?: "unknown"
            } catch (e: Exception) {
                "unknown"
            }

        val osVersion = platform.UIKit.UIDevice.currentDevice.systemVersion
        val model = platform.UIKit.UIDevice.currentDevice.model

        val body = buildBody(appVersion, model, osVersion)

        val subject = "Ostimate 2.0 Feedback"
        val encodedSubject = subject.replace(" ", "%20")
        val encodedBody =
            body
                .replace("%", "%25")
                .replace("\n", "%0A")
                .replace(" ", "%20")

        val url =
            NSURL.URLWithString(
                "mailto:ostomate26@gmail.com?subject=$encodedSubject&body=$encodedBody",
            ) ?: return
        UIApplication.sharedApplication.openURL(url)
    }

    private fun buildBody(
        appVersion: String,
        model: String,
        osVersion: String,
    ): String =
        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "OSTIMATE 2.0 — FEEDBACK REPORT\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "\n" +
            "─── APP INFORMATION ───────────────\n" +
            "App:      Ostimate 2.0\n" +
            "Version:  $appVersion\n" +
            "Platform: iOS\n" +
            "\n" +
            "─── DEVICE INFORMATION ────────────\n" +
            "Model:    $model\n" +
            "iOS:      $osVersion\n" +
            "\n" +
            "─── CRASH / ERROR DATA ────────────\n" +
            "No crash data captured.\n" +
            "(Automated crash reporting is planned for a future release.)\n" +
            "\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "YOUR COMMENTS — please describe what happened:\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "\n" +
            "\n"
}
