package com.sevis.photos

import android.Manifest
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.sevis.photos.autoupload.AutoUploadScheduler
import com.sevis.photos.data.ImageFile
import com.sevis.photos.data.PhotoApi
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var api: PhotoApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("photos_prefs", MODE_PRIVATE)

        // Restore persisted auth state
        AppState.token = prefs.getString("token", null)
        AppState.folderPassword = prefs.getString("folder_password", null)
        AppState.autoUploadEnabled = prefs.getBoolean("auto_upload_enabled", false)
        val savedFavs = prefs.getString("favorites", "") ?: ""
        if (savedFavs.isNotBlank()) {
            savedFavs.split(",").mapNotNull { it.trim().toIntOrNull() }
                .forEach { AppState.favoriteIds.add(it) }
        }

        api = PhotoApi(baseUrl = BuildConfig.API_BASE_URL, client = buildKtorClient())

        setContent {
            var pickedImages by remember { mutableStateOf<List<ImageFile>>(emptyList()) }

            // Gallery image picker
            val imagePicker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickMultipleVisualMedia()
            ) { uris ->
                pickedImages = uris.mapNotNull { uri -> readImageFile(uri) }
            }

            // Permission launcher for READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE
            val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val granted = permissions[readPermission] == true
                if (granted) {
                    AppState.autoUploadEnabled = true
                    prefs.edit().putBoolean("auto_upload_enabled", true).apply()
                    AutoUploadScheduler.schedule(applicationContext)
                    // Run an immediate sync
                    AutoUploadScheduler.runOnce(applicationContext)
                }
            }

            App(
                api = api,
                baseUrl = BuildConfig.API_BASE_URL,
                onTokenChange = { token ->
                    AppState.token = token
                    prefs.edit().putString("token", token).apply()
                },
                onFolderPasswordChange = { pwd ->
                    AppState.folderPassword = pwd
                    prefs.edit().putString("folder_password", pwd).apply()
                },
                onFavoritesChange = { ids ->
                    prefs.edit().putString("favorites", ids.joinToString(",")).apply()
                },
                pickedImages = pickedImages,
                onPickImages = {
                    pickedImages = emptyList()
                    imagePicker.launch(
                        ActivityResultContracts.PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                onClearPickedImages = { pickedImages = emptyList() },
                uploadImage = { imageFile ->
                    runCatching {
                        val bytes = contentResolver.openInputStream(Uri.parse(imageFile.uri))
                            ?.use { it.readBytes() }
                            ?: error("Cannot read ${imageFile.uri}")
                        api.uploadImage(bytes, imageFile.name, imageFile.mimeType)
                    }
                },
                autoUploadEnabled = AppState.autoUploadEnabled,
                onAutoUploadToggle = { enabled ->
                    if (enabled) {
                        // Request gallery permissions first
                        val permsToRequest = buildList {
                            add(readPermission)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        permissionLauncher.launch(permsToRequest.toTypedArray())
                    } else {
                        AppState.autoUploadEnabled = false
                        prefs.edit().putBoolean("auto_upload_enabled", false).apply()
                        AutoUploadScheduler.cancel(applicationContext)
                    }
                }
            )
        }
    }

    private fun readImageFile(uri: Uri): ImageFile? {
        return try {
            val cursor = contentResolver.query(
                uri,
                arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
                null, null, null
            )
            val name = cursor?.use {
                if (it.moveToFirst()) it.getString(0) else uri.lastPathSegment ?: "image.jpg"
            } ?: (uri.lastPathSegment ?: "image.jpg")
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
            ImageFile(uri = uri.toString(), name = name, mimeType = mimeType)
        } catch (e: Exception) {
            null
        }
    }

    private fun buildKtorClient(): HttpClient = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(createClientPlugin("DynamicAuth") {
            onRequest { request, _ ->
                AppState.token?.let {
                    request.headers.append(HttpHeaders.Authorization, "Bearer $it")
                }
            }
        })
    }
}
