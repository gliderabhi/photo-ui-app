package com.sevis.photos.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sevis.photos.AppState
import com.sevis.photos.data.AlbumResponse
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.PhotoResponse
import kotlinx.coroutines.launch

@Composable
fun GalleryScreen(
    api: PhotoApi,
    baseUrl: String,
    favoritesOnly: Boolean,
    onFavoritesChange: (Set<Int>) -> Unit
) {
    val scope = rememberCoroutineScope()

    var allGroups by remember { mutableStateOf<List<Pair<String, List<PhotoResponse>>>>(emptyList()) }
    var albums by remember { mutableStateOf<List<AlbumResponse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var bulkMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showAlbumPicker by remember { mutableStateOf(false) }
    var newAlbumName by remember { mutableStateOf("") }
    var lightboxPhoto by remember { mutableStateOf<PhotoResponse?>(null) }
    var showInfoPanel by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(AppState.favoriteIds.toSet()) }

    fun reload() {
        loading = true
        scope.launch {
            runCatching { api.listPhotos() }
                .onSuccess { data ->
                    allGroups = data.map { it.date to it.photos }
                }
            loading = false
        }
        scope.launch {
            runCatching { api.listAlbums() }.onSuccess { albums = it }
        }
    }

    LaunchedEffect(Unit) { reload() }

    val filteredGroups = remember(allGroups, searchQuery, favoritesOnly, favorites) {
        var groups = allGroups
        if (favoritesOnly) {
            groups = groups.map { (date, photos) -> date to photos.filter { favorites.contains(it.id) } }
                .filter { it.second.isNotEmpty() }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.lowercase()
            groups = groups.map { (date, photos) ->
                date to photos.filter {
                    it.originalFilename.lowercase().contains(q) || date.contains(q)
                }
            }.filter { it.second.isNotEmpty() }
        }
        groups
    }

    val flatPhotos = remember(filteredGroups) { filteredGroups.flatMap { it.second } }

    fun toggleFavorite(photo: PhotoResponse) {
        val newFavs = AppState.favoriteIds.toMutableSet()
        if (newFavs.contains(photo.id)) newFavs.remove(photo.id) else newFavs.add(photo.id)
        AppState.favoriteIds.clear()
        AppState.favoriteIds.addAll(newFavs)
        favorites = newFavs.toSet()
        onFavoritesChange(newFavs)
    }

    // Lightbox dialog
    if (lightboxPhoto != null) {
        val photo = lightboxPhoto!!
        val currentIndex = flatPhotos.indexOf(photo)
        Dialog(
            onDismissRequest = { lightboxPhoto = null; showInfoPanel = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0000000))
                    .clickable { lightboxPhoto = null; showInfoPanel = false }
            ) {
                // Image
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = "$baseUrl/photo-service/api/photos/${photo.id}/content",
                        contentDescription = photo.originalFilename,
                        modifier = Modifier
                            .fillMaxWidth(if (showInfoPanel) 0.7f else 0.95f)
                            .fillMaxHeight(0.9f)
                            .clickable(enabled = false) {},
                        contentScale = ContentScale.Fit
                    )
                }

                // Top bar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(photo.originalFilename, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        IconButton(onClick = { toggleFavorite(photo) }) {
                            Icon(
                                if (favorites.contains(photo.id)) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (favorites.contains(photo.id)) Color(0xFFEA4335) else Color.White
                            )
                        }
                        IconButton(onClick = { showInfoPanel = !showInfoPanel }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { lightboxPhoto = null; showInfoPanel = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Prev arrow
                if (currentIndex > 0) {
                    IconButton(
                        onClick = { lightboxPhoto = flatPhotos[currentIndex - 1] },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronLeft, contentDescription = "Previous",
                            tint = Color.White, modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Next arrow
                if (currentIndex < flatPhotos.size - 1) {
                    IconButton(
                        onClick = { lightboxPhoto = flatPhotos[currentIndex + 1] },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = if (showInfoPanel) 300.dp else 16.dp)
                    ) {
                        Icon(
                            Icons.Default.ChevronRight, contentDescription = "Next",
                            tint = Color.White, modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Info panel
                if (showInfoPanel) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(280.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF1A1A2E))
                            .padding(20.dp)
                            .clickable(enabled = false) {},
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(48.dp))
                        Text("Photo Info", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        InfoRow("Filename", photo.originalFilename)
                        InfoRow("File size", formatSize(photo.fileSize))
                        InfoRow("Type", photo.contentType)
                        InfoRow("Uploaded", photo.uploadedAt.take(19).replace("T", " "))
                        InfoRow("Photo ID", "#${photo.id}")
                    }
                }
            }
        }
    }

    // Album picker dialog
    if (showAlbumPicker) {
        AlertDialog(
            onDismissRequest = { showAlbumPicker = false },
            title = { Text("Add to Album") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newAlbumName,
                            onValueChange = { newAlbumName = it },
                            placeholder = { Text("New album name…") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            onClick = {
                                if (newAlbumName.isBlank()) return@Button
                                scope.launch {
                                    runCatching { api.createAlbum(newAlbumName.trim()) }
                                        .onSuccess { album ->
                                            api.addPhotosToAlbum(album.id, selectedIds.toList())
                                            showAlbumPicker = false
                                            selectedIds = emptySet()
                                            bulkMode = false
                                            newAlbumName = ""
                                            runCatching { api.listAlbums() }.onSuccess { albums = it }
                                        }
                                }
                            },
                            enabled = newAlbumName.isNotBlank()
                        ) { Text("Create") }
                    }
                    albums.forEach { album ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    runCatching { api.addPhotosToAlbum(album.id, selectedIds.toList()) }
                                        .onSuccess {
                                            showAlbumPicker = false
                                            selectedIds = emptySet()
                                            bulkMode = false
                                            runCatching { api.listAlbums() }.onSuccess { albums = it }
                                        }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(album.name)
                                Text("${album.photoCount} photos", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAlbumPicker = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: search + actions
        Surface(shadowElevation = 1.dp, color = Color.White) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name or date…") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                    )
                    Button(
                        onClick = { bulkMode = !bulkMode; selectedIds = emptySet() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (bulkMode) Color(0xFFE8F0FE) else Color(0xFFF1F3F4),
                            contentColor = if (bulkMode) Color(0xFF1A73E8) else Color(0xFF5F6368)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) { Text(if (bulkMode) "Cancel" else "Select") }
                }
            }
        }

        // Bulk action bar
        if (bulkMode && selectedIds.isNotEmpty()) {
            Surface(color = Color(0xFFE8F0FE)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${selectedIds.size} selected", color = Color(0xFF1A73E8), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { showAlbumPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                    ) { Text("Add to Album") }
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { api.bulkDeletePhotos(selectedIds.toList()) }
                                    .onSuccess { reload(); selectedIds = emptySet(); bulkMode = false }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA4335))
                    ) { Text("Delete (${selectedIds.size})") }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredGroups.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Text(if (searchQuery.isNotBlank() || favoritesOnly) "No photos match" else "No photos yet", fontSize = 17.sp, color = Color(0xFF202124))
                    Text(
                        if (searchQuery.isNotBlank() || favoritesOnly) "Try a different search or filter" else "Upload some photos to get started",
                        fontSize = 13.sp, color = Color(0xFF5F6368)
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                filteredGroups.forEach { (date, photos) ->
                    item(key = "header_$date") {
                        Text(
                            formatDateHeader(date),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF3C4043)
                        )
                    }
                    item(key = "grid_$date") {
                        PhotoGrid(
                            photos = photos,
                            baseUrl = baseUrl,
                            bulkMode = bulkMode,
                            selectedIds = selectedIds,
                            favorites = favorites,
                            onPhotoClick = { photo ->
                                if (bulkMode) {
                                    selectedIds = if (selectedIds.contains(photo.id))
                                        selectedIds - photo.id else selectedIds + photo.id
                                } else {
                                    lightboxPhoto = photo
                                    showInfoPanel = false
                                }
                            },
                            onFavoriteClick = { photo -> toggleFavorite(photo) },
                            onDeleteClick = { photo ->
                                scope.launch {
                                    runCatching { api.deletePhoto(photo.id) }.onSuccess { reload() }
                                }
                            }
                        )
                    }
                }
            }
        }
        } // end Box weight(1f)
    }
}

