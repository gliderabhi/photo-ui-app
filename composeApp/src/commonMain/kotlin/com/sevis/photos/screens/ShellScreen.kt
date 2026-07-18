package com.sevis.photos.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sevis.photos.data.ImageFile
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.PhotoResponse
import com.sevis.photos.data.VideoApi
import com.sevis.photos.data.VideoFile
import com.sevis.photos.data.VideoResponse
import com.sevis.photos.tvFocusRing
import photosapp.composeapp.generated.resources.Res
import photosapp.composeapp.generated.resources.app_icon
import org.jetbrains.compose.resources.painterResource

private data class NavItem(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("Library", Icons.Filled.PhoneAndroid, Icons.Outlined.PhoneAndroid),
    NavItem("Gallery", Icons.Filled.PhotoLibrary, Icons.Outlined.PhotoLibrary),
    NavItem("Upload", Icons.Filled.CloudUpload, Icons.Outlined.CloudUpload),
    NavItem("Videos", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    NavItem("Albums", Icons.Filled.Photo, Icons.Outlined.Photo),
    NavItem("Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
)

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
    localLibraryContent: @Composable () -> Unit = {},
    versionName: String = "",
    versionCode: Int = 0
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // TVs commonly overscan and crop a margin off every edge of the picture —
    // content sitting flush against the screen edge (the top bar's menu icon,
    // the bottom nav's outer tabs) gets cut off on those sets.
    val overscanPadding = if (isTv) Modifier.padding(horizontal = 24.dp, vertical = 16.dp) else Modifier

    Scaffold(
        modifier = overscanPadding,
        topBar = {
            TopAppBar(
                title = {
                    Image(
                        painter = painterResource(Res.drawable.app_icon),
                        contentDescription = "Photos",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(28.dp).clip(CircleShape)
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.tvFocusRing(cornerRadius = 24.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color.White)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Lock Folder") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                onClick = { showMenu = false; onLockFolder() },
                                modifier = Modifier.tvFocusRing()
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = { showMenu = false; showSettings = true },
                                modifier = Modifier.tvFocusRing()
                            )
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                                onClick = { showMenu = false; onLogout() },
                                modifier = Modifier.tvFocusRing()
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2563EB),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFFEEF2FB), tonalElevation = 0.dp) {
                NAV_ITEMS.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        modifier = Modifier.tvFocusRing(),
                        icon = {
                            Icon(
                                if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF2563EB),
                            selectedTextColor = Color(0xFF2563EB),
                            indicatorColor = Color(0xFFD8E3FA),
                            unselectedIconColor = Color(0xFF5F6368),
                            unselectedTextColor = Color(0xFF5F6368)
                        )
                    )
                }
            }
        },
        containerColor = Color(0xFFF4F7FC)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> localLibraryContent()
                1 -> GalleryScreen(
                    api = api,
                    baseUrl = baseUrl,
                    favoritesOnly = false,
                    onFavoritesChange = onFavoritesChange,
                    isTv = isTv
                )
                2 -> UploadScreen(
                    pickedImages = pickedImages,
                    pickedVideos = pickedVideos,
                    onPickMedia = onPickMedia,
                    onClearPickedMedia = onClearPickedMedia,
                    uploadImage = uploadImage,
                    uploadVideo = uploadVideo,
                    autoUploadEnabled = autoUploadEnabled,
                    onAutoUploadToggle = onAutoUploadToggle
                )
                3 -> VideoListScreen(
                    videoApi = videoApi,
                    onPlayVideo = onPlayVideo
                )
                4 -> AlbumsScreen(api = api, baseUrl = baseUrl)
                5 -> GalleryScreen(
                    api = api,
                    baseUrl = baseUrl,
                    favoritesOnly = true,
                    onFavoritesChange = onFavoritesChange,
                    isTv = isTv
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
