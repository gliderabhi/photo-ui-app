package com.sevis.photos.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sevis.photos.data.ImageFile
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.PhotoResponse
import com.sevis.photos.data.VideoApi
import com.sevis.photos.data.VideoFile
import com.sevis.photos.data.VideoResponse
import com.sevis.photos.tvFocusRing
import com.sevis.photos.ui.GlassColors
import com.sevis.photos.ui.GlassPageBackground
import com.sevis.photos.ui.GlassSurface
import photosapp.composeapp.generated.resources.Res
import photosapp.composeapp.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

/**
 * All destinations reachable from the shell. Gallery is home; every other pane —
 * regrouping the on-device library (Albums/People/By Place/By Date) as well as
 * cloud-side content (Upload/Cloud Gallery/Videos/Favorites) — is reached from the
 * FAB. The top bar's overflow menu is reserved for account-level actions only
 * (Lock Folder/Settings/Logout), not content navigation.
 */
private sealed class AppPane {
    data object Gallery : AppPane()
    data object Albums : AppPane()
    data class AlbumPhotos(val bucketName: String) : AppPane()
    data object People : AppPane()
    data class PersonPhotos(val personId: Long, val displayName: String?) : AppPane()
    data object Upload : AppPane()
    data object CloudGallery : AppPane()
    data object Videos : AppPane()
    data object Favorites : AppPane()
}

private enum class Grouping { DATE, PLACE }

/** Measures/places the child normally but reports zero size upward, so a purely
 * decorative, oversized element (e.g. a blurred glow blob) can't inflate its
 * parent's wrapped size. */
