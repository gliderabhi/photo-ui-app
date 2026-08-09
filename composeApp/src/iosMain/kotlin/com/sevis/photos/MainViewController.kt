package com.sevis.photos

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.VideoApi
import com.sevis.photos.screens.PeopleScreen
import com.sevis.photos.screens.PersonPhotosScreen
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/** iOS's counterpart to MainActivity.kt — same role (build the shared clients,
 *  restore session state, wire every platform-specific App() callback), just
 *  called from Swift's ContentView instead of being an Activity itself. */
@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    SettingsStore.restore()
    registerAutoUploadTask()

    val api = PhotoApi(baseUrl = API_BASE_URL, client = buildKtorClient())
    val videoApi = VideoApi(baseUrl = API_BASE_URL, client = buildKtorClient())

    return ComposeUIViewController {
        var updateProgress by remember { mutableStateOf<Int?>(null) }
        var updateError by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        // The one *reliable* auto-upload trigger on iOS — see syncOnAppOpen()'s doc
        // comment. Runs once per fresh ComposeUIViewController (i.e. once per app
        // launch/session), not on every recomposition.
        LaunchedEffect(Unit) { syncOnAppOpen() }

        App(
            api = api,
            baseUrl = API_BASE_URL,
            onTokenChange = { token ->
                AppState.token = token
                SettingsStore.setToken(token)
            },
            onFolderPasswordChange = { pwd ->
                AppState.folderPassword = pwd
                SettingsStore.setFolderPassword(pwd)
            },
            onFavoritesChange = { ids -> SettingsStore.setFavorites(ids) },
            videoApi = videoApi,
            onPlayVideo = { url, rawUrl -> presentVideoPlayer(url, rawUrl) },
            autoUploadEnabled = AppState.autoUploadEnabled,
            onAutoUploadToggle = { enabled -> scope.launch { setAutoUploadEnabled(enabled) } },
            updateProgress = updateProgress,
            updateError = updateError,
            onDismissUpdateError = { updateError = null },
            // Self-updating via a downloaded binary is an Android-only concept (APK
            // sideload install) — the App Store doesn't allow apps to install their
            // own updates. There's simply no onUpdateApp equivalent to wire up here;
            // SettingsScreen's update button becomes a no-op that never shows
            // progress (Apple's own App Store update mechanism is what applies here).
            onUpdateApp = { },
            extraLoginContent = { onSuccess -> GoogleSignInButton(api = api, onLoginSuccess = onSuccess) },
            showCredentialsForm = true,
            isTv = false,
            localLibraryContent = { groupByPlace -> com.sevis.photos.screens.LocalLibraryScreen(groupByPlace, api) },
            localAlbumsContent = { onBack, onAlbumClick ->
                com.sevis.photos.screens.LocalAlbumsScreen(onBack = onBack, onAlbumClick = onAlbumClick)
            },
            localAlbumPhotosContent = { bucketName, onBack ->
                com.sevis.photos.screens.LocalAlbumPhotosScreen(bucketName = bucketName, onBack = onBack)
            },
            localPeopleContent = { onBack, onPersonClick ->
                PeopleScreen(api = api, baseUrl = API_BASE_URL, onBack = onBack, onPersonClick = onPersonClick)
            },
            localPersonPhotosContent = { personId, displayName, onBack ->
                PersonPhotosScreen(api = api, baseUrl = API_BASE_URL, personId = personId, displayName = displayName, onBack = onBack)
            },
            versionName = "1.0 (iOS)",
            // Self-updating via a downloaded binary is Android-only (see onUpdateApp above) —
            // version.json's versionCode numbers Android APK builds, which don't correspond to
            // anything meaningful here. Int.MAX_VALUE keeps SettingsScreen's remote.versionCode
            // > versionCode check permanently false, so it always reads "up to date" and never
            // renders the (otherwise non-functional, since onUpdateApp is a no-op) Update button.
            versionCode = Int.MAX_VALUE
        )
    }
}
