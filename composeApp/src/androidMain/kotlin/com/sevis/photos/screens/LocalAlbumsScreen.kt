package com.sevis.photos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sevis.photos.data.local.LocalMediaEntity
import com.sevis.photos.data.local.PhotosDatabase
import com.sevis.photos.ui.GlassColors
import com.sevis.photos.ui.GlassSurface

private data class Album(val name: String, val photos: List<LocalMediaEntity>)

/** On-device photos grouped by their MediaStore folder (e.g. "Camera", "WhatsApp Images"). */
@Composable
fun LocalAlbumsScreen(onBack: () -> Unit, onAlbumClick: (String) -> Unit) {
    val context = LocalContext.current
    val db = remember { PhotosDatabase.get(context) }
    val media by db.localMediaDao().observeAll().collectAsState(initial = emptyList())

    val albums = remember(media) {
        media.groupBy { it.bucketName ?: "Other" }
            .map { (name, photos) -> Album(name, photos) }
            .sortedByDescending { it.photos.size }
    }

    Column(modifier = Modifier.fillMaxSize().background(com.sevis.photos.ui.GlassPageBackground)) {
        PaneHeader(title = "Albums", subtitle = "${albums.size} folder${if (albums.size != 1) "s" else ""}", onBack = onBack)

        if (media.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassColors.AccentBlue)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(albums, key = { it.name }) { album ->
                    AlbumCell(album = album, onClick = { onAlbumClick(album.name) })
                }
            }
        }
    }
}

@Composable
private fun AlbumCell(album: Album, onClick: () -> Unit) {
    GlassSurface(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), elevation = 4.dp) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val cover = album.photos.firstOrNull()
                if (cover != null) {
                    AsyncImage(
                        model = Uri.parse(cover.uri),
                        contentDescription = album.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = GlassColors.AccentBlue)
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(album.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.TextPrimary, maxLines = 1)
                Text("${album.photos.size} photos", fontSize = 11.sp, color = GlassColors.TextSecondary)
            }
        }
    }
}

/** All photos in a given on-device folder/bucket, reusing the Gallery grid look. */
@Composable
fun LocalAlbumPhotosScreen(bucketName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { PhotosDatabase.get(context) }
    val media by db.localMediaDao().observeAll().collectAsState(initial = emptyList())
    val photos = remember(media, bucketName) { media.filter { (it.bucketName ?: "Other") == bucketName } }
    var lightboxPhoto by remember { mutableStateOf<LocalMediaEntity?>(null) }

    if (lightboxPhoto != null) {
        LocalPhotoLightbox(photos = photos, initialPhoto = lightboxPhoto!!, onDismiss = { lightboxPhoto = null })
    }

    Column(modifier = Modifier.fillMaxSize().background(com.sevis.photos.ui.GlassPageBackground)) {
        PaneHeader(title = bucketName, subtitle = "${photos.size} photo${if (photos.size != 1) "s" else ""}", onBack = onBack)

        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No photos in this album", color = GlassColors.TextSecondary, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                photoGridItems(photos, onPhotoClick = { lightboxPhoto = it })
            }
        }
    }
}
