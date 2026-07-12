package com.ostomate.app.data.diagnostics

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

private const val DIAGNOSTIC_FILE_NAME = "ostomate_diagnostics.log"

/** App-private diagnostic file in the app's Documents directory (not synced anywhere). */
@OptIn(ExperimentalForeignApi::class)
private class FileDiagnosticLogStore(
    private val path: String,
) : DiagnosticLogStore {
    override fun read(): String =
        NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) ?: ""

    override fun write(content: String) {
        (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun diagnosticLogStore(): DiagnosticLogStore {
    val documentsUrl =
        NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
    val basePath = requireNotNull(documentsUrl?.path) { "Could not resolve documents directory" }
    return FileDiagnosticLogStore("$basePath/$DIAGNOSTIC_FILE_NAME")
}
