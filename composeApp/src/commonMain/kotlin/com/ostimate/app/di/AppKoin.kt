package com.ostimate.app.di

import com.ostimate.app.ui.calendar.CalendarViewModel
import com.ostimate.app.ui.history.HistoryViewModel
import com.ostimate.app.ui.home.HomeViewModel
import com.ostimate.app.ui.settings.SettingsViewModel
import com.ostimate.app.ui.stats.StatsViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val uiModule =
    module {
        viewModelOf(::HomeViewModel)
        viewModelOf(::HistoryViewModel)
        viewModelOf(::CalendarViewModel)
        viewModelOf(::StatsViewModel)
        viewModelOf(::SettingsViewModel)
    }

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(platformModule, dataModule, uiModule)
    }
}
