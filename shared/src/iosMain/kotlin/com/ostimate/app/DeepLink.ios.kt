package com.ostimate.app

import com.ostimate.app.data.ChangeEventRepository
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Entry point for iOS `onOpenURL`. Fire-and-forget: parses and logs the change;
 * invalid URIs are ignored (parser allowlist).
 */
fun handleDeepLink(uri: String) {
    val repository = KoinPlatform.getKoin().get<ChangeEventRepository>()
    MainScope().launch { repository.handleDeepLink(uri) }
}
