@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ostomate.app.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene

actual class FileSharer {
    actual fun shareText(
        content: String,
        fileName: String,
        mimeType: String,
    ) {
        val path = NSTemporaryDirectory() + fileName
        val bytes = content.encodeToByteArray()
        val nsData = bytes.usePinned { NSData.dataWithBytes(it.addressOf(0), bytes.size.toULong()) }
        NSFileManager.defaultManager.createFileAtPath(path, contents = nsData, attributes = null)
        val fileURL = NSURL.fileURLWithPath(path)
        val activityVC = UIActivityViewController(
            activityItems = listOf(fileURL),
            applicationActivities = null,
        )
        topViewController()?.presentViewController(activityVC, animated = true, completion = null)
    }

    actual fun openFilePicker(
        mimeType: String,
        onResult: (String?) -> Unit,
    ) {
        onResult(null) // file import is handled by the FileImportLauncher composable
    }
}

internal fun topViewController(): UIViewController? {
    val windowScene = UIApplication.sharedApplication
        .connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull() ?: return null
    var vc = windowScene.keyWindow?.rootViewController ?: return null
    while (vc.presentedViewController != null) vc = vc.presentedViewController!!
    return vc
}
