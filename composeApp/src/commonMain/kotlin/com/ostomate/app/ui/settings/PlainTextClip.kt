package com.ostomate.app.ui.settings

import androidx.compose.ui.platform.ClipEntry

/** A clipboard entry holding [text]; ClipEntry is built differently on each platform. */
expect fun plainTextClip(text: String): ClipEntry
