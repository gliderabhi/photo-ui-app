package com.sevis.photos

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.sevis.photos.data.*
import com.sevis.photos.screens.*

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
    onPickImages: () -> Unit,
    onClearPickedImages: () -> Unit,
    uploadImage: suspend (ImageFile) -> Result<PhotoResponse>,
    autoUploadEnabled: Boolean,
    onAutoUploadToggle: (Boolean) -> Unit
) {
    MaterialTheme {
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
                    }
                )
            }

            composable(Routes.FOLDER_CHECK) {
                FolderCheckScreen(api = api, navController = navController)
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
                    onPickImages = onPickImages,
                    onClearPickedImages = onClearPickedImages,
                    uploadImage = uploadImage,
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
                    }
                )
            }
        }
    }
}
