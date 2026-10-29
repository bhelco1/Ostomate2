package com.ostomate.app.platform

/** Outcome of asking Google for a Drive (drive.file) access token. */
sealed interface DriveAuthorization {
    data class Authorized(val accessToken: String) : DriveAuthorization

    /** Consent or sign-in is needed, and this request was not allowed to show UI. */
    data object NeedsUserInteraction : DriveAuthorization

    /** The user backed out of the Google sign-in or consent screen. */
    data object Cancelled : DriveAuthorization

    data object Failed : DriveAuthorization
}

/**
 * The daily Google Drive backup's platform side: Google authorization and the daily job.
 * Android only; elsewhere [isSupported] is false and the Settings controls are not shown.
 */
interface DriveBackupPlatform {
    val isSupported: Boolean

    /** May show Google's account picker and consent screen over the current Activity. */
    suspend fun authorizeInteractively(): DriveAuthorization

    /** Never shows UI; answers [DriveAuthorization.NeedsUserInteraction] if it would have to. */
    suspend fun authorizeSilently(): DriveAuthorization

    /** Idempotent: keeps an already-scheduled job rather than restarting its clock. */
    fun scheduleDaily()

    fun cancelDaily()
}

/** For platforms without the feature (iOS). */
object UnsupportedDriveBackupPlatform : DriveBackupPlatform {
    override val isSupported: Boolean = false

    override suspend fun authorizeInteractively(): DriveAuthorization = DriveAuthorization.Failed

    override suspend fun authorizeSilently(): DriveAuthorization = DriveAuthorization.Failed

    override fun scheduleDaily() = Unit

    override fun cancelDaily() = Unit
}
