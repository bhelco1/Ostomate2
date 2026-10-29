package com.ostomate.app.data.drive

/** One HTTPS request. Bodies are text: everything the Drive backup sends or reads is JSON. */
data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class HttpResponse(
    val status: Int,
    val body: String,
)

/** The connection could not be made or broke off; there is no HTTP status to report. */
class DriveNetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Drive answered with a non-2xx status. */
class DriveApiException(val status: Int, message: String) : Exception(message)

/**
 * The only seam between the Drive backup and the network. The Android implementation is a
 * thin HttpURLConnection wrapper; everything that decides what to send lives in common code.
 */
fun interface HttpTransport {
    /** Returns any status, 2xx or not; throws [DriveNetworkException] if there is none. */
    suspend fun execute(request: HttpRequest): HttpResponse
}
