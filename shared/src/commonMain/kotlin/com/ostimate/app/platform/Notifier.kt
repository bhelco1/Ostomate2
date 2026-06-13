package com.ostimate.app.platform

/**
 * Schedules a local notification [delaySeconds] from now.
 * [tag] is used as a unique identifier — rescheduling with the same tag replaces the previous
 * notification (cancel + re-enqueue on Android; update request identifier on iOS).
 *
 * iOS requests notification authorization on first use. On Android the app shell
 * requests POST_NOTIFICATIONS at startup (API 33+).
 */
expect class Notifier {
    fun schedule(
        tag: String,
        delaySeconds: Int,
        title: String,
        body: String,
    )
}
