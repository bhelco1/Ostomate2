package com.ostomate.app

import android.app.Application
import com.ostomate.app.data.drive.ensureDriveBackupScheduled
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.di.initKoin
import com.ostomate.app.platform.CrashReporter
import com.ostomate.app.platform.DriveBackupPlatform
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext

class OstomateApp : Application() {
    /**
     * Work that must outlive any one Activity, such as saving a scanned change: an Activity's
     * lifecycleScope is cancelled when it is destroyed (rotation, a recreate) mid-save.
     */
    val appScope = MainScope()


    private val settings: SettingsRepository by inject()
    private val crashReporter: CrashReporter by inject()
    private val driveBackupPlatform: DriveBackupPlatform by inject()

    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@OstomateApp)
        }
        initCrashReporter()
        // KEEP policy: re-enqueuing an existing daily job leaves its schedule alone.
        appScope.launch { ensureDriveBackupScheduled(settings, driveBackupPlatform) }
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
