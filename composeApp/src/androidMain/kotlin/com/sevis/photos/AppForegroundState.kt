package com.sevis.photos

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * App-wide foreground/background state, updated by a ProcessLifecycleOwner
 * observer registered in PhotosApplication. Heavy on-device work (face
 * detection in LocalScanWorker) checks this to avoid running while the user
 * is actively looking at the UI.
 */
object AppForegroundState {
    val isForeground = MutableStateFlow(true)
}
