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
import io.ktor.client.plugins.HttpTimeout
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
        // How often (in successfully-uploaded photos) to run a face-scan batch and post
        // a progress notification during one run — on a big first-ever backlog (found
        // 4048 images in one real run), waiting for the *entire* upload loop to finish
        // before face-scan ever gets a turn, or before the user sees any progress at
        // all beyond the initial "Uploading…" toast, is a long wait for both. NOT used
        // to advance KEY_LAST_SYNC early — images are fetched newest-first (see
        // MediaStoreHelper), so at any checkpoint mid-loop the *older*, not-yet-attempted
        // tail is still exactly what's below the original sinceEpoch; advancing the
        // cursor before the whole snapshot finishes would risk silently skipping
        // whatever's left if the worker gets killed partway through.
        private const val CHECKPOINT_EVERY = 100
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        Log.d(TAG, "Run starting (id=$id, tags=$tags)")

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)
        val folderPwd = prefs.getString("folder_password", null)

        if (token == null || folderPwd == null) {
            Log.d(TAG, "Skipping — not authenticated (token=${token != null}, folderPassword=${folderPwd != null})")
            return@withContext Result.success()
        }

        // Temporarily set AppState so the API client picks up credentials
        AppState.token = token
        AppState.folderPassword = folderPwd

        // 0L (not "now - 60s") the first time this ever runs — without a manual Upload
        // screen as a fallback, this is now the *only* path anything already in the
        // library gets to the server through, so the first sync has to catch the whole
        // existing library, not just whatever's taken in the next minute.
        val sinceEpoch = prefs.getLong(KEY_LAST_SYNC, 0L)
        val newImages = MediaStoreHelper.getImagesSince(applicationContext, sinceEpoch)
        // Videos are temporarily NOT auto-uploaded — readBytes() below loads a file
        // entirely into one ByteArray, and a real-world video (a 212MB .MOV triggered
        // this) is both a genuine OOM risk on its own and, separately, doomed to fail
        // regardless: photos.sevis.store is fronted by a cloudflared tunnel, which caps
        // request bodies well under photo-service's own 2GB server.servlet.multipart
        // limit — the upload was failing with NSURLErrorDomain -1017 "cannot parse
        // response" / connection reset, consistent with Cloudflare closing the
        // connection mid-upload rather than a bug in this client. Re-enable once
        // uploads are chunked/resumable or routed around the tunnel's size limit —
        // don't just remove this comment and flip videos back on without one of those.
        Log.d(TAG, "Scanned MediaStore since epoch $sinceEpoch: ${newImages.size} image(s) (video auto-upload disabled)")

        val client = buildHttpClient(token)
        val photoApi = PhotoApi(BuildConfig.API_BASE_URL, client)

        if (newImages.isEmpty()) {
            Log.d(TAG, "Nothing new to upload")
            runFaceScanBatch(photoApi)
            Log.d(TAG, "Run complete: nothing to upload, elapsed=${System.currentTimeMillis() - startedAt}ms")
            return@withContext Result.success()
        }

        createNotificationChannel()

        var uploaded = 0
        var failed = 0

        newImages.forEachIndexed { index, image ->
            val bytes = MediaStoreHelper.readBytes(applicationContext, image.uri)
            if (bytes == null) {
                Log.w(TAG, "Skipping ${image.name} — couldn't read bytes from ${image.uri}")
                return@forEachIndexed
            }
            Log.d(TAG, "Uploading image ${image.name} (${bytes.size} bytes, ${image.mimeType})…")
            runCatching { photoApi.uploadImage(bytes, image.name, image.mimeType) }
                .onSuccess { response ->
                    uploaded++
                    Log.d(TAG, "Uploaded ${image.name} -> photo id=${response.id}")
                }
                .onFailure { e ->
                    failed++
                    Log.w(TAG, "Failed to upload ${image.name}: ${e::class.simpleName}: ${e.message}")
                }

            if ((index + 1) % CHECKPOINT_EVERY == 0) {
                Log.d(TAG, "Checkpoint: ${index + 1}/${newImages.size} processed (uploaded=$uploaded, failed=$failed)")
                showProgressNotification(index + 1, newImages.size)
                runFaceScanBatch(photoApi)
            }
        }

        // Update last sync time to now
        prefs.edit()
            .putLong(KEY_LAST_SYNC, System.currentTimeMillis() / 1000)
            .apply()

        runFaceScanBatch(photoApi)

        Log.d(TAG, "Run complete: uploaded=$uploaded, failed=$failed, elapsed=${System.currentTimeMillis() - startedAt}ms")

        if (uploaded > 0) {
            showCompletionNotification(uploaded)
        }

        Result.success()
    }

    private fun showProgressNotification(done: Int, total: Int) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setContentTitle("Syncing photos…")
            .setContentText("$done of $total")
            .setProgress(total, done, false)
            .setOngoing(true)
            .build()
        nm.notify(NOTIF_ID, notif)
    }

    // One batch per run (not looped to drain the whole backlog — see SettingsScreen's
    // "Scan now" for that) is plenty: this worker already re-runs every 15 minutes
    // (see AutoUploadScheduler), so a backlog clears gradually over successive runs
    // without any one run taking noticeably longer than an upload-only run would.
    private suspend fun runFaceScanBatch(photoApi: PhotoApi) {
        runCatching { photoApi.scanFaces() }
            .onSuccess { result -> Log.d(TAG, "Face scan batch: scanned=${result.scanned}, remaining=${result.remaining}") }
            .onFailure { e -> Log.w(TAG, "Face scan batch failed: ${e::class.simpleName}: ${e.message}") }
    }

    private fun buildHttpClient(token: String): HttpClient {
        return HttpClient(Android) {
            // No HttpTimeout previously installed here at all — ktor-client-android
            // without one falls back to the underlying engine's own default, which for
            // some requests effectively means no timeout, i.e. a stuck connection could
            // hang the whole run indefinitely rather than failing fast. 60s matches the
            // main app client (MainActivity's buildKtorClient) — long enough for a large
            // HEIC upload or a faces/scan batch (processed synchronously server-side)
            // without hanging forever on a truly dead connection.
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 60_000
            }
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
