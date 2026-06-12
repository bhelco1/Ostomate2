package com.ostimate.app.di

import com.ostimate.app.data.ChangeEventRepository
import com.ostimate.app.data.db.ChangeEventDao
import com.ostimate.app.data.db.OstimateDatabase
import com.ostimate.app.data.db.buildDatabase
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Provides the platform-specific RoomDatabase.Builder. */
expect val platformModule: Module

val dataModule = module {
    single<OstimateDatabase> { buildDatabase(get()) }
    single<ChangeEventDao> { get<OstimateDatabase>().changeEventDao() }
    singleOf(::ChangeEventRepository)
}
