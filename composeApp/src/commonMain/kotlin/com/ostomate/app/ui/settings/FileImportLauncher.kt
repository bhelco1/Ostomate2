package com.ostimate.app.ui.settings

import androidx.compose.runtime.Composable

/**
 * Platform-specific composable that shows a system file picker and returns the
 * selected file's content as a String via [onContent].
 * [trigger] increments to open the picker; the composable registers side effects.
 */
@Composable
expect fun FileImportLauncher(
    trigger: Int,
    mimeType: String,
    onContent: (String?) -> Unit,
)
