package com.ostomate.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.ostomate.app.data.db.OstomateDatabase
import com.ostomate.app.data.db.databaseBuilder
import com.ostomate.app.data.diagnostics.DiagnosticLog
import com.ostomate.app.data.diagnostics.diagnosticLogStore
import com.ostomate.app.data.settings.settingsDataStore
import com.ostomate.app.platform.BiometricAuth
import com.ostomate.app.platform.BiometricAuthenticator
import com.ostomate.app.platform.CrashReporter
import com.ostomate.app.platform.CrashReporting
import com.ostomate.app.platform.FeedbackHelper
import com.ostomate.app.platform.FileSharer
import com.ostomate.app.platform.Notifier
import com.ostomate.app.platform.ReminderNotifier
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        single<RoomDatabase.Builder<OstomateDatabase>> { databaseBuilder(androidContext()) }
        single<DataStore<Preferences>> { settingsDataStore(androidContext()) }
        single { DiagnosticLog(diagnosticLogStore(androidContext())) }
        single { Notifier(androidContext()) } bind ReminderNotifier::class
        single { BiometricAuthenticator() } bind BiometricAuth::class
        single { FileSharer() }
        single { FeedbackHelper(androidContext()) }
        single { CrashReporter(androidContext()) } bind CrashReporting::class
    }
