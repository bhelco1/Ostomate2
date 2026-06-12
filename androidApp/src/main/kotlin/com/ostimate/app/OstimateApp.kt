package com.ostimate.app

import android.app.Application
import com.ostimate.app.di.initKoin
import org.koin.android.ext.koin.androidContext

class OstimateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@OstimateApp)
        }
    }
}
