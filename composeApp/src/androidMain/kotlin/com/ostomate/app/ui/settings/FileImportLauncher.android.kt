package com.ostimate.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun FileImportLauncher(
    trigger: Int,
    mimeType: String,
    onContent: (String?) -> Unit,
) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val content =
                uri?.let { u ->
                    runCatching {
                        context.contentResolver.openInputStream(u)?.bufferedReader()?.readText()
                    }.getOrNull()
                }
            onContent(content)
        }
    LaunchedEffect(trigger) {
        if (trigger > 0) launcher.launch(mimeType)
    }
}
