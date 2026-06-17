package com.ostomate.app.platform

/**
 * Thin abstraction over the platform crash-reporting SDK (Sentry).
 * Opt-in only — [init] is a no-op when [enabled] is false.
 * Android: sentry-android. iOS: no-op stub until SPM is wired in iosApp.
 */
expect class CrashReporter {
    /** Called once at app startup with the stored preference. */
    fun init(
        dsn: String,
        enabled: Boolean,
    )

    /** Called when the user toggles the Settings switch at runtime. */
    fun setEnabled(enabled: Boolean)
}
