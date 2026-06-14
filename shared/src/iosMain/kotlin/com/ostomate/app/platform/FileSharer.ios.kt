package com.ostimate.app.platform

actual class FileSharer {
    actual fun shareText(
        content: String,
        fileName: String,
        mimeType: String,
    ) {
        // TODO(Phase 3): implement iOS share sheet via UIActivityViewController
    }

    actual fun openFilePicker(
        mimeType: String,
        onResult: (String?) -> Unit,
    ) {
        // TODO(Phase 3): implement iOS document picker via UIDocumentPickerViewController
        onResult(null)
    }
}
