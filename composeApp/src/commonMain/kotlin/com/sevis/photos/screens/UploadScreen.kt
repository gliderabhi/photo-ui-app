package com.sevis.photos.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import coil3.compose.AsyncImage
import com.sevis.photos.data.ImageFile
import com.sevis.photos.data.PhotoResponse
import com.sevis.photos.data.UploadStatus
import com.sevis.photos.data.VideoFile
import com.sevis.photos.data.VideoResponse
import kotlinx.coroutines.launch

/** Kind-tagged so one queue/grid can hold both photo and video picks, mirroring
 *  the web app's unified upload page. `uri` doubles as the item's stable key. */
private sealed class MediaUploadItem {
    abstract val uri: String
    abstract val name: String
    abstract val status: UploadStatus
    abstract val errorMsg: String?

    abstract fun withStatus(status: UploadStatus, errorMsg: String? = null): MediaUploadItem

    data class Photo(val file: ImageFile, override val status: UploadStatus = UploadStatus.PENDING, override val errorMsg: String? = null) : MediaUploadItem() {
        override val uri get() = file.uri
        override val name get() = file.name
        override fun withStatus(status: UploadStatus, errorMsg: String?) = copy(status = status, errorMsg = errorMsg)
    }

    data class Video(val file: VideoFile, override val status: UploadStatus = UploadStatus.PENDING, override val errorMsg: String? = null) : MediaUploadItem() {
        override val uri get() = file.uri
        override val name get() = file.name
        override fun withStatus(status: UploadStatus, errorMsg: String?) = copy(status = status, errorMsg = errorMsg)
    }
}

@Composable
fun UploadScreen(
    pickedImages: List<ImageFile>,
    pickedVideos: List<VideoFile>,
    onPickMedia: () -> Unit,
    onClearPickedMedia: () -> Unit,
    uploadImage: suspend (ImageFile) -> Result<PhotoResponse>,
    uploadVideo: suspend (VideoFile) -> Result<VideoResponse>,
    autoUploadEnabled: Boolean,
    onAutoUploadToggle: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<List<MediaUploadItem>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }

    // Sync picked photos + videos into one queue
    LaunchedEffect(pickedImages, pickedVideos) {
        if (pickedImages.isNotEmpty() || pickedVideos.isNotEmpty()) {
            val existing = queue.map { it.uri }.toSet()
            val newPhotoItems = pickedImages.filter { !existing.contains(it.uri) }.map { MediaUploadItem.Photo(file = it) }
            val newVideoItems = pickedVideos.filter { !existing.contains(it.uri) }.map { MediaUploadItem.Video(file = it) }
            queue = queue + newPhotoItems + newVideoItems
        }
    }

    val pendingCount = queue.count { it.status == UploadStatus.PENDING }
    val doneCount = queue.count { it.status == UploadStatus.DONE }
    val allDone = queue.isNotEmpty() && queue.all { it.status == UploadStatus.DONE || it.status == UploadStatus.ERROR }

    fun uploadAll() {
        uploading = true
        val pending = queue.filter { it.status == UploadStatus.PENDING }
        if (pending.isEmpty()) { uploading = false; return }
        var finished = 0
        fun updateItem(uri: String, status: UploadStatus, errorMsg: String? = null) {
            queue = queue.map { if (it.uri == uri) it.withStatus(status, errorMsg) else it }
        }
        pending.forEach { item ->
            updateItem(item.uri, UploadStatus.UPLOADING)
            scope.launch {
                val result = when (item) {
                    is MediaUploadItem.Photo -> uploadImage(item.file)
                    is MediaUploadItem.Video -> uploadVideo(item.file)
                }
                result
                    .onSuccess { updateItem(item.uri, UploadStatus.DONE) }
                    .onFailure { e -> updateItem(item.uri, UploadStatus.ERROR, e.message ?: "Upload failed") }
                finished++
                if (finished == pending.size) uploading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Upload photos & videos", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF202124))
                Text("Add photos and videos to your private vault", fontSize = 13.sp, color = Color(0xFF5F6368))
            }
            if (doneCount > 0) {
                OutlinedButton(onClick = { queue = emptyList(); onClearPickedMedia() }) {
                    Text("Clear done")
                }
            }
        }

        // Auto-upload toggle
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Auto Upload",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF202124)
                    )
                    Text(
                        "Automatically upload new photos from your gallery",
                        fontSize = 12.sp,
                        color = Color(0xFF5F6368)
                    )
                }
                Switch(
                    checked = autoUploadEnabled,
                    onCheckedChange = onAutoUploadToggle,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF0A84FF))
                )
            }
        }

        if (autoUploadEnabled) {
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    Text("Auto-upload is active. New gallery photos will be synced.", fontSize = 13.sp, color = Color(0xFF2E7D32))
                }
            }
        }

        // Drop zone / pick button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFAFAFA))
                .border(2.dp, Color(0xFFDADCE0), RoundedCornerShape(16.dp))
                .clickable(onClick = onPickMedia),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFE8F0FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF0A84FF), modifier = Modifier.size(28.dp))
                }
                Text("Select from Gallery", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF202124))
                Text("Photos: PNG, JPG, WEBP · Videos: MP4, MOV, MKV", fontSize = 12.sp, color = Color(0xFF9AA0A6))
            }
        }

        // Queue
        if (queue.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${queue.size} file${if (queue.size != 1) "s" else ""} selected" +
                            if (doneCount > 0) " · $doneCount uploaded" else "",
                        fontSize = 14.sp,
                        color = Color(0xFF5F6368)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!allDone) {
                            OutlinedButton(
                                onClick = { queue = emptyList(); onClearPickedMedia() },
                                enabled = !uploading
                            ) { Text("Clear") }

                            Button(
                                onClick = ::uploadAll,
                                enabled = !uploading && pendingCount > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A84FF))
                            ) {
                                if (uploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Uploading…")
                                } else {
                                    Text("Upload $pendingCount file${if (pendingCount != 1) "s" else ""}")
                                }
                            }
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.height(320.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(queue, key = { it.uri }) { item ->
                        UploadThumbnail(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadThumbnail(item: MediaUploadItem) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFF1F3F4))
    ) {
        when (item) {
            is MediaUploadItem.Photo -> AsyncImage(
                model = item.file.uri,
                contentDescription = item.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            is MediaUploadItem.Video -> Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFFE8EAED)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Videocam, contentDescription = item.name, tint = Color(0xFF5F6368), modifier = Modifier.size(32.dp))
            }
        }

        when (item.status) {
            UploadStatus.UPLOADING -> Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = Color.White, strokeWidth = 3.dp)
            }
            UploadStatus.DONE -> Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF0A84FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            UploadStatus.ERROR -> Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFEA4335)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                    item.errorMsg?.let {
                        Text(it.take(20), fontSize = 10.sp, color = Color.White)
                    }
                }
            }
            UploadStatus.PENDING -> Unit
        }
    }
}
