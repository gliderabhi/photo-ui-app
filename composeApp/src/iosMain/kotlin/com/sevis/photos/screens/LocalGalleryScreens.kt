package com.sevis.photos.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.sevis.photos.LocalAlbum
import com.sevis.photos.LocalMedia
import com.sevis.photos.fetchAlbumMedia
import com.sevis.photos.fetchAlbums
import com.sevis.photos.fetchAllMedia
import com.sevis.photos.hasPhotoLibraryAccess
import com.sevis.photos.requestPhotoLibraryAccess
import com.sevis.photos.thumbnailFileForId
import com.sevis.photos.ui.GlassColors
import com.sevis.photos.ui.GlassPageBackground
import com.sevis.photos.ui.GlassSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * iOS's counterpart to Android's LocalLibraryScreen — same shape (permission gate, date/place
 * grouping, collapsible headers, gesture-driven lightbox), PHPhotoLibrary instead of MediaStore/
 * Room. Photos load once per permission grant rather than via a live Room Flow — PHPhotoLibrary
 * has no Compose-friendly observe-all API as cheap as Room's, and re-running fetchAllMedia() is
 * itself a full re-scan (see LocalPhotoLibrary.kt's synchronous thumbnail caching) — so refresh
 * is manual (pull-to-refresh isn't wired up here; re-entering the pane re-fetches).
 */
@Composable
fun LocalLibraryScreen(groupByPlace: Boolean) {
    var hasPermission by remember { mutableStateOf(hasPhotoLibraryAccess()) }
    var media by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        media = withContext(Dispatchers.Default) { fetchAllMedia() }
        loading = false
    }

    if (!hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Text("Your photos, on this device", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Nothing is uploaded — this library stays on your phone unless you choose to upload.",
                    fontSize = 13.sp, color = Color(0xFF5F6368)
                )
                Button(onClick = {
                    scope.launch { hasPermission = requestPhotoLibraryAccess() }
                }) { Text("Grant photo access") }
            }
        }
        return
    }

    // Location-based grouping (Android's LocationLabeler reverse-geocode) isn't ported yet on
    // iOS — every photo groups under "Unknown location" until that's added.
    val grouped = remember(media, groupByPlace) {
        if (groupByPlace) media.groupBy { "Unknown location" }
        else media.groupBy { monthYearLabel(it.dateTakenMillis) }
    }

    var collapsed by remember(groupByPlace) { mutableStateOf(setOf<String>()) }
    val allCollapsed = grouped.isNotEmpty() && grouped.keys.all { it in collapsed }
    var lightboxPhoto by remember { mutableStateOf<LocalMedia?>(null) }

    if (lightboxPhoto != null) {
        LocalPhotoLightbox(photos = media, initialPhoto = lightboxPhoto!!, onDismiss = { lightboxPhoto = null })
    }

    Column(modifier = Modifier.fillMaxSize().background(GlassPageBackground)) {
        if (loading) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassColors.AccentBlue)
            }
        } else if (media.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("No photos on this device", color = GlassColors.TextSecondary, fontSize = 13.sp)
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
                        DateHeader(label = label, collapsed = isCollapsed, onToggle = {
                            collapsed = if (isCollapsed) collapsed - label else collapsed + label
                        })
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
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

/** Resolves (and disk-caches) one asset's thumbnail lazily, only while its composable is
 *  actually part of the composition — for a LazyVerticalGrid that means only currently
 *  visible cells (plus LazyVerticalGrid's own small prefetch buffer), not the whole library
 *  at once. This is what fixed both the "Gallery takes forever to appear" and the
 *  didReceiveMemoryWarning crash — see LocalPhotoLibrary.kt's fetchAllMedia() doc comment. */
@Composable
private fun rememberThumbnailPath(id: String): String? {
    val path by produceState<String?>(initialValue = null, id) {
        value = withContext(Dispatchers.Default) { thumbnailFileForId(id) }
    }
    return path
}

internal fun LazyGridScope.photoGridItems(photos: List<LocalMedia>, onPhotoClick: (LocalMedia) -> Unit) {
    items(photos, key = { it.id }) { photo ->
        val path = rememberThumbnailPath(photo.id)
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable { onPhotoClick(photo) }) {
            if (path != null) {
                AsyncImage(
                    model = path,
                    contentDescription = photo.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.15f)))
            }
        }
    }
}

