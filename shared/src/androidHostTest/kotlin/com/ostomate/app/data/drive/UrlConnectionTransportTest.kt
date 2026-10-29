package com.ostomate.app.data.drive

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Runs the real HttpURLConnection against the JDK's built-in server on localhost. */
class UrlConnectionTransportTest {
    private data class Seen(
        val method: String,
        val path: String,
        val auth: String?,
        val type: String?,
        val body: String,
    )

    private val seen = mutableListOf<Seen>()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val body = exchange.requestBody.bufferedReader().readText()
                seen +=
                    Seen(
                        exchange.requestMethod,
                        exchange.requestURI.toString(),
                        exchange.requestHeaders.getFirst("Authorization"),
                        exchange.requestHeaders.getFirst("Content-Type"),
                        body,
                    )
                val (status, reply) =
                    when (exchange.requestURI.path) {
                        "/denied" -> 403 to """{"error":"no"}"""
                        "/empty" -> 204 to ""
                        else -> 200 to """{"id":"x"}"""
                    }
                val bytes = reply.toByteArray()
                exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
                if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
                exchange.close()
            }
            start()
        }
    private val base = "http://127.0.0.1:${server.address.port}"
    private val transport = UrlConnectionTransport()

    @After
    fun stop() = server.stop(0)

    @Test
    fun sendsMethodHeadersAndBodyAndReturnsTheReply() =
        runBlocking {
            val response =
                transport.execute(
                    HttpRequest(
                        method = "POST",
                        url = "$base/files?fields=id",
                        headers = mapOf("Authorization" to "Bearer t", "Content-Type" to "application/json"),
                        body = """{"name":"é"}""",
                    ),
                )

            assertEquals(HttpResponse(200, """{"id":"x"}"""), response)
            assertEquals(
                Seen("POST", "/files?fields=id", "Bearer t", "application/json", """{"name":"é"}"""),
                seen.single(),
            )
        }

    @Test
    fun aRefusalComesBackWithItsStatusAndErrorBody() =
        runBlocking {
            assertEquals(HttpResponse(403, """{"error":"no"}"""), transport.execute(HttpRequest("GET", "$base/denied")))
        }

    @Test
    fun deleteWithNoReplyBodyWorks() =
        runBlocking {
            assertEquals(HttpResponse(204, ""), transport.execute(HttpRequest("DELETE", "$base/empty")))
            assertEquals("DELETE", seen.single().method)
        }

    @Test
    fun noConnectionIsANetworkException() {
        val closedPort = ServerSocket(0).use { it.localPort }
        runBlocking {
            assertFailsWith<DriveNetworkException> {
                transport.execute(HttpRequest("GET", "http://127.0.0.1:$closedPort/"))
            }
        }
    }
}
