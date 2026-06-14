package com.ostomate.app.platform

import android.content.Context
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

actual class CrashReporter(private val context: Context) {
    private var currentDsn: String = ""

    actual fun init(dsn: String, enabled: Boolean) {
        currentDsn = dsn
        if (enabled && dsn.isNotBlank()) start()
    }

    actual fun setEnabled(enabled: Boolean) {
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
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ -> scrub(event) }
        }
    }

    private fun scrub(event: SentryEvent): SentryEvent {
        // Remove any extra data that might carry note or tag field contents.
        event.removeExtra("note")
        event.removeExtra("tags")
        return event
    }
}
