package com.sevis.photos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Small badge overlaid on a local Gallery thumbnail's corner once that photo is already on
 * the server (see uploadedFilenamesFrom()). Shared by Android's LocalLibraryScreen and iOS's
 * LocalGalleryScreens so the "already backed up" indicator looks identical on both platforms
 * — this replaced the separate "Cloud Gallery" pane, which duplicated the whole library as a
 * second, easy-to-confuse timeline instead of just marking what's already synced.
 */
@Composable
internal fun CloudUploadBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(18.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.CloudDone,
            contentDescription = "Uploaded to server",
            tint = Color.White,
            modifier = Modifier.size(12.dp)
        )
    }
}
