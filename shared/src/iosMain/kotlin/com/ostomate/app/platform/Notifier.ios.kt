package com.ostimate.app.platform

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

actual class Notifier {
    actual fun schedule(
        tag: String,
        delaySeconds: Int,
        title: String,
        body: String,
    ) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            if (!granted) return@requestAuthorizationWithOptions

            val content =
                UNMutableNotificationContent().apply {
                    setTitle(title)
                    setBody(body)
                    setSound(UNNotificationSound.defaultSound())
                }
            val trigger =
                UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(
                    timeInterval = delaySeconds.toDouble(),
                    repeats = false,
                )
            // Remove pending notification with same tag before re-adding
            center.removePendingNotificationRequestsWithIdentifiers(listOf(tag))
            val request =
                UNNotificationRequest.requestWithIdentifier(
                    identifier = tag,
                    content = content,
                    trigger = trigger,
                )
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }
}
