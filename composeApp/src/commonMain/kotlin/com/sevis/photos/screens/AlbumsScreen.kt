package com.sevis.photos.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.sevis.photos.data.AlbumResponse
import com.sevis.photos.data.PhotoApi
import kotlinx.coroutines.launch

@Composable
fun AlbumsScreen(api: PhotoApi, baseUrl: String) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var albums by remember { mutableStateOf<List<AlbumResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var newAlbumName by remember { mutableStateOf("") }
    var selectedAlbum by remember { mutableStateOf<AlbumResponse?>(null) }

    fun load() {
        loading = true
        scope.launch {
            runCatching { api.listAlbums() }.onSuccess { albums = it }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    // If album is selected, show detail view
    if (selectedAlbum != null) {
        AlbumDetailScreen(
            api = api,
            baseUrl = baseUrl,
            albumId = selectedAlbum!!.id,
            albumName = selectedAlbum!!.name,
            onBack = { selectedAlbum = null }
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Albums", fontSize = 22.sp, fontWeight = FontWeight.Normal, color = Color(0xFF202124))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    placeholder = { Text("New album name…") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.width(180.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newAlbumName.isNotBlank()) {
                            scope.launch {
                                runCatching { api.createAlbum(newAlbumName.trim()) }
                                    .onSuccess { albums = listOf(it) + albums; newAlbumName = "" }
                            }
                            focusManager.clearFocus()
                        }
                    })
                )
                Button(
                    onClick = {
                        if (newAlbumName.isBlank()) return@Button
                        scope.launch {
                            runCatching { api.createAlbum(newAlbumName.trim()) }
                                .onSuccess { albums = listOf(it) + albums; newAlbumName = "" }
                        }
                        focusManager.clearFocus()
                    },
                    enabled = newAlbumName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8)),
                    shape = RoundedCornerShape(24.dp)
                ) { Text("Create") }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Text("No albums yet", fontSize = 17.sp, color = Color(0xFF202124))
                    Text("Create an album to organise your photos", fontSize = 13.sp, color = Color(0xFF5F6368))
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumCard(
                        album = album,
                        baseUrl = baseUrl,
                        onClick = { selectedAlbum = album },
                        onDelete = {
                            scope.launch {
                                runCatching { api.deleteAlbum(album.id) }
                                    .onSuccess { albums = albums.filter { it.id != album.id } }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: AlbumResponse,
    baseUrl: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(1.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3F4))
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                if (album.coverPhoto != null) {
                    AsyncImage(
                        model = "$baseUrl/photo-service/api/photos/${album.coverPhoto.id}/content",
                        contentDescription = album.name,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color(0xFFBDC1C6))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(album.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF202124), maxLines = 1)
                    Text("${album.photoCount} photo${if (album.photoCount != 1) "s" else ""}", fontSize = 12.sp, color = Color(0xFF5F6368))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete album", modifier = Modifier.size(15.dp), tint = Color(0xFF9AA0A6))
                }
            }
        }
    }
}
