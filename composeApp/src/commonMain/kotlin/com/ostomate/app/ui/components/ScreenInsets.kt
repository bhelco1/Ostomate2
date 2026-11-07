package com.ostomate.app.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable

/**
 * The app shell applies only the side and bottom insets; the top belongs to each screen, so a
 * TopAppBar can paint its colour under the status bar (it takes that inset itself). Screens
 * without a TopAppBar pass this as their Scaffold's contentWindowInsets instead.
 */
val ShellInsets: WindowInsets
    @Composable get() =
        ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        )

/** See [ShellInsets]: the status-bar inset for a screen that has no TopAppBar. */
val TopBarlessScreenInsets: WindowInsets
    @Composable get() = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top)
