package com.sevis.photos

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** In-memory session state, populated at startup from platform persistence. */
object AppState {
    var token: String? by mutableStateOf(null)
    var folderPassword: String? by mutableStateOf(null)
    val favoriteIds: MutableSet<Int> = mutableSetOf()
    var autoUploadEnabled: Boolean by mutableStateOf(false)
}