@Composable
private fun PhotoGrid(
    photos: List<PhotoResponse>,
    baseUrl: String,
    bulkMode: Boolean,
    selectedIds: Set<Int>,
    favorites: Set<Int>,
    onPhotoClick: (PhotoResponse) -> Unit,
    onFavoriteClick: (PhotoResponse) -> Unit,
    onDeleteClick: (PhotoResponse) -> Unit
) {
    val columns = 3
    val chunked = photos.chunked(columns)
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        chunked.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { photo ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onPhotoClick(photo) }
                    ) {
                        AsyncImage(
                            model = "$baseUrl/photo-service/api/photos/${photo.id}/content",
                            contentDescription = photo.originalFilename,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        // Bulk select checkbox
                        if (bulkMode) {
                            Box(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedIds.contains(photo.id)) Color(0xFF1A73E8) else Color.Black.copy(alpha = 0.35f))
                                    .align(Alignment.TopStart),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedIds.contains(photo.id)) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }

                        // Favorite + Delete on non-bulk
                        if (!bulkMode) {
                            if (favorites.contains(photo.id)) {
                                IconButton(
                                    onClick = { onFavoriteClick(photo) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color(0xFFEA4335), modifier = Modifier.size(16.dp))
                                }
                            }
                            Row(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                SmallIconBtn(icon = Icons.Default.Delete, tint = Color.White) { onDeleteClick(photo) }
                            }
                        }
                    }
                }
                // Fill remaining cells in last row
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun SmallIconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), fontSize = 11.sp, color = Color(0xFF64748B), letterSpacing = 0.5.sp)
        Text(value, fontSize = 13.sp, color = Color(0xFFE2E8F0))
    }
}

private fun formatDateHeader(dateStr: String): String {
    return try {
        val parts = dateStr.split("-")
        val months = listOf("", "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December")
        "${months[parts[1].toInt()]} ${parts[2].toInt()}, ${parts[0]}"
    } catch (e: Exception) { dateStr }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.2f MB".format(bytes / (1024.0 * 1024))
}
