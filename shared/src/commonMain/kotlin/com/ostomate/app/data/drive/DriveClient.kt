package com.ostomate.app.data.drive

import com.ostomate.app.domain.drive.DriveFile
import com.ostomate.app.domain.drive.multipartBoundary
import com.ostomate.app.domain.drive.multipartRelatedBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id"
private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
private const val JSON_CONTENT_TYPE = "application/json; charset=UTF-8"
private const val FILE_FIELDS = "id,name,createdTime"
private const val PAGE_SIZE = 100

// The folder never holds more than ~15 files; this only stops a misbehaving server
// (a nextPageToken that never ends) from keeping a background job looping.
private const val MAX_PAGES = 20

private val HTTP_OK = 200..299

private val driveJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

@Serializable
private data class FileMetadata(
    val name: String,
    val mimeType: String,
    val parents: List<String>? = null,
)

@Serializable
private data class FileResource(
    val id: String? = null,
    val name: String? = null,
    val createdTime: String? = null,
)

@Serializable
private data class FileList(
    val files: List<FileResource> = emptyList(),
    val nextPageToken: String? = null,
)

/**
 * The four Drive REST v3 calls the daily backup needs, over [HttpTransport]. Every request
 * carries only the access token for the drive.file scope, so it can see and change only the
 * files and folders this app created.
 */
class DriveClient(private val transport: HttpTransport) {
    /** The app's folder named [name]; if there are several, the oldest, so the choice is stable. */
    suspend fun findFolder(
        token: String,
        name: String,
    ): String? {
        val query =
            "name = '${escapeQueryLiteral(name)}' and mimeType = '$FOLDER_MIME_TYPE' and trashed = false"
        val url = "$FILES_URL?q=${percentEncode(query)}&spaces=drive&fields=${percentEncode("files($FILE_FIELDS)")}"
        val list = decode<FileList>(send(HttpRequest("GET", url, authHeader(token))))
        return list.files
            .mapNotNull { it.toDriveFile() }
            .minWithOrNull(compareBy(nullsLast()) { it.createdAtMillis })
            ?.id
    }

    suspend fun createFolder(
        token: String,
        name: String,
    ): String {
        val metadata = driveJson.encodeToString(FileMetadata(name = name, mimeType = FOLDER_MIME_TYPE))
        val request =
            HttpRequest(
                method = "POST",
                url = "$FILES_URL?fields=id",
                headers = authHeader(token) + ("Content-Type" to JSON_CONTENT_TYPE),
                body = metadata,
            )
        return requireId(send(request))
    }

    /** Uploads [content] as a new JSON file in [folderId]; returns the new file's id. */
    suspend fun uploadJson(
        token: String,
        folderId: String,
        fileName: String,
        content: String,
    ): String {
        val metadata =
            driveJson.encodeToString(
                FileMetadata(name = fileName, mimeType = "application/json", parents = listOf(folderId)),
            )
        val boundary = multipartBoundary(metadata, content)
        val request =
            HttpRequest(
                method = "POST",
                url = UPLOAD_URL,
                headers = authHeader(token) + ("Content-Type" to "multipart/related; boundary=$boundary"),
                body = multipartRelatedBody(boundary, metadata, "application/json", content),
            )
        return requireId(send(request))
    }

    /** Every non-trashed file in [folderId]. */
    suspend fun listFiles(
        token: String,
        folderId: String,
    ): List<DriveFile> {
        val query = "'${escapeQueryLiteral(folderId)}' in parents and trashed = false"
        val baseUrl =
            "$FILES_URL?q=${percentEncode(query)}&spaces=drive&pageSize=$PAGE_SIZE" +
                "&fields=${percentEncode("nextPageToken,files($FILE_FIELDS)")}"
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        var pages = 0
        do {
            val url = pageToken?.let { "$baseUrl&pageToken=${percentEncode(it)}" } ?: baseUrl
            val page = decode<FileList>(send(HttpRequest("GET", url, authHeader(token))))
            files += page.files.mapNotNull { it.toDriveFile() }
            pageToken = page.nextPageToken
            pages++
        } while (pageToken != null && pages < MAX_PAGES)
        return files
    }

    suspend fun delete(
        token: String,
        fileId: String,
    ) {
        send(HttpRequest("DELETE", "$FILES_URL/${percentEncode(fileId)}", authHeader(token)))
    }

    private suspend fun send(request: HttpRequest): HttpResponse {
        val response = transport.execute(request)
        if (response.status !in HTTP_OK) {
            throw DriveApiException(response.status, "Drive ${request.method} answered ${response.status}")
        }
        return response
    }

    private fun requireId(response: HttpResponse): String =
        decode<FileResource>(response).id
            ?: throw DriveApiException(response.status, "Drive response had no file id")

    private inline fun <reified T> decode(response: HttpResponse): T = driveJson.decodeFromString<T>(response.body)

    private fun authHeader(token: String) = mapOf("Authorization" to "Bearer $token")
}

private fun FileResource.toDriveFile(): DriveFile? {
    val id = id ?: return null
    val createdAt = createdTime?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
    return DriveFile(id = id, name = name.orEmpty(), createdAtMillis = createdAt)
}

/** Drive query string literals escape a quote or backslash with a backslash. */
private fun escapeQueryLiteral(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")

/** RFC 3986 percent-encoding of a query value: everything but unreserved characters. */
fun percentEncode(value: String): String =
    buildString {
        for (byte in value.encodeToByteArray()) {
            val c = (byte.toInt() and BYTE_MASK).toChar()
            if (c.isLetterOrDigit() && c.code < ASCII_LIMIT || c in UNRESERVED_MARKS) {
                append(c)
            } else {
                append('%')
                append(HEX[(byte.toInt() shr NIBBLE) and NIBBLE_MASK])
                append(HEX[byte.toInt() and NIBBLE_MASK])
            }
        }
    }

private const val HEX = "0123456789ABCDEF"
private const val UNRESERVED_MARKS = "-_.~"
private const val BYTE_MASK = 0xFF
private const val ASCII_LIMIT = 0x80
private const val NIBBLE = 4
private const val NIBBLE_MASK = 0x0F
