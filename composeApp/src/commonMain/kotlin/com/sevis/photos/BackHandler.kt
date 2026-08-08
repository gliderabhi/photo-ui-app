package com.sevis.photos

import androidx.compose.runtime.Composable

/**
 * Intercepts the system back gesture/button while [enabled], invoking [onBack] instead of
 * the default behavior. Android has a real system-level back gesture/button to hook into;
 * iOS has no equivalent for a single-Activity app driving its own internal pane state (no
 * navigation-controller push/pop happening here), so the iOS actual is a no-op — ShellScreen's
 * visible back button (see GlassTopBar) is the actual way back on iOS.
 */
@Composable
expect fun BackHandler(enabled: Boolean, onBack: () -> Unit)
