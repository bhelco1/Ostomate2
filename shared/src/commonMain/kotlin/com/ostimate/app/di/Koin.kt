package com.ostimate.app.di

import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.data.SupplyRepository
import com.ostimate.app.data.db.ChangeEventDao
import com.ostimate.app.data.db.OstimateDatabase
import com.ostimate.app.data.db.SupplyTypeDao
import com.ostimate.app.data.db.buildDatabase
import com.ostimate.app.data.settings.SettingsRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Provides the platform-specific RoomDatabase.Builder. */
expect val platformModule: Module

val dataModule = module {
    single<OstimateDatabase> { buildDatabase(get()) }
    single<ChangeEventDao> { get<OstimateDatabase>().changeEventDao() }
    single<SupplyTypeDao> { get<OstimateDatabase>().supplyTypeDao() }
    singleOf(::ChangeEventRepository)
    singleOf(::SupplyRepository)
    singleOf(::SettingsRepository)
}
