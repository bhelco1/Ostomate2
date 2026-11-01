package com.ostomate.app.platform

import android.content.Context
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

actual class CrashReporter(private val context: Context) : CrashReporting {
    private var currentDsn: String = ""

    override fun init(
        dsn: String,
        enabled: Boolean,
    ) {
        currentDsn = dsn
        if (enabled && dsn.isNotBlank()) start()
    }

    override fun setEnabled(enabled: Boolean) {
        if (enabled) {
            if (currentDsn.isNotBlank() && !Sentry.isEnabled()) start()
        } else {
            Sentry.close()
        }
    }

    private fun start() {
        SentryAndroid.init(context) { options ->
            options.dsn = currentDsn
            // Crash-only: no sessions, no performance, no breadcrumbs.
            options.isEnableAutoSessionTracking = false
            options.tracesSampleRate = null
            options.maxBreadcrumbs = 0
            options.isEnableUserInteractionBreadcrumbs = false
            // libsentry.so crashes in sentry_add_breadcrumb on SentryExecutorS thread;
            // JVM-level crash reporting is sufficient for our opt-in reporter.
            options.isEnableNdk = false
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrubCrashEvent(event) }
        }
    }
}

// Runs after Sentry's event processors, which set user.id and device.id to a random install
// ID and user.ip_address to "{{auto}}" (asking Sentry to store the sender's IP). The privacy
// policy promises reports carry neither.
internal fun scrubCrashEvent(event: SentryEvent): SentryEvent {
    event.user = null
    event.contexts.device?.id = null
    // Remove any extra data that might carry note or tag field contents.
    event.removeExtra("note")
    event.removeExtra("tags")
    return event
}
