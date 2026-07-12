package com.ostomate.app.platform

import com.ostomate.app.data.DeepLinkOutcome
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries deep-link outcomes from platform entry points (MainActivity, handleDeepLink iOS)
 * to the UI layer (App.kt), which turns each outcome into a snackbar, a confirm dialog, or
 * nothing (a suppressed phantom re-fire).
 */
object DeepLinkBus {
    private val _events = MutableSharedFlow<DeepLinkOutcome>(extraBufferCapacity = 4)
    val events: SharedFlow<DeepLinkOutcome> = _events.asSharedFlow()

    suspend fun post(outcome: DeepLinkOutcome) = _events.emit(outcome)
}
