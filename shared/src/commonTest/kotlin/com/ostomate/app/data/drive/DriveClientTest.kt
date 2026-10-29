package com.ostomate.app.data.drive

import com.ostomate.app.domain.drive.DriveFile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Answers each request with the next scripted response and keeps what was sent. */
class ScriptedTransport(vararg responses: HttpResponse) : HttpTransport {
    private val queue = ArrayDeque(responses.toList())
    val requests = mutableListOf<HttpRequest>()

    fun enqueue(vararg responses: HttpResponse) = queue.addAll(responses)

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return queue.removeFirstOrNull() ?: error("no scripted response for ${request.method} ${request.url}")
    }
}

class DriveClientTest {
    private val transport = ScriptedTransport()
    private val client = DriveClient(transport)

    @Test
    fun findFolderQueriesByNameAndFolderTypeAndPicksTheOldest() =
        runTest {
            transport.enqueue(
                ok(
                    """
                    {"files":[
                      {"id":"newer","name":"Ostomate backups","createdTime":"2026-03-02T00:00:00.000Z"},
                      {"id":"older","name":"Ostomate backups","createdTime":"2026-03-01T00:00:00Z"}
                    ]}
                    """,
                ),
            )

            assertEquals("older", client.findFolder(TOKEN, "Ostomate backups"))

            val request = transport.requests.single()
            assertEquals("GET", request.method)
            assertEquals("Bearer $TOKEN", request.headers["Authorization"])
            assertEquals(
                "https://www.googleapis.com/drive/v3/files" +
                    "?q=name%20%3D%20%27Ostomate%20backups%27%20and%20" +
                    "mimeType%20%3D%20%27application%2Fvnd.google-apps.folder%27%20and%20trashed%20%3D%20false" +
                    "&spaces=drive&fields=files%28id%2Cname%2CcreatedTime%29",
                request.url,
            )
        }

    @Test
    fun findFolderReturnsNullWhenNoneExists() =
        runTest {
            transport.enqueue(ok("""{"files":[]}"""))
            assertNull(client.findFolder(TOKEN, "Ostomate backups"))
        }

    @Test
    fun quotesInNamesAreEscapedInTheQuery() =
        runTest {
            transport.enqueue(ok("""{"files":[]}"""))
            client.findFolder(TOKEN, "Bob's")
            assertTrue(transport.requests.single().url.contains("Bob%5C%27s"))
        }

    @Test
    fun createFolderPostsFolderMetadata() =
        runTest {
            transport.enqueue(ok("""{"id":"folder1","name":"Ostomate backups"}"""))

            assertEquals("folder1", client.createFolder(TOKEN, "Ostomate backups"))

            val request = transport.requests.single()
            assertEquals("POST", request.method)
            assertEquals("https://www.googleapis.com/drive/v3/files?fields=id", request.url)
            assertEquals("application/json; charset=UTF-8", request.headers["Content-Type"])
            assertEquals("Bearer $TOKEN", request.headers["Authorization"])
            assertEquals(
                """{"name":"Ostomate backups","mimeType":"application/vnd.google-apps.folder"}""",
                request.body,
            )
        }

    @Test
    fun uploadSendsAMultipartRequestIntoTheFolder() =
        runTest {
            transport.enqueue(ok("""{"id":"file1"}"""))

            val id = client.uploadJson(TOKEN, folderId = "folder1", fileName = "b.json", content = """{"x":1}""")

            assertEquals("file1", id)
            val request = transport.requests.single()
            assertEquals("POST", request.method)
            assertEquals("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", request.url)
            assertEquals("multipart/related; boundary=ostomate_backup_part_0", request.headers["Content-Type"])
            assertEquals("Bearer $TOKEN", request.headers["Authorization"])
            assertEquals(
                "--ostomate_backup_part_0\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
                    """{"name":"b.json","mimeType":"application/json","parents":["folder1"]}""" + "\r\n" +
                    "--ostomate_backup_part_0\r\n" +
                    "Content-Type: application/json\r\n\r\n" +
                    """{"x":1}""" + "\r\n" +
                    "--ostomate_backup_part_0--\r\n",
                request.body,
            )
        }

