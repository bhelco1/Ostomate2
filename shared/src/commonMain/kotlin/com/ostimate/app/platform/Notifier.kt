package com.ostimate.app.platform

/**
 * Schedules a local notification [delaySeconds] from now.
 *
 * iOS requests notification authorization on first use. On Android the app shell
 * requests POST_NOTIFICATIONS at startup (API 33+).
 */
expect class Notifier {
    fun schedule(delaySeconds: Int, title: String, body: String)
}
