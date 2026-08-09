package com.sevis.photos

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import com.sevis.photos.autoupload.AutoUploadScheduler
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.VideoApi
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

@UnstableApi
class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var api: PhotoApi
    private lateinit var videoApi: VideoApi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without this, the system reserves the status/nav bar area *and* our
        // own statusBarsPadding()/navigationBarsPadding() calls add their own
        // inset on top of that — a double gap that read as "too much padding"
        // and left the status bar a plain unstyled system strip instead of
        // part of the glass background. Edge-to-edge lets our content draw
        // underneath the system bars, with our existing insets doing the
        // (now single) correct inset.
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

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
        videoApi = VideoApi(baseUrl = BuildConfig.API_BASE_URL, client = buildKtorClient())

        setContent {
            // Null = no update in progress; 0..100 = download percent.
            var updateProgress by remember { mutableStateOf<Int?>(null) }
            var updateError by remember { mutableStateOf<String?>(null) }

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

            // The one *reliable* auto-upload trigger, same reasoning as iOS's
            // syncOnAppOpen() — WorkManager's periodic schedule is normally enough on its
            // own, but re-arming it here too is cheap defensive insurance against
            // anything that can silently drop it (e.g. a reinstall, which wipes both this
            // app's WorkManager schedule *and* the auto_upload_enabled preference itself —
            // see AutoUploadScheduler.schedule()'s KEEP policy, safe to call redundantly).
            LaunchedEffect(Unit) {
                if (AppState.autoUploadEnabled) {
                    AutoUploadScheduler.schedule(applicationContext)
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
                videoApi = videoApi,
                onPlayVideo = { url, rawUrl ->
                    startActivity(
                        Intent(this, VideoPlayerActivity::class.java)
                            .putExtra("url", url)
                            .putExtra("rawUrl", rawUrl)
                            .putExtra("token", AppState.token)
                    )
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
                },
                updateProgress = updateProgress,
                updateError = updateError,
                onDismissUpdateError = { updateError = null },
                onUpdateApp = {
                    updateProgress = 0
                    updateError = null
                    val apkName = if (BuildConfig.FLAVOR == "tv") "app.apk" else "app-mobile.apk"
                    UpdateManager.downloadAndInstall(
                        context = this,
                        apkUrl = "${BuildConfig.API_BASE_URL}/photo-service/downloads/$apkName",
                        onProgress = { pct -> updateProgress = pct },
                        onError = { msg ->
                            updateProgress = null
                            updateError = msg
                        }
                    )
                },
                // TV gets the QR/device-code flow (typing a Gmail password via a
                // D-pad on-screen keyboard is painful); mobile gets the standard
                // native Google account picker via Credential Manager instead.
                extraLoginContent = if (BuildConfig.FLAVOR == "tv") {
                    { onSuccess -> TvGoogleLoginContent(api = api, onLoginSuccess = onSuccess) }
                } else {
                    { onSuccess -> MobileGoogleLoginContent(api = api, onLoginSuccess = onSuccess) }
                },
                // TV is Google-only: typing an email/password via a D-pad
                // on-screen keyboard is painful, and the QR/device-code flow
                // above is always available.
                showCredentialsForm = BuildConfig.FLAVOR != "tv",
                isTv = BuildConfig.FLAVOR == "tv",
                localLibraryContent = { groupByPlace -> com.sevis.photos.screens.LocalLibraryScreen(groupByPlace, api) },
                localAlbumsContent = { onBack, onAlbumClick ->
                    com.sevis.photos.screens.LocalAlbumsScreen(onBack = onBack, onAlbumClick = onAlbumClick)
                },
                localAlbumPhotosContent = { bucketName, onBack ->
                    com.sevis.photos.screens.LocalAlbumPhotosScreen(bucketName = bucketName, onBack = onBack)
                },
                // People used to be a local-only screen (on-device face detection);
                // it's now server-backed (see photo-service's face-service), so it
                // reuses the same api/baseUrl every cloud-content pane already has.
                localPeopleContent = { onBack, onPersonClick ->
                    com.sevis.photos.screens.PeopleScreen(api = api, baseUrl = BuildConfig.API_BASE_URL, onBack = onBack, onPersonClick = onPersonClick)
                },
                localPersonPhotosContent = { personId, displayName, onBack ->
                    com.sevis.photos.screens.PersonPhotosScreen(api = api, baseUrl = BuildConfig.API_BASE_URL, personId = personId, displayName = displayName, onBack = onBack)
                },
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE
            )
        }
    }

    private fun buildKtorClient(): HttpClient = HttpClient(Android) {
        // Without this, a non-2xx response (e.g. a 401 with an empty body when the
        // gateway rejects an expired/missing JWT before it even reaches the backend)
        // still falls through to .body<T>() deserialization instead of throwing a
        // catchable, typed exception — producing a confusing raw
        // NoTransformationFoundException instead of something screens can handle.
        expectSuccess = true
        // Without a timeout, a stalled connection (bad network, DNS hang, silently
        // dropped packets) leaves the login screen's coroutine suspended forever —
        // the spinner never stops and neither onSuccess nor onFailure ever fires.
        // 15s was too tight for two real request shapes this client makes: a large
        // HEIC/video upload over a slow connection, and POST .../faces/scan, which
        // processes its whole batch synchronously server-side (decrypt + a face-service
        // round trip per photo) before responding at all — both timed out in practice.
        // Connect timeout stays tight; it's the request itself that needs the room.
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 60_000
        }
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
