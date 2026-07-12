package com.ostomate.app.data.diagnostics

import android.content.Context
import java.io.File

private const val DIAGNOSTIC_FILE_NAME = "ostomate_diagnostics.log"

/** App-private diagnostic file in the internal files dir (never on shared/external storage). */
private class FileDiagnosticLogStore(
    private val file: File,
) : DiagnosticLogStore {
    override fun read(): String = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")

    override fun write(content: String) {
        runCatching { file.writeText(content) }
    }
}

fun diagnosticLogStore(context: Context): DiagnosticLogStore =
    FileDiagnosticLogStore(File(context.filesDir, DIAGNOSTIC_FILE_NAME))
