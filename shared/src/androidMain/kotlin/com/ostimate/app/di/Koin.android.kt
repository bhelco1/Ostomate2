package com.ostimate.app.di

import androidx.room.RoomDatabase
import com.ostimate.app.data.db.OstimateDatabase
import com.ostimate.app.data.db.databaseBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single<RoomDatabase.Builder<OstimateDatabase>> { databaseBuilder(androidContext()) }
}
