package com.sevis.photos

import androidx.compose.runtime.Composable

// No system back gesture/button to hook into for a single-VC app driving its own
// internal pane state — ShellScreen's visible back chevron (see GlassTopBar) is
// the actual way back on iOS.
@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
}
