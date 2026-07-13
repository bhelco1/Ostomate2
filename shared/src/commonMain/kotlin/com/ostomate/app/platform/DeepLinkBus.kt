package com.ostomate.app.platform

import com.ostomate.app.data.DeepLinkOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Carries deep-link outcomes from platform entry points (MainActivity, handleDeepLink iOS)
 * to the UI layer (App.kt), which turns each outcome into a snackbar, a confirm dialog, or
 * nothing (a suppressed phantom re-fire).
 *
 * A [Channel], not a `SharedFlow`. It previously used `MutableSharedFlow(extraBufferCapacity = 4)`
 * — i.e. **replay = 0**, which silently drops anything emitted while there are no collectors.
 * On a cold start that is exactly what happened: `MainActivity.onCreate` handles the deep link
 * and posts the outcome immediately, but App.kt only subscribes once DataStore has emitted
 * settings and `MainApp` has composed. The post won that race, so scanning a QR sticker with the
 * app closed logged the change to the database and then showed the user nothing — inviting a
 * re-scan, which is one of the ways duplicate events get created (see BUG-09).
 *
 * A channel holds the event until something collects it, so a late subscriber still receives it.
 * Deliberately not `replay = 1`, which would re-deliver the snackbar on every resubscribe
 * (rotation, process recreation).
 */
object DeepLinkBus {
    private val _events = Channel<DeepLinkOutcome>(capacity = Channel.BUFFERED)

    /** Single-consumer by design: App.kt is the only collector, and each outcome is delivered once. */
    val events: Flow<DeepLinkOutcome> = _events.receiveAsFlow()

    suspend fun post(outcome: DeepLinkOutcome) {
        _events.send(outcome)
    }
}
