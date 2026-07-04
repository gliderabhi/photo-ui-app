package com.sevis.photos.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.sevis.photos.AppState
import com.sevis.photos.Routes
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.isUnauthorized

@Composable
fun FolderCheckScreen(api: PhotoApi, navController: NavController, onSessionExpired: () -> Unit) {
    LaunchedEffect(Unit) {
        runCatching { api.getFolderStatus() }
            .onSuccess { status ->
                when {
                    !status.hasFolder -> navController.navigate(Routes.FOLDER_SETUP) {
                        popUpTo(Routes.FOLDER_CHECK) { inclusive = true }
                    }
                    AppState.folderPassword == null -> navController.navigate(Routes.FOLDER_UNLOCK) {
                        popUpTo(Routes.FOLDER_CHECK) { inclusive = true }
                    }
                    else -> navController.navigate(Routes.SHELL) {
                        popUpTo(Routes.FOLDER_CHECK) { inclusive = true }
                    }
                }
            }
            .onFailure { e ->
                if (e.isUnauthorized()) {
                    // The session token itself is expired/invalid — sending the user to
                    // Unlock Folder would just fail the same way on every retry, since
                    // that screen's request carries the same bad token. Log out instead
                    // so they get a clean re-login rather than a confusing request error.
                    onSessionExpired()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.FOLDER_CHECK) { inclusive = true }
                    }
                } else {
                    // Some other (e.g. network) failure — go to folder unlock to retry.
                    navController.navigate(Routes.FOLDER_UNLOCK) {
                        popUpTo(Routes.FOLDER_CHECK) { inclusive = true }
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
