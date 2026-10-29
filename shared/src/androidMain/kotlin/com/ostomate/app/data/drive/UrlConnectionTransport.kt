package com.ostomate.app.data.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** [HttpTransport] over the platform's HttpURLConnection; no HTTP library is added for this. */
class UrlConnectionTransport(
    private val connectTimeoutMillis: Int = 30_000,
    private val readTimeoutMillis: Int = 60_000,
) : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(request.url).openConnection() as HttpURLConnection
                try {
                    send(connection, request)
                } finally {
                    connection.disconnect()
                }
            } catch (e: IOException) {
                throw DriveNetworkException("${request.method} failed: ${e.javaClass.simpleName}", e)
            }
        }

    private fun send(
        connection: HttpURLConnection,
        request: HttpRequest,
    ): HttpResponse {
        connection.requestMethod = request.method
        connection.connectTimeout = connectTimeoutMillis
        connection.readTimeout = readTimeoutMillis
        connection.useCaches = false
        request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        request.body?.let { body ->
            val bytes = body.encodeToByteArray()
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use { it.write(bytes) }
        }
        val status = connection.responseCode
        // A non-2xx body arrives on the error stream, and is null when there is none.
        val stream = if (status in HTTP_SUCCESS) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return HttpResponse(status, body)
    }

    private companion object {
        val HTTP_SUCCESS = 200..299
    }
}
