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
import com.sevis.photos.data.UploadItem
import com.sevis.photos.data.UploadStatus
import kotlinx.coroutines.launch

@Composable
fun UploadScreen(
    pickedImages: List<ImageFile>,
    onPickImages: () -> Unit,
    onClearPickedImages: () -> Unit,
    uploadImage: suspend (ImageFile) -> Result<PhotoResponse>,
    autoUploadEnabled: Boolean,
    onAutoUploadToggle: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var queue by remember { mutableStateOf<List<UploadItem>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }

    // Sync picked images into queue
    LaunchedEffect(pickedImages) {
        if (pickedImages.isNotEmpty()) {
            val existing = queue.map { it.file.uri }.toSet()
            val newItems = pickedImages
                .filter { !existing.contains(it.uri) }
                .map { UploadItem(file = it) }
            queue = queue + newItems
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
        pending.forEach { item ->
            queue = queue.map { if (it.file.uri == item.file.uri) it.copy(status = UploadStatus.UPLOADING) else it }
            scope.launch {
                uploadImage(item.file)
                    .onSuccess {
                        queue = queue.map { if (it.file.uri == item.file.uri) it.copy(status = UploadStatus.DONE) else it }
                    }
                    .onFailure { e ->
                        queue = queue.map {
                            if (it.file.uri == item.file.uri) it.copy(status = UploadStatus.ERROR, errorMsg = e.message ?: "Upload failed")
                            else it
                        }
                    }
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
                Text("Upload photos", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF202124))
                Text("Add photos to your private vault", fontSize = 13.sp, color = Color(0xFF5F6368))
            }
            if (doneCount > 0) {
                OutlinedButton(onClick = { queue = emptyList(); onClearPickedImages() }) {
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
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF1A73E8))
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
                .clickable(onClick = onPickImages),
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
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF1A73E8), modifier = Modifier.size(28.dp))
                }
                Text("Select from Gallery", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color(0xFF202124))
                Text("PNG, JPG, WEBP · up to 20 MB each", fontSize = 12.sp, color = Color(0xFF9AA0A6))
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
                        "${queue.size} photo${if (queue.size != 1) "s" else ""} selected" +
                            if (doneCount > 0) " · $doneCount uploaded" else "",
                        fontSize = 14.sp,
                        color = Color(0xFF5F6368)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!allDone) {
                            OutlinedButton(
                                onClick = { queue = emptyList(); onClearPickedImages() },
                                enabled = !uploading
                            ) { Text("Clear") }

                            Button(
                                onClick = ::uploadAll,
                                enabled = !uploading && pendingCount > 0,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A73E8))
                            ) {
                                if (uploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Uploading…")
                                } else {
                                    Text("Upload $pendingCount photo${if (pendingCount != 1) "s" else ""}")
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
                    items(queue, key = { it.file.uri }) { item ->
                        UploadThumbnail(item = item)
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadThumbnail(item: UploadItem) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFF1F3F4))
    ) {
        AsyncImage(
            model = item.file.uri,
            contentDescription = item.file.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

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
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF1A73E8)),
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
