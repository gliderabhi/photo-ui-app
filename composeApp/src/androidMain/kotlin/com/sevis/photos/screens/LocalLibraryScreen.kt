package com.sevis.photos.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.sevis.photos.data.local.LocalMediaEntity
import com.sevis.photos.data.local.PhotosDatabase
import com.sevis.photos.localscan.LocalScanWorker
import com.sevis.photos.ui.GlassColors
import com.sevis.photos.ui.GlassPageBackground
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * The on-device Gallery — the app's default/home content. Photos are read straight
 * from MediaStore into the local Room DB by LocalScanWorker, which also runs
 * on-device ML Kit face detection (background-only, see AppForegroundState).
 * Grouping (by date vs. by place) is controlled from ShellScreen's FAB menu, not
 * locally — [groupByPlace] is hoisted so the FAB and this grid stay in sync.
 */
@Composable
fun LocalLibraryScreen(groupByPlace: Boolean) {
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

    val grouped = remember(media, groupByPlace) {
        if (groupByPlace) media.groupBy { it.placeName ?: "Unknown location" }
        else media.groupBy { monthYearLabel(it.dateTakenMillis) }
    }

    // Collapsed-group state resets when the grouping mode changes — a label from
    // "By Date" grouping has no meaning once switched to "By Place" grouping.
    var collapsed by remember(groupByPlace) { mutableStateOf(setOf<String>()) }
    val allCollapsed = grouped.isNotEmpty() && grouped.keys.all { it in collapsed }
    var lightboxPhoto by remember { mutableStateOf<LocalMediaEntity?>(null) }

    if (lightboxPhoto != null) {
        LocalPhotoLightbox(photos = media, initialPhoto = lightboxPhoto!!, onDismiss = { lightboxPhoto = null })
    }

    Column(modifier = Modifier.fillMaxSize().background(GlassPageBackground)) {
        if (media.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassColors.AccentBlue)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    if (allCollapsed) "Expand all" else "Collapse all",
                    modifier = Modifier.clickable {
                        collapsed = if (allCollapsed) emptySet() else grouped.keys.toSet()
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlassColors.AccentBlue
                )
            }
            // A single LazyVerticalGrid with full-width header items interspersed with
            // regular 1x1 photo cells — every cell is individually lazy, so a date group
            // with hundreds of photos no longer composes/decodes them all at once (the
            // previous plain Column/Row grid inside one LazyColumn item did exactly that,
            // and was the actual cause of the scroll lag, not image loading itself).
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalArrangement = Arrangement.spacedBy(1.5.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                grouped.forEach { (label, photos) ->
                    val isCollapsed = label in collapsed
                    item(key = "header_$label", span = { GridItemSpan(maxLineSpan) }) {
                        DateHeader(
                            label = label,
                            collapsed = isCollapsed,
                            onToggle = {
                                collapsed = if (isCollapsed) collapsed - label else collapsed + label
                            }
                        )
                    }
                    if (!isCollapsed) {
                        photoGridItems(photos, onPhotoClick = { lightboxPhoto = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(label: String, collapsed: Boolean, onToggle: () -> Unit) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, label = "chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = if (collapsed) "Expand" else "Collapse",
            tint = GlassColors.TextSecondary,
            modifier = Modifier.size(18.dp).rotate(rotation)
        )
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = GlassColors.TextPrimary)
    }
}

/**
 * Adds one lazy grid cell per photo — shared by the main Gallery, PersonPhotosScreen,
 * and LocalAlbumPhotosScreen so every photo grid in the app is genuinely virtualized,
 * not just the outer date/group list.
 */
internal fun LazyGridScope.photoGridItems(photos: List<LocalMediaEntity>, onPhotoClick: (LocalMediaEntity) -> Unit) {
    items(photos, key = { it.id }) { photo ->
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(Uri.parse(photo.uri))
                // Grid cells are small — decoding at a capped size avoids reading full-
                // resolution camera photos into memory just to shrink them on screen.
                .size(300, 300)
                .build(),
            contentDescription = photo.displayName,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable { onPhotoClick(photo) },
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}

/**
 * Full-screen photo viewer — entirely gesture-driven, no nav buttons:
 * - Pinch, or drag while already zoomed, to zoom/pan.
 * - Double-tap to toggle zoom.
 * - Swipe left/right (only while not zoomed) to move to the next/previous photo.
 * - Swipe up (only while not zoomed) to reveal a details panel below the image;
 *   swipe down to dismiss the details panel, or to close the viewer entirely
 *   if the details panel isn't showing.
 * All of this is one gesture recognizer rather than several independent
 * detectXGestures — stacking separate detectors on the same pointer stream causes
 * detectTransformGestures to consume every touch move for its own pan tracking,
 * starving sibling detectors (e.g. a horizontal-drag detector) of events entirely.
 */
@Composable
internal fun LocalPhotoLightbox(photos: List<LocalMediaEntity>, initialPhoto: LocalMediaEntity, onDismiss: () -> Unit) {
    var current by remember(initialPhoto) { mutableStateOf(initialPhoto) }
    val currentIndex = photos.indexOfFirst { it.id == current.id }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            var scale by remember(current) { mutableFloatStateOf(1f) }
            var offset by remember(current) { mutableStateOf(Offset.Zero) }
            var showDetails by remember(current) { mutableStateOf(false) }

            AsyncImage(
                model = Uri.parse(current.uri),
                contentDescription = current.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(current) {
                        awaitEachGesture {
                            var panAccum = Offset.Zero
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (event.changes.size > 1 || scale > 1f) {
                                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                                    scale = newScale
                                    offset = if (newScale <= 1f) Offset.Zero else offset + panChange
                                    event.changes.forEach { it.consume() }
                                } else {
                                    panAccum += panChange
                                }
                            } while (event.changes.any { it.pressed })

                            if (scale <= 1f) {
                                val (dx, dy) = panAccum
                                when {
                                    kotlin.math.abs(dx) > kotlin.math.abs(dy) && kotlin.math.abs(dx) > 100f -> {
                                        if (dx < 0 && currentIndex < photos.size - 1) current = photos[currentIndex + 1]
                                        else if (dx > 0 && currentIndex > 0) current = photos[currentIndex - 1]
                                    }
                                    dy < -100f -> showDetails = true
                                    dy > 100f -> if (showDetails) showDetails = false else onDismiss()
                                }
                            }
                        }
                    }
                    .pointerInput(current) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        })
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            AnimatedVisibility(
                visible = showDetails,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PhotoDetailsPanel(current)
            }
        }
    }
}

@Composable
private fun PhotoDetailsPanel(photo: LocalMediaEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xE6111111))
            .navigationBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Details", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        DetailRow("Filename", photo.displayName)
        DetailRow("Date taken", formatFullDate(photo.dateTakenMillis))
        photo.bucketName?.let { DetailRow("Album", it) }
        if (photo.placeResolved) {
            DetailRow("Location", photo.placeName ?: "Unknown")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), fontSize = 11.sp, color = Color(0xFF9CA3AF), letterSpacing = 0.5.sp)
        Text(value, fontSize = 13.sp, color = Color(0xFFE2E8F0))
    }
}

private fun formatFullDate(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$month ${date.dayOfMonth}, ${date.year}"
}

private fun monthYearLabel(epochMillis: Long): String {
    val date = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$month ${date.year}"
}
