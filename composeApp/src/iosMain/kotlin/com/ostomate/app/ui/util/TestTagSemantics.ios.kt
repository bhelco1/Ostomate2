package com.ostomate.app.ui.util

import androidx.compose.ui.Modifier

// Compose on iOS already exports testTags as accessibility identifiers — Maestro resolves
// `id:` selectors inside iOS dialogs without any opt-in, so there is nothing to do here.
actual fun Modifier.exposeTestTagsAsResourceIds(): Modifier = this
