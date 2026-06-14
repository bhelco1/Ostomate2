package com.ostimate.app.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat

actual class FeedbackHelper(private val context: Context) {
    actual fun launch() {
        val subject = "Ostimate 2.0 Feedback"
        val body = buildBody()

        val intent =
            Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        // Prefer running in the current activity so Android 10+ doesn't require NEW_TASK.
        val starter = CurrentActivityHolder.activity ?: context
        starter.startActivity(intent)
    }

    private fun buildBody(): String {
        val appInfo = buildAppInfo()
        val deviceInfo = buildDeviceInfo()
        val crashInfo = buildCrashInfo()

        return "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "OSTIMATE 2.0 — FEEDBACK REPORT\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "\n" +
            "─── APP INFORMATION ───────────────\n" +
            "$appInfo\n" +
            "\n" +
            "─── DEVICE INFORMATION ────────────\n" +
            "$deviceInfo\n" +
            "\n" +
            "─── CRASH / ERROR DATA ────────────\n" +
            "$crashInfo\n" +
            "\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "YOUR COMMENTS — please describe what happened:\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "\n" +
            "\n"
    }

    private fun buildAppInfo(): String {
        return try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            val versionCode = PackageInfoCompat.getLongVersionCode(pi)
            buildString {
                appendLine("App:          Ostimate 2.0")
                appendLine("Version name: ${pi.versionName ?: "unknown"}")
                appendLine("Version code: $versionCode")
                append("Package:      ${context.packageName}")
            }
        } catch (e: Exception) {
            "App info unavailable: ${e.message}"
        }
    }

    private fun buildDeviceInfo(): String =
        buildString {
            appendLine("Manufacturer: ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }}")
            appendLine("Model:        ${Build.MODEL}")
            appendLine("Android:      ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            append("ABI:          ${Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"}")
        }

    private fun buildCrashInfo(): String {
        val lastCrash = LastCrashStore.read(context)
        return if (lastCrash != null) {
            "Last recorded crash:\n$lastCrash"
        } else {
            "No crash data captured.\n" +
                "(Automated crash reporting is planned for a future release.)"
        }
    }

    companion object {
        const val FEEDBACK_EMAIL = "ostomate26@gmail.com"
    }
}