    @Test
    fun listFilesFollowsPagesAndParsesCreationTimes() =
        runTest {
            transport.enqueue(
                ok(
                    """
                    {"nextPageToken":"p2","files":[
                      {"id":"a","name":"ostomate_backup_1.json","createdTime":"1970-01-01T00:00:01.000Z","extra":true}
                    ]}
                    """,
                ),
                ok("""{"files":[{"id":"b","name":"x.json","createdTime":"not a time"},{"id":"c","name":"y.json"}]}"""),
            )

            val files = client.listFiles(TOKEN, "folder1")

            assertEquals(
                listOf(
                    DriveFile("a", "ostomate_backup_1.json", 1_000L),
                    DriveFile("b", "x.json", null),
                    DriveFile("c", "y.json", null),
                ),
                files,
            )
            val (first, second) = transport.requests
            val query =
                "q=%27folder1%27%20in%20parents%20and%20trashed%20%3D%20false" +
                    "&spaces=drive&pageSize=100&fields=nextPageToken%2Cfiles%28id%2Cname%2CcreatedTime%29"
            assertEquals("https://www.googleapis.com/drive/v3/files?$query", first.url)
            assertEquals("https://www.googleapis.com/drive/v3/files?$query&pageToken=p2", second.url)
            assertEquals("Bearer $TOKEN", second.headers["Authorization"])
        }

    @Test
    fun listFilesStopsAfterAPageCapEvenIfDriveKeepsPaging() =
        runTest {
            repeat(50) { transport.enqueue(ok("""{"nextPageToken":"again","files":[]}""")) }

            client.listFiles(TOKEN, "folder1")

            assertEquals(20, transport.requests.size)
        }

    @Test
    fun deleteSendsDeleteForTheFile() =
        runTest {
            transport.enqueue(HttpResponse(204, ""))

            client.delete(TOKEN, "file9")

            val request = transport.requests.single()
            assertEquals("DELETE", request.method)
            assertEquals("https://www.googleapis.com/drive/v3/files/file9", request.url)
            assertEquals("Bearer $TOKEN", request.headers["Authorization"])
        }

    @Test
    fun anyNon2xxStatusBecomesADriveApiException() =
        runTest {
            transport.enqueue(HttpResponse(401, """{"error":{}}"""))
            assertEquals(401, assertFailsWith<DriveApiException> { client.findFolder(TOKEN, "f") }.status)

            transport.enqueue(HttpResponse(199, ""))
            assertEquals(199, assertFailsWith<DriveApiException> { client.delete(TOKEN, "x") }.status)

            transport.enqueue(HttpResponse(300, ""))
            assertEquals(300, assertFailsWith<DriveApiException> { client.createFolder(TOKEN, "f") }.status)

            transport.enqueue(HttpResponse(299, """{"id":"ok"}"""))
            assertEquals("ok", client.createFolder(TOKEN, "f"))
        }

    @Test
    fun aResponseWithoutAnIdIsAnError() =
        runTest {
            transport.enqueue(ok("""{"name":"no id"}"""))
            assertFailsWith<DriveApiException> { client.createFolder(TOKEN, "f") }
        }

    @Test
    fun percentEncodingLeavesOnlyUnreservedCharacters() {
        assertEquals("AZaz09-_.~", percentEncode("AZaz09-_.~"))
        assertEquals("%20%27%3D%2F%28%29%2C%25%2B", percentEncode(" '=/(),%+"))
        assertEquals("%C3%A9", percentEncode("é"))
    }

    private fun ok(body: String) = HttpResponse(200, body.trimIndent())

    private companion object {
        const val TOKEN = "tok"
    }
}
