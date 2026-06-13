package com.ostimate.app.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.RoomDatabase
import com.ostimate.app.data.db.OstimateDatabase
import com.ostimate.app.data.db.databaseBuilder
import com.ostimate.app.data.settings.settingsDataStore
import com.ostimate.app.platform.BiometricAuthenticator
import com.ostimate.app.platform.FileSharer
import com.ostimate.app.platform.Notifier
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        single<RoomDatabase.Builder<OstimateDatabase>> { databaseBuilder(androidContext()) }
        single<DataStore<Preferences>> { settingsDataStore(androidContext()) }
        single { Notifier(androidContext()) }
        single { BiometricAuthenticator() }
        single { FileSharer() }
    }
