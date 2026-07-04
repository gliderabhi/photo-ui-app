package com.sevis.photos

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.composed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material3's default focus indication (a faint ripple/overlay) is nearly
 * invisible on a TV viewed from a couch, unlike touch devices where the
 * pointer itself shows what's selected. D-pad navigation needs a loud,
 * unambiguous highlight instead.
 */
fun Modifier.tvFocusRing(cornerRadius: Dp = 8.dp): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused }
        .then(
            if (focused) {
                Modifier.border(3.dp, Color(0xFF60A5FA), RoundedCornerShape(cornerRadius))
            } else {
                Modifier
            }
        )
}
