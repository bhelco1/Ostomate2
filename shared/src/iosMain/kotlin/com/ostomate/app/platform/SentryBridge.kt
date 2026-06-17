package com.ostomate.app.platform

import com.ostomate.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.mp.KoinPlatform

fun sentryEnabled(): Boolean = runBlocking {
    KoinPlatform.getKoin().get<SettingsRepository>().settings.first().crashReportingEnabled
}
