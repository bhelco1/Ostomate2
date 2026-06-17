@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ostomate.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject

@Composable
actual fun FileImportLauncher(
    trigger: Int,
    mimeType: String,
    onContent: (String?) -> Unit,
) {
    val onContentRef = rememberUpdatedState(onContent)
    val delegate =
        remember {
            DocumentPickerDelegate { content -> onContentRef.value.invoke(content) }
        }

    LaunchedEffect(trigger) {
        if (trigger > 0) {
            val utType = UTType.typeWithMIMEType(mimeType) ?: return@LaunchedEffect
            val picker =
                UIDocumentPickerViewController(
                    forOpeningContentTypes = listOf(utType),
                    asCopy = true,
                )
            picker.delegate = delegate
            iosTopViewController()?.presentViewController(picker, animated = true, completion = null)
        }
    }
}

private class DocumentPickerDelegate(
    private val callback: (String?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        val content =
            url?.let {
                NSString.stringWithContentsOfURL(it, NSUTF8StringEncoding, null)
            }
        callback(content)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        callback(null)
    }
}

private fun iosTopViewController(): UIViewController? {
    val windowScene =
        UIApplication.sharedApplication
            .connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull() ?: return null
    var vc = windowScene.keyWindow?.rootViewController ?: return null
    while (vc.presentedViewController != null) vc = vc.presentedViewController!!
    return vc
}
