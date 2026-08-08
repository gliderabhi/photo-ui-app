package com.sevis.photos

import com.sevis.photos.data.PhotoApi
import com.sevis.photos.data.VideoApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateByAddingTimeInterval
import platform.Foundation.timeIntervalSince1970

// Must also be listed in Info.plist's BGTaskSchedulerPermittedIdentifiers (see project.yml).
private const val BG_TASK_ID = "com.sevis.photos.autoupload"
private const val KEY_LAST_SYNC = "auto_upload_last_sync"
private const val MIN_REFRESH_INTERVAL_SECONDS = 15.0 * 60.0

private val autoUploadScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Registers the background-refresh task handler. Must run once, before the app finishes
 * launching (called from MainViewController) — BGTaskScheduler requires that timing, unlike
 * Android's WorkManager, which can be scheduled any time after enqueue is called.
 *
 * Caveat vs. Android: this is a *best-effort* BGAppRefreshTask, not a guaranteed periodic
 * job — iOS decides if/when it actually runs based on usage patterns, battery, and charging
 * state, and there's no BOOT_COMPLETED-equivalent to resume it right after a restart either.
 * Where Android's WorkManager (backed by a real foreground-service-capable OS API) gives a
 * strong "runs roughly every 15 minutes" guarantee, iOS gives "runs sometime, maybe," by design
 * — Apple deliberately doesn't let third-party apps run arbitrary code in the background on a
 * fixed schedule, for battery/privacy reasons. The most reliable trigger in practice ends up
 * being simply opening the app, which also runs a sync (see MainViewController's LaunchedEffect).
 */
@OptIn(ExperimentalForeignApi::class)
fun registerAutoUploadTask() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(BG_TASK_ID, usingQueue = null) { task ->
        (task as? BGTask)?.let(::handleBackgroundTask)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun handleBackgroundTask(task: BGTask) {
    scheduleNextRefresh() // keep the chain going regardless of this run's outcome
    val job = autoUploadScope.launch {
        runCatching { syncNow() }
        task.setTaskCompletedWithSuccess(true)
    }
    task.expirationHandler = { job.cancel() }
}

@OptIn(ExperimentalForeignApi::class)
private fun scheduleNextRefresh() {
    if (!AppState.autoUploadEnabled) return
    val request = BGAppRefreshTaskRequest(identifier = BG_TASK_ID)
    request.earliestBeginDate = NSDate().dateByAddingTimeInterval(MIN_REFRESH_INTERVAL_SECONDS)
    BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
}

/** Toggles auto-upload — iOS's counterpart to MainActivity's onAutoUploadToggle: request
 *  Photos permission first, then persist + (de)register background work, plus an immediate
 *  sync (mirrors AutoUploadScheduler.runOnce()). */
@OptIn(ExperimentalForeignApi::class)
suspend fun setAutoUploadEnabled(enabled: Boolean) {
    if (enabled) {
        if (!requestPhotoLibraryAccess()) return
        AppState.autoUploadEnabled = true
        SettingsStore.setAutoUploadEnabled(true)
        scheduleNextRefresh()
        autoUploadScope.launch { syncNow() }
    } else {
        AppState.autoUploadEnabled = false
        SettingsStore.setAutoUploadEnabled(false)
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(BG_TASK_ID)
    }
}

/** Uploads every local photo/video newer than the last sync, using each asset's *original*
 *  file (see LocalPhotoLibrary.exportOriginal) — same incremental approach as
 *  AutoUploadWorker.doWork() on Android, just PHPhotoLibrary instead of MediaStore. */
private suspend fun syncNow() {
    if (AppState.token == null || AppState.folderPassword == null) return

    val defaults = NSUserDefaults.standardUserDefaults
    val lastSync = defaults.doubleForKey(KEY_LAST_SYNC)
    val since = if (lastSync > 0) lastSync else NSDate().timeIntervalSince1970 - 60

    val newMedia = fetchMediaSince(since.toLong())
    if (newMedia.isEmpty()) return

    val client = buildKtorClient()
    val photoApi = PhotoApi(API_BASE_URL, client)
    val videoApi = VideoApi(API_BASE_URL, client)

    newMedia.forEach { item ->
        val (path, filename) = exportOriginal(item.id) ?: return@forEach
        val bytes = readBytesAtPath("file://$path") ?: return@forEach
        val mimeType = if (item.isVideo) mimeTypeForFilename(filename, isVideo = true) else mimeTypeForFilename(filename, isVideo = false)
        runCatching {
            if (item.isVideo) videoApi.uploadVideo(bytes, filename, mimeType)
            else photoApi.uploadImage(bytes, filename, mimeType)
        }
    }

    defaults.setDouble(NSDate().timeIntervalSince1970, KEY_LAST_SYNC)
}

private fun mimeTypeForFilename(filename: String, isVideo: Boolean): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return if (isVideo) {
        when (ext) { "mp4" -> "video/mp4"; "mov" -> "video/quicktime"; else -> "video/mp4" }
    } else {
        when (ext) { "png" -> "image/png"; "heic" -> "image/heic"; "gif" -> "image/gif"; else -> "image/jpeg" }
    }
}
