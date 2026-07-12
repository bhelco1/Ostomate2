package com.ostomate.app

import com.ostomate.app.data.ChangeEventRepository
import com.ostomate.app.platform.DeepLinkBus
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Entry point for iOS `onOpenURL`. Logs the change and posts result to DeepLinkBus
 * so App.kt can show the confirmation snackbar.
 */
fun handleDeepLink(uri: String) {
    val repository = KoinPlatform.getKoin().get<ChangeEventRepository>()
    MainScope().launch {
        val outcome = repository.handleDeepLink(uri)
        DeepLinkBus.post(outcome)
    }
}
