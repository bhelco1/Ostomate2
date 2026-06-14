package com.ostomate.app

import android.app.Application
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.di.initKoin
import com.ostomate.app.platform.CrashReporter
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext

class OstomateApp : Application() {

    private val settings: SettingsRepository by inject()
    private val crashReporter: CrashReporter by inject()

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@OstomateApp)
        }
        initCrashReporter()
    }

    private fun initCrashReporter() {
        // Read the stored opt-in preference and start Sentry only if enabled.
        // The async read means a crash in the first few milliseconds won't be
        // captured — acceptable for an opt-in-only reporter.
        MainScope().launch {
            val enabled = settings.settings.first().crashReportingEnabled
            crashReporter.init(BuildConfig.SENTRY_DSN, enabled)
        }
    }
}
