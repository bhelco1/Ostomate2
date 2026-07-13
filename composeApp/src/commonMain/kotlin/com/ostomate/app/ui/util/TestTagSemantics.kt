package com.ostomate.app.ui.util

import androidx.compose.ui.Modifier

/**
 * Publishes this subtree's Compose testTags as accessibility resource-ids.
 *
 * Required on every **dialog**. A Compose dialog is a separate platform window with its own
 * composition root, so the opt-in applied once at the Activity root does not reach inside it —
 * which made every `id:` selector within a dialog invisible to UiAutomator (and so to Maestro),
 * while text inside the same dialog matched fine.
 *
 * No-op on iOS, where Compose already exports testTags as accessibility identifiers.
 */
expect fun Modifier.exposeTestTagsAsResourceIds(): Modifier
