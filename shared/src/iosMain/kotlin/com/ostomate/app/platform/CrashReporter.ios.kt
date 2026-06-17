package com.ostomate.app.platform

// Sentry is initialized from Swift in iOSApp.swift via SentryBridgeKt.sentryEnabled().
// The Kotlin expect/actual contract is satisfied here with no-ops; the SDK is not
// available to call from Kotlin. Changes to setEnabled() take effect on next launch.
actual class CrashReporter {
    actual fun init(
        dsn: String,
        enabled: Boolean,
    ) {}

    actual fun setEnabled(enabled: Boolean) {}
}
