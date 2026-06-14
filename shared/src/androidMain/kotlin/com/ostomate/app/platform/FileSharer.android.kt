package com.ostomate.app.platform

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import java.io.File

actual class FileSharer {
    private val activity: FragmentActivity
        get() =
            CurrentActivityHolder.activity as? FragmentActivity
                ?: error("FileSharer requires a FragmentActivity")

    actual fun shareText(
        content: String,
        fileName: String,
        mimeType: String,
    ) {
        val file = File(activity.cacheDir, fileName)
        file.writeText(content)
        val uri: Uri =
            FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file,
            )
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        activity.startActivity(Intent.createChooser(intent, "Share $fileName"))
    }

    actual fun openFilePicker(
        mimeType: String,
        onResult: (String?) -> Unit,
    ) {
        val intent =
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = mimeType
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        activity.startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
        pendingCallback = { uri ->
            if (uri == null) {
                onResult(null)
            } else {
                val content =
                    runCatching {
                        activity.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    }.getOrNull()
                onResult(content)
            }
        }
    }

    companion object {
        const val REQUEST_CODE_PICK_FILE = 42001
        var pendingCallback: ((Uri?) -> Unit)? = null
    }
}