private fun Modifier.zeroSizeLayout(): Modifier = this.layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) { placeable.place(0, 0) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(
    api: PhotoApi,
    baseUrl: String,
    pickedImages: List<ImageFile>,
    pickedVideos: List<VideoFile>,
    onPickMedia: () -> Unit,
    onClearPickedMedia: () -> Unit,
    uploadImage: suspend (ImageFile) -> Result<PhotoResponse>,
    videoApi: VideoApi,
    uploadVideo: suspend (VideoFile) -> Result<VideoResponse>,
    onPlayVideo: (String, String?) -> Unit,
    autoUploadEnabled: Boolean,
    onAutoUploadToggle: (Boolean) -> Unit,
    onFavoritesChange: (Set<Int>) -> Unit,
    onLogout: () -> Unit,
    onLockFolder: () -> Unit,
    updateProgress: Int?,
    updateError: String?,
    onDismissUpdateError: () -> Unit,
    onUpdateApp: () -> Unit,
    isTv: Boolean = false,
    localLibraryContent: @Composable (groupByPlace: Boolean) -> Unit = {},
    localAlbumsContent: @Composable (onBack: () -> Unit, onAlbumClick: (String) -> Unit) -> Unit = { _, _ -> },
    localAlbumPhotosContent: @Composable (bucketName: String, onBack: () -> Unit) -> Unit = { _, _ -> },
    localPeopleContent: @Composable (onBack: () -> Unit, onPersonClick: (Long, String?) -> Unit) -> Unit = { _, _ -> },
    localPersonPhotosContent: @Composable (personId: Long, displayName: String?, onBack: () -> Unit) -> Unit = { _, _, _ -> },
    versionName: String = "",
    versionCode: Int = 0
) {
    var pane by remember { mutableStateOf<AppPane>(AppPane.Gallery) }
    var grouping by remember { mutableStateOf(Grouping.DATE) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = pane != AppPane.Gallery) {
        pane = when (val p = pane) {
            is AppPane.PersonPhotos -> AppPane.People
            is AppPane.AlbumPhotos -> AppPane.Albums
            else -> AppPane.Gallery
        }
    }

    // TVs commonly overscan and crop a margin off every edge of the picture —
    // content sitting flush against the screen edge (the top bar's menu icon,
    // the FAB) gets cut off on those sets.
    val overscanPadding = if (isTv) Modifier.padding(horizontal = 24.dp, vertical = 16.dp) else Modifier

    Scaffold(
        modifier = overscanPadding,
        topBar = {
            GlassTopBar(
                showMenu = showMenu,
                onMenuToggle = { showMenu = it },
                onLockFolder = onLockFolder,
                onShowSettings = { showSettings = true },
                onLogout = onLogout
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GlassPageBackground)
                .padding(paddingValues)
        ) {
            when (val p = pane) {
                AppPane.Gallery -> localLibraryContent(grouping == Grouping.PLACE)
                AppPane.Albums -> localAlbumsContent(
                    { pane = AppPane.Gallery },
                    { bucket -> pane = AppPane.AlbumPhotos(bucket) }
                )
                is AppPane.AlbumPhotos -> localAlbumPhotosContent(p.bucketName) { pane = AppPane.Albums }
                AppPane.People -> localPeopleContent(
                    { pane = AppPane.Gallery },
                    { id, name -> pane = AppPane.PersonPhotos(id, name) }
                )
                is AppPane.PersonPhotos -> localPersonPhotosContent(p.personId, p.displayName) { pane = AppPane.People }
                AppPane.Upload -> UploadScreen(
                    pickedImages = pickedImages,
                    pickedVideos = pickedVideos,
                    onPickMedia = onPickMedia,
                    onClearPickedMedia = onClearPickedMedia,
                    uploadImage = uploadImage,
                    uploadVideo = uploadVideo,
                    autoUploadEnabled = autoUploadEnabled,
                    onAutoUploadToggle = onAutoUploadToggle
                )
                AppPane.CloudGallery -> GalleryScreen(
                    api = api,
                    baseUrl = baseUrl,
                    favoritesOnly = false,
                    onFavoritesChange = onFavoritesChange,
                    isTv = isTv
                )
                AppPane.Videos -> VideoListScreen(
                    videoApi = videoApi,
                    onPlayVideo = onPlayVideo
                )
                AppPane.Favorites -> GalleryScreen(
                    api = api,
                    baseUrl = baseUrl,
                    favoritesOnly = true,
                    onFavoritesChange = onFavoritesChange,
                    isTv = isTv
                )
            }

            // The FAB is the primary way to regroup/navigate the on-device library —
            // only meaningful while actually looking at the Gallery.
            if (pane == AppPane.Gallery) {
                GlassFab(
                    expanded = fabExpanded,
                    onExpandedChange = { fabExpanded = it },
                    grouping = grouping,
                    onSelectGrouping = { grouping = it; fabExpanded = false },
                    onNavigate = { pane = it; fabExpanded = false },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .navigationBarsPadding()
                )
            }
        }
    }

    if (updateProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Updating") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(
                        progress = { updateProgress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Downloading… $updateProgress%", fontSize = 13.sp, color = Color(0xFF64748B))
                }
            }
        )
    }

    if (showSettings) {
        SettingsScreen(
            versionName = versionName,
            versionCode = versionCode,
            api = api,
            autoUploadEnabled = autoUploadEnabled,
            onAutoUploadToggle = onAutoUploadToggle,
            updateProgress = updateProgress,
            onUpdateApp = onUpdateApp,
            onDismiss = { showSettings = false }
        )
    }

    if (updateError != null) {
        AlertDialog(
            onDismissRequest = onDismissUpdateError,
            confirmButton = {
                TextButton(onClick = onDismissUpdateError, modifier = Modifier.tvFocusRing()) {
                    Text("OK")
                }
            },
            title = { Text("Update failed") },
            text = { Text(updateError) }
        )
    }
}

