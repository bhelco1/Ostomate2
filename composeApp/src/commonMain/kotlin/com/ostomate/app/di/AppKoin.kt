package com.ostomate.app.di

import com.ostomate.app.ui.calendar.CalendarViewModel
import com.ostomate.app.ui.history.HistoryViewModel
import com.ostomate.app.ui.home.HomeViewModel
import com.ostomate.app.ui.onboarding.OnboardingViewModel
import com.ostomate.app.ui.settings.ManageSuppliesViewModel
import com.ostomate.app.ui.settings.SettingsViewModel
import com.ostomate.app.ui.stats.StatsViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import kotlin.time.Clock

val uiModule =
    module {
        // Injected rather than read statically so screenshot tests can pin "today": a
        // wall-clock read inside a ViewModel changes its rendered output every day.
        single<Clock> { Clock.System }
        viewModelOf(::HomeViewModel)
        viewModelOf(::HistoryViewModel)
        viewModelOf(::CalendarViewModel)
        viewModelOf(::StatsViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::ManageSuppliesViewModel)
        viewModelOf(::OnboardingViewModel)
    }

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(platformModule, dataModule, uiModule)
    }
}
