package com.sevis.photos.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.sevis.photos.data.local.LocalMediaEntity
import com.sevis.photos.data.local.PhotosDatabase
import com.sevis.photos.localscan.LocalScanWorker
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * A fully local, on-device photo library: nothing here ever calls the network. Photos are
 * read straight from MediaStore into the local Room DB by LocalScanWorker, which also runs
 * on-device ML Kit face detection — grouping/upload build on top of that local data.
 */
@Composable
fun LocalLibraryScreen() {
    val context = LocalContext.current
    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, readPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Text("Your photos, on this device", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Nothing is uploaded — this library stays on your phone unless you choose to upload.",
                    fontSize = 13.sp, color = Color(0xFF5F6368)
                )
                Button(onClick = { permissionLauncher.launch(readPermission) }) { Text("Grant photo access") }
            }
        }
        return
    }

    val db = remember { PhotosDatabase.get(context) }
    LaunchedEffect(Unit) { LocalScanWorker.runOnce(context) }

    val media by db.localMediaDao().observeAll().collectAsState(initial = emptyList())
    val faceCount by db.faceDao().observeTotalCount().collectAsState(initial = 0)

    val grouped = remember(media) {
        media.groupBy { monthYearLabel(it.dateTakenMillis) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(shadowElevation = 1.dp, color = Color(0xFFF4F7FC)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${media.size} photos on this device", fontSize = 13.sp, color = Color(0xFF3C4043))
                Text("$faceCount faces detected", fontSize = 13.sp, color = Color(0xFF5F6368))
            }
        }

        if (media.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                grouped.forEach { (label, photos) ->
                    item(key = "header_$label") {
                        Text(
                            label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF3C4043)
                        )
                    }
                    item(key = "grid_$label") {
                        LocalPhotoGrid(photos)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPhotoGrid(photos: List<LocalMediaEntity>) {
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
                    ) {
                        AsyncImage(
                            model = Uri.parse(photo.uri),
                            contentDescription = photo.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                }
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

private fun monthYearLabel(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$month ${date.year}"
}
