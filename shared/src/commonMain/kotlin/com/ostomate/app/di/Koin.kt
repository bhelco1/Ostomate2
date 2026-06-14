package com.ostomate.app.di

import com.ostomate.app.data.BackupRepository
import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.data.SupplyRepository
import com.ostomate.app.data.db.ChangeEventDao
import com.ostomate.app.data.db.OstomateDatabase
import com.ostomate.app.data.db.SupplyTypeDao
import com.ostomate.app.data.db.buildDatabase
import com.ostomate.app.data.settings.SettingsRepository
import com.ostomate.app.domain.NotificationScheduler
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/** Provides the platform-specific RoomDatabase.Builder. */
expect val platformModule: Module

val dataModule =
    module {
        single<OstomateDatabase> { buildDatabase(get()) }
        single<ChangeEventDao> { get<OstomateDatabase>().changeEventDao() }
        single<SupplyTypeDao> { get<OstomateDatabase>().supplyTypeDao() }
        singleOf(::ChangeEventRepository)
        singleOf(::SupplyRepository)
        singleOf(::SettingsRepository)
        singleOf(::NotificationScheduler)
        singleOf(::BackupRepository)
    }
