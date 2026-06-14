package com.ostimate.app.platform

// iOS Sentry integration is deferred: add `Sentry` via Swift Package Manager in
// iosApp/iosApp, call SentrySDK.start in the Swift entry point (AppDelegate or
// iosApp.swift), and read the crashReportingEnabled setting there.
// This no-op stub keeps the expect/actual contract satisfied in the meantime.
actual class CrashReporter {
    actual fun init(dsn: String, enabled: Boolean) {}
    actual fun setEnabled(enabled: Boolean) {}
}
