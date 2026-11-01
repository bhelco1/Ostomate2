package com.ostomate.app.platform

import io.sentry.SentryEvent
import io.sentry.protocol.Device
import io.sentry.protocol.User
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * sentry-android's DefaultAndroidEventProcessor gives every event a user (the random
 * install ID, plus ip_address "{{auto}}") and puts the same install ID in device.id.
 * Event processors run before beforeSend, so the scrubber is the last word on what leaves.
 */
class CrashReporterScrubTest {
    private fun eventAsSentryBuildsIt() =
        SentryEvent().apply {
            user =
                User().apply {
                    id = "install-id-1234"
                    ipAddress = "{{auto}}"
                }
            contexts.setDevice(
                Device().apply {
                    id = "install-id-1234"
                    model = "Pixel 8"
                },
            )
            setExtra("note", "changed flange early")
            setExtra("tags", "leak")
        }

    @Test
    fun scrubDropsTheUserSoNeitherInstallIdNorIpIsSent() {
        val scrubbed = scrubCrashEvent(eventAsSentryBuildsIt())

        assertNull(scrubbed.user)
    }

    @Test
    fun scrubDropsTheInstallIdFromTheDeviceContextButKeepsTheModel() {
        val device = scrubCrashEvent(eventAsSentryBuildsIt()).contexts.device

        assertNull(device?.id)
        assertEquals("Pixel 8", device?.model)
    }

    @Test
    fun scrubRemovesNoteAndTagExtras() {
        val scrubbed = scrubCrashEvent(eventAsSentryBuildsIt())

        assertNull(scrubbed.getExtra("note"))
        assertNull(scrubbed.getExtra("tags"))
    }
}