@Composable
private fun GlassTopBar(
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onLockFolder: () -> Unit,
    onShowSettings: () -> Unit,
    onLogout: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(com.sevis.photos.ui.GlassBarBackground)
    ) {
        // Ambient glow: a soft, self-contained blurred color blob for depth —
        // the kind of tint iOS "Liquid Glass" surfaces pick up from content.
        // zeroSizeLayout() keeps this decorative 140dp blob from inflating the
        // bar's own measured height (Box otherwise wraps to its largest child,
        // regardless of offset) — without it the whole top bar was forced to
        // ~140dp tall, showing up as a large blank gap above the content.
        Box(
            modifier = Modifier
                .zeroSizeLayout()
                .size(140.dp)
                .offset(x = (-40).dp, y = (-50).dp)
                .blur(70.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(GlassColors.AccentBlue.copy(alpha = 0.16f), CircleShape)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    painter = painterResource(Res.drawable.app_icon),
                    contentDescription = "Photos",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp).clip(CircleShape)
                )
                Text(
                    "Photos",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassColors.TextPrimary
                )
            }

            Box {
                GlassIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    onClick = { onMenuToggle(true) }
                )
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { onMenuToggle(false) },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("Lock Folder") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        onClick = { onMenuToggle(false); onLockFolder() },
                        modifier = Modifier.tvFocusRing()
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        onClick = { onMenuToggle(false); onShowSettings() },
                        modifier = Modifier.tvFocusRing()
                    )
                    DropdownMenuItem(
                        text = { Text("Logout") },
                        leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                        onClick = { onMenuToggle(false); onLogout() },
                        modifier = Modifier.tvFocusRing()
                    )
                }
            }
        }

        // Hairline seam at the base of the bar — reads as the "edge" of the
        // glass against whatever scrolls up beneath it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(0.6.dp)
                .background(GlassColors.Hairline)
        )
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.7f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            .tvFocusRing(cornerRadius = 19.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = GlassColors.TextPrimary, modifier = Modifier.size(20.dp))
    }
}

private data class FabMenuItem(val label: String, val icon: ImageVector, val action: () -> Unit)

@Composable
private fun GlassFab(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    grouping: Grouping,
    onSelectGrouping: (Grouping) -> Unit,
    onNavigate: (AppPane) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val items = listOf(
                FabMenuItem("Favorites", Icons.Filled.Favorite) { onNavigate(AppPane.Favorites) },
                FabMenuItem("Videos", Icons.Filled.VideoLibrary) { onNavigate(AppPane.Videos) },
                FabMenuItem("Cloud Gallery", Icons.Filled.PhotoLibrary) { onNavigate(AppPane.CloudGallery) },
                FabMenuItem("Upload", Icons.Filled.CloudUpload) { onNavigate(AppPane.Upload) },
                FabMenuItem("People", Icons.Filled.Face) { onNavigate(AppPane.People) },
                FabMenuItem("Albums", Icons.Filled.Folder) { onNavigate(AppPane.Albums) },
                FabMenuItem("By Place", Icons.Filled.Place) { onSelectGrouping(Grouping.PLACE) },
                FabMenuItem("By Date", Icons.Filled.CalendarMonth) { onSelectGrouping(Grouping.DATE) }
            )
            items.forEach { item ->
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    FabMenuPill(item = item, active = when (item.label) {
                        "By Date" -> grouping == Grouping.DATE
                        "By Place" -> grouping == Grouping.PLACE
                        else -> false
                    })
                }
            }

            val rotation by animateFloatAsState(if (expanded) 45f else 0f, label = "fabRotation")
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .shadow(elevation = 14.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                    .clip(CircleShape)
                    .background(GlassColors.AccentBlue)
                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    .clickable(
                        onClick = { onExpandedChange(!expanded) },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = if (expanded) "Close menu" else "Gallery view options",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp).rotate(rotation)
                )
            }
        }
    }
}

@Composable
private fun FabMenuPill(item: FabMenuItem, active: Boolean) {
    GlassSurface(
        shape = RoundedCornerShape(20.dp),
        elevation = 8.dp,
        tintAlpha = if (active) 0.97f else 0.92f,
        modifier = Modifier.clickable(onClick = item.action)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                item.label,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) GlassColors.AccentBlue else GlassColors.TextPrimary
            )
            Icon(
                item.icon,
                contentDescription = null,
                tint = if (active) GlassColors.AccentBlue else GlassColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
