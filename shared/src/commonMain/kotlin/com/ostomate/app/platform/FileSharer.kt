package com.ostomate.app.platform

/**
 * Platform-specific file operations for backup/restore.
 * Import callback: receives file content as a String (UTF-8), or null on cancel/error.
 * Export callback: receives true on success, false on failure.
 */
expect class FileSharer {
    fun shareText(
        content: String,
        fileName: String,
        mimeType: String,
    )

    fun shareBytes(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    )

    fun openFilePicker(
        mimeType: String,
        onResult: (String?) -> Unit,
    )
}
