package com.ostimate.app.di

import com.ostimate.app.ui.home.HomeViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val uiModule = module {
    viewModelOf(::HomeViewModel)
}

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(platformModule, dataModule, uiModule)
    }
}
