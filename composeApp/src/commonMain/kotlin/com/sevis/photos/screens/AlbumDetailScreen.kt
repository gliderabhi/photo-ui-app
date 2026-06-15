package com.sevis.photos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.PhotoResponse
import kotlinx.coroutines.launch

@Composable
fun AlbumDetailScreen(
    api: PhotoApi,
    baseUrl: String,
    albumId: Int,
    albumName: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<PhotoResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var lightboxPhoto by remember { mutableStateOf<PhotoResponse?>(null) }

    fun load() {
        loading = true
        scope.launch {
            runCatching { api.getAlbumPhotos(albumId) }.onSuccess { photos = it }
            loading = false
        }
    }

    LaunchedEffect(albumId) { load() }

    // Lightbox
    if (lightboxPhoto != null) {
        val photo = lightboxPhoto!!
        val idx = photos.indexOf(photo)
        Dialog(
            onDismissRequest = { lightboxPhoto = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0000000))
                    .clickable { lightboxPhoto = null }
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = "$baseUrl/photo-service/api/photos/${photo.id}/content",
                        contentDescription = photo.originalFilename,
                        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f).clickable(enabled = false) {},
                        contentScale = ContentScale.Fit
                    )
                }

                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(photo.originalFilename, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    IconButton(onClick = { lightboxPhoto = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Prev
                if (idx > 0) {
                    IconButton(
                        onClick = { lightboxPhoto = photos[idx - 1] },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }

                // Next
                if (idx < photos.size - 1) {
                    IconButton(
                        onClick = { lightboxPhoto = photos[idx + 1] },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Surface(shadowElevation = 1.dp, color = Color.White) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(albumName, fontSize = 20.sp, fontWeight = FontWeight.Normal, color = Color(0xFF202124), modifier = Modifier.weight(1f))
                Text("${photos.size} photo${if (photos.size != 1) "s" else ""}", fontSize = 13.sp, color = Color(0xFF5F6368))
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This album is empty", fontSize = 17.sp, color = Color(0xFF202124))
                    Text("Add photos from the Gallery using Select mode", fontSize = 13.sp, color = Color(0xFF5F6368))
                }
            }
        } else {
            val columns = 3
            val chunked = photos.chunked(columns)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                chunked.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { photo ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { lightboxPhoto = photo }
                            ) {
                                AsyncImage(
                                    model = "$baseUrl/photo-service/api/photos/${photo.id}/content",
                                    contentDescription = photo.originalFilename,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Remove from album button (bottom right on hover/overlay)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.55f))
                                        .clickable {
                                            scope.launch {
                                                runCatching { api.removePhotoFromAlbum(albumId, photo.id) }
                                                    .onSuccess { photos = photos.filter { it.id != photo.id } }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove from album",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        repeat(columns - row.size) {
                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}
