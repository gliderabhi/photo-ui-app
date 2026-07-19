package com.sevis.photos

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.sevis.photos.data.*
import com.sevis.photos.screens.*
import com.sevis.photos.ui.GlassColors

// Without a custom scheme, MaterialTheme falls back to Material 3's stock
// purple defaults — every default-styled widget (dialogs, buttons, dropdown
// menus, progress indicators) clashed with the hand-styled Glass surfaces
// elsewhere, reading as "no theme at all" rather than one consistent look.
private val PhotosColorScheme = lightColorScheme(
    primary = GlassColors.AccentBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = GlassColors.AccentPurple,
    background = GlassColors.PageTop,
    surface = androidx.compose.ui.graphics.Color.White,
    onBackground = GlassColors.TextPrimary,
    onSurface = GlassColors.TextPrimary,
    error = GlassColors.AccentRed
)

object Routes {
    const val LOGIN = "login"
    const val FOLDER_CHECK = "folder-check"
    const val FOLDER_SETUP = "folder-setup"
    const val FOLDER_UNLOCK = "folder-unlock"
    const val SHELL = "shell"
    const val GALLERY = "gallery"
    const val FAVORITES = "favorites"
    const val UPLOAD = "upload"
    const val ALBUMS = "albums"
    const val ALBUM_DETAIL = "albums/{albumId}"

    fun albumDetail(id: Int) = "albums/$id"
}

@Composable
fun App(
    api: PhotoApi,
    baseUrl: String,
    onTokenChange: (String?) -> Unit,
    onFolderPasswordChange: (String?) -> Unit,
    onFavoritesChange: (Set<Int>) -> Unit,
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
    updateProgress: Int?,
    updateError: String?,
    onDismissUpdateError: () -> Unit,
    onUpdateApp: () -> Unit,
    extraLoginContent: (@Composable ((String) -> Unit) -> Unit)? = null,
    showCredentialsForm: Boolean = true,
    isTv: Boolean = false,
    localLibraryContent: @Composable (groupByPlace: Boolean) -> Unit = {},
    localAlbumsContent: @Composable (onBack: () -> Unit, onAlbumClick: (String) -> Unit) -> Unit = { _, _ -> },
    localAlbumPhotosContent: @Composable (bucketName: String, onBack: () -> Unit) -> Unit = { _, _ -> },
    localPeopleContent: @Composable (onBack: () -> Unit, onPersonClick: (Long, String?) -> Unit) -> Unit = { _, _ -> },
    localPersonPhotosContent: @Composable (personId: Long, displayName: String?, onBack: () -> Unit) -> Unit = { _, _, _ -> },
    versionName: String = "",
    versionCode: Int = 0
) {
    MaterialTheme(colorScheme = PhotosColorScheme) {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = if (AppState.token != null) Routes.FOLDER_CHECK else Routes.LOGIN
        ) {

            composable(Routes.LOGIN) {
                LoginScreen(
                    api = api,
                    onLoginSuccess = { token ->
                        AppState.token = token
                        onTokenChange(token)
                        navController.navigate(Routes.FOLDER_CHECK) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    extraLoginContent = extraLoginContent,
                    showCredentialsForm = showCredentialsForm
                )
            }

            composable(Routes.FOLDER_CHECK) {
                FolderCheckScreen(
                    api = api,
                    navController = navController,
                    onSessionExpired = {
                        AppState.token = null
                        AppState.folderPassword = null
                        onTokenChange(null)
                        onFolderPasswordChange(null)
                    }
                )
            }

            composable(Routes.FOLDER_SETUP) {
                FolderSetupScreen(
                    api = api,
                    onSetupComplete = { password ->
                        AppState.folderPassword = password
                        onFolderPasswordChange(password)
                        navController.navigate(Routes.SHELL) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.FOLDER_UNLOCK) {
                FolderUnlockScreen(
                    api = api,
                    onUnlockSuccess = { password ->
                        AppState.folderPassword = password
                        onFolderPasswordChange(password)
                        navController.navigate(Routes.SHELL) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onChangePassword = {
                        navController.navigate(Routes.FOLDER_SETUP)
                    }
                )
            }

            composable(Routes.SHELL) {
                ShellScreen(
                    api = api,
                    baseUrl = baseUrl,
                    pickedImages = pickedImages,
                    pickedVideos = pickedVideos,
                    onPickMedia = onPickMedia,
                    onClearPickedMedia = onClearPickedMedia,
                    uploadImage = uploadImage,
                    videoApi = videoApi,
                    uploadVideo = uploadVideo,
                    onPlayVideo = onPlayVideo,
                    autoUploadEnabled = autoUploadEnabled,
                    onAutoUploadToggle = onAutoUploadToggle,
                    onFavoritesChange = onFavoritesChange,
                    onLogout = {
                        AppState.token = null
                        AppState.folderPassword = null
                        onTokenChange(null)
                        onFolderPasswordChange(null)
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onLockFolder = {
                        AppState.folderPassword = null
                        onFolderPasswordChange(null)
                        navController.navigate(Routes.FOLDER_UNLOCK) {
                            popUpTo(Routes.SHELL) { inclusive = true }
                        }
                    },
                    updateProgress = updateProgress,
                    updateError = updateError,
                    onDismissUpdateError = onDismissUpdateError,
                    onUpdateApp = onUpdateApp,
                    isTv = isTv,
                    localLibraryContent = localLibraryContent,
                    localAlbumsContent = localAlbumsContent,
                    localAlbumPhotosContent = localAlbumPhotosContent,
                    localPeopleContent = localPeopleContent,
                    localPersonPhotosContent = localPersonPhotosContent,
                    versionName = versionName,
                    versionCode = versionCode
                )
            }
        }
    }
}
