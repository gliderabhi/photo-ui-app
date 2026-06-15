package com.sevis.photos.autoupload

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.sevis.photos.AppState
import com.sevis.photos.BuildConfig
import com.sevis.photos.data.PhotoApi
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.api.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class AutoUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "AutoUploadWorker"
        private const val PREFS_NAME = "photos_prefs"
        private const val KEY_LAST_SYNC = "auto_upload_last_sync"
        private const val CHANNEL_ID = "auto_upload_channel"
        private const val NOTIF_ID = 1001
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val folderPwd = prefs.getString("folder_password", null)

        if (token == null || folderPwd == null) {
            Log.d(TAG, "Skipping — not authenticated")
            return@withContext Result.success()
        }

        // Temporarily set AppState so the API client picks up credentials
        AppState.token = token
        AppState.folderPassword = folderPwd

        val sinceEpoch = prefs.getLong(KEY_LAST_SYNC, System.currentTimeMillis() / 1000 - 60)
        val newImages = MediaStoreHelper.getImagesSince(applicationContext, sinceEpoch)

        if (newImages.isEmpty()) {
            Log.d(TAG, "No new images since $sinceEpoch")
            return@withContext Result.success()
        }

        Log.d(TAG, "Found ${newImages.size} new images to upload")
        createNotificationChannel()

        val api = buildApi(token)
        var uploaded = 0

        newImages.forEach { image ->
            val bytes = MediaStoreHelper.readBytes(applicationContext, image.uri) ?: return@forEach
            runCatching { api.uploadImage(bytes, image.name, image.mimeType) }
                .onSuccess {
                    uploaded++
                    Log.d(TAG, "Uploaded ${image.name}")
                }
                .onFailure { e ->
                    Log.w(TAG, "Failed to upload ${image.name}: ${e.message}")
                }
        }

        // Update last sync time to now
        prefs.edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis() / 1000)
            .apply()

        if (uploaded > 0) {
            showCompletionNotification(uploaded)
        }

        Result.success()
    }

    private fun buildApi(token: String): PhotoApi {
        val client = HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(createClientPlugin("StaticAuth") {
                onRequest { request, _ ->
                    request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                    AppState.folderPassword?.let {
                        request.headers.append("X-Folder-Password", it)
                    }
                }
            })
        }
        return PhotoApi(BuildConfig.API_BASE_URL, client)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Auto Upload",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Background photo upload notifications" }
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun showCompletionNotification(count: Int) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("Photos synced")
            .setContentText("$count new photo${if (count != 1) "s" else ""} uploaded automatically")
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID, notif)
    }
}