/** Same gesture set as Android's: pinch/pan to zoom, double-tap to toggle zoom, swipe left/
 *  right to move between photos, swipe up for details, swipe down to dismiss. */
@Composable
internal fun LocalPhotoLightbox(photos: List<LocalMedia>, initialPhoto: LocalMedia, onDismiss: () -> Unit) {
    var current by remember(initialPhoto) { mutableStateOf(initialPhoto) }
    val currentIndex = photos.indexOfFirst { it.id == current.id }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            var scale by remember(current) { mutableFloatStateOf(1f) }
            var offset by remember(current) { mutableStateOf(Offset.Zero) }
            var showDetails by remember(current) { mutableStateOf(false) }
            val currentPath = rememberThumbnailPath(current.id)

            Box(
                contentAlignment = Alignment.Center,
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
                            if (scale > 1f) { scale = 1f; offset = Offset.Zero } else { scale = 2.5f }
                        })
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
            ) {
                if (currentPath != null) {
                    AsyncImage(
                        model = currentPath,
                        contentDescription = current.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp)) {
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
private fun PhotoDetailsPanel(photo: LocalMedia) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xE6111111)).navigationBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Details", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        DetailRow("Filename", photo.displayName)
        DetailRow("Date taken", formatFullDate(photo.dateTakenMillis))
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
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
    return NSDateFormatter().apply { dateFormat = "MMMM d, yyyy" }.stringFromDate(date)
}

private fun monthYearLabel(epochMillis: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)
    return NSDateFormatter().apply { dateFormat = "MMMM yyyy" }.stringFromDate(date)
}

// ── Albums ──────────────────────────────────────────────────────────────

/** On-device photos grouped into their PHAssetCollection albums — iOS's counterpart to
 *  Android's MediaStore-bucket grouping (they're different underlying concepts — real
 *  user albums here vs. storage folders there — but read the same in the UI). */
@Composable
fun LocalAlbumsScreen(onBack: () -> Unit, onAlbumClick: (String) -> Unit) {
    var albums by remember { mutableStateOf<List<LocalAlbum>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        albums = withContext(Dispatchers.Default) { fetchAlbums() }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(GlassPageBackground)) {
        PaneHeader(title = "Albums", subtitle = "${albums.size} album${if (albums.size != 1) "s" else ""}", onBack = onBack)

        if (loading) {
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
                items(albums, key = { it.id }) { album ->
                    AlbumCell(album = album, onClick = { onAlbumClick(album.id) })
                }
            }
        }
    }
}

@Composable
private fun AlbumCell(album: LocalAlbum, onClick: () -> Unit) {
    val coverPath = album.coverId?.let { rememberThumbnailPath(it) }
    GlassSurface(modifier = Modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), elevation = 4.dp) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (coverPath != null) {
                    AsyncImage(
                        model = coverPath,
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
                Text("${album.photoCount} photos", fontSize = 11.sp, color = GlassColors.TextSecondary)
            }
        }
    }
}

/** bucketName here is the album's PHAssetCollection.localIdentifier — a stable id, but named
 *  to match the shared App()/ShellScreen() localAlbumPhotosContent(bucketName, onBack) signature
 *  (also just an opaque id/name on Android). */
@Composable
fun LocalAlbumPhotosScreen(bucketName: String, onBack: () -> Unit) {
    var photos by remember { mutableStateOf<List<LocalMedia>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var lightboxPhoto by remember { mutableStateOf<LocalMedia?>(null) }

    LaunchedEffect(bucketName) {
        loading = true
        photos = withContext(Dispatchers.Default) { fetchAlbumMedia(bucketName) }
        loading = false
    }

    if (lightboxPhoto != null) {
        LocalPhotoLightbox(photos = photos, initialPhoto = lightboxPhoto!!, onDismiss = { lightboxPhoto = null })
    }

    Column(modifier = Modifier.fillMaxSize().background(GlassPageBackground)) {
        PaneHeader(title = "Album", subtitle = "${photos.size} photo${if (photos.size != 1) "s" else ""}", onBack = onBack)

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlassColors.AccentBlue)
            }
        } else if (photos.isEmpty()) {
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
