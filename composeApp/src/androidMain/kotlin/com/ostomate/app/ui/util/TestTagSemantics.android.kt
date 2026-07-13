package com.ostomate.app.ui.util

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.exposeTestTagsAsResourceIds(): Modifier = semantics { testTagsAsResourceId = true }
