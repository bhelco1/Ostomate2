package com.ostomate.app.ui.settings

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun plainTextClip(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("Ostomate link", text))
