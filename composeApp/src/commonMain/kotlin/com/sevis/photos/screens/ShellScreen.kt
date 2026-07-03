package com.sevis.photos.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.sevis.photos.data.ImageFile
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.PhotoResponse
import com.sevis.photos.data.VideoApi
import com.sevis.photos.data.VideoFile
import com.sevis.photos.data.VideoResponse

private data class NavItem(val label: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector)

private val NAV_ITEMS = listOf(
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
    onPlayVideo: (String) -> Unit,
    autoUploadEnabled: Boolean,
    onAutoUploadToggle: (Boolean) -> Unit,
    onFavoritesChange: (Set<Int>) -> Unit,
    onLogout: () -> Unit,
    onLockFolder: () -> Unit,
    appDownloadUrl: String,
    onOpenUrl: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Lock Folder") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                onClick = { showMenu = false; onLockFolder() }
                            )
                            DropdownMenuItem(
                                text = { Text("Get Latest App") },
                                leadingIcon = { Icon(Icons.Default.Android, contentDescription = null) },
                                onClick = { showMenu = false; onOpenUrl(appDownloadUrl) }
                            )
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                                onClick = { showMenu = false; onLogout() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF202124)
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                NAV_ITEMS.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        },
        containerColor = Color(0xFFFAFAFA)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> GalleryScreen(
                    api = api,
                    baseUrl = baseUrl,
                    favoritesOnly = false,
                    onFavoritesChange = onFavoritesChange
                )
                1 -> UploadScreen(
                    pickedImages = pickedImages,
                    pickedVideos = pickedVideos,
                    onPickMedia = onPickMedia,
                    onClearPickedMedia = onClearPickedMedia,
                    uploadImage = uploadImage,
                    uploadVideo = uploadVideo,
                    autoUploadEnabled = autoUploadEnabled,
                    onAutoUploadToggle = onAutoUploadToggle
                )
                2 -> VideoListScreen(
                    videoApi = videoApi,
                    onPlayVideo = onPlayVideo
                )
                3 -> AlbumsScreen(api = api, baseUrl = baseUrl)
                4 -> GalleryScreen(
                    api = api,
                    baseUrl = baseUrl,
                    favoritesOnly = true,
                    onFavoritesChange = onFavoritesChange
                )
            }
        }
    }
}
