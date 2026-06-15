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

@Composable
fun FolderCheckScreen(api: PhotoApi, navController: NavController) {
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
            .onFailure {
                // On failure, go to folder unlock to retry
                navController.navigate(Routes.FOLDER_UNLOCK) {
                    popUpTo(Routes.FOLDER_CHECK) { inclusive = true }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
