package com.ostimate.app.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries deep-link log confirmations from platform entry points (MainActivity, handleDeepLink iOS)
 * to the UI layer (App.kt snackbar). supply name = success; null = unrecognized link.
 */
object DeepLinkBus {
    private val _events = MutableSharedFlow<String?>(extraBufferCapacity = 4)
    val events: SharedFlow<String?> = _events.asSharedFlow()

    suspend fun post(supplyName: String?) = _events.emit(supplyName)
}
