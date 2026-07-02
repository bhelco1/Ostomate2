package com.ostomate.app.platform

// Sentry is initialized from Swift in iOSApp.swift via SentryBridgeKt.sentryEnabled().
// The Kotlin expect/actual contract is satisfied here with no-ops; the SDK is not
// available to call from Kotlin. Changes to setEnabled() take effect on next launch.
actual class CrashReporter : CrashReporting {
    override fun init(
        dsn: String,
        enabled: Boolean,
    ) {}

    override fun setEnabled(enabled: Boolean) {}
}
