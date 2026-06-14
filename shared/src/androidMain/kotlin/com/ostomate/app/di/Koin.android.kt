package com.ostomate.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.ostomate.app.data.db.OstomateDatabase
import com.ostomate.app.data.db.databaseBuilder
import com.ostomate.app.data.settings.settingsDataStore
import com.ostomate.app.platform.BiometricAuthenticator
import com.ostomate.app.platform.CrashReporter
import com.ostomate.app.platform.FeedbackHelper
import com.ostomate.app.platform.FileSharer
import com.ostomate.app.platform.Notifier
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        single<RoomDatabase.Builder<OstomateDatabase>> { databaseBuilder(androidContext()) }
        single<DataStore<Preferences>> { settingsDataStore(androidContext()) }
        single { Notifier(androidContext()) }
        single { BiometricAuthenticator() }
        single { FileSharer() }
        single { FeedbackHelper(androidContext()) }
        single { CrashReporter(androidContext()) }
    }
