package com.ostimate.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.ostimate.app.data.db.OstimateDatabase
import com.ostimate.app.data.db.databaseBuilder
import com.ostimate.app.data.settings.settingsDataStore
import com.ostimate.app.platform.BiometricAuthenticator
import com.ostimate.app.platform.FeedbackHelper
import com.ostimate.app.platform.FileSharer
import com.ostimate.app.platform.Notifier
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        single<RoomDatabase.Builder<OstimateDatabase>> { databaseBuilder() }
        single<DataStore<Preferences>> { settingsDataStore() }
        single { Notifier() }
        single { BiometricAuthenticator() }
        single { FileSharer() }
        single { FeedbackHelper() }
    }
