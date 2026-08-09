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
import platform.Foundation.NSLog
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateByAddingTimeInterval
import platform.Foundation.timeIntervalSince1970

// Every log line here is prefixed "AutoUpload:" — filter Xcode's console (or `xcrun
// simctl spawn booted log stream --predicate 'eventMessage contains "AutoUpload:"'` on a
// real device/simulator) by that to watch a sync happen, the iOS counterpart to grepping
// Logcat for AutoUploadWorker's TAG on Android.
private fun log(message: String) = NSLog("AutoUpload: $message")

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
 * being simply opening the app — MainViewController calls syncOnAppOpen() for exactly that
 * reason, since relying on BGTaskScheduler alone left auto-upload silently idle for weeks
 * (nothing uploaded from 2026-07-04 to today, confirmed against the server's photo_db).
 */
@OptIn(ExperimentalForeignApi::class)
fun registerAutoUploadTask() {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(BG_TASK_ID, usingQueue = null) { task ->
        log("background task fired")
        (task as? BGTask)?.let(::handleBackgroundTask)
    }
    log("background task handler registered (id=$BG_TASK_ID)")
}

@OptIn(ExperimentalForeignApi::class)
private fun handleBackgroundTask(task: BGTask) {
    scheduleNextRefresh() // keep the chain going regardless of this run's outcome
    val job = autoUploadScope.launch {
        runCatching { syncNow() }
            .onFailure { e -> log("background sync threw: ${e::class.simpleName}: ${e.message}") }
        task.setTaskCompletedWithSuccess(true)
    }
    task.expirationHandler = {
        log("background task expired before sync finished — cancelling")
        job.cancel()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun scheduleNextRefresh() {
    if (!AppState.autoUploadEnabled) {
        log("scheduleNextRefresh: auto-upload is off, not scheduling")
        return
    }
    val request = BGAppRefreshTaskRequest(identifier = BG_TASK_ID)
    val earliest = NSDate().dateByAddingTimeInterval(MIN_REFRESH_INTERVAL_SECONDS)
    request.earliestBeginDate = earliest
    BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
    log("next background refresh requested, earliest at $earliest (iOS decides if/when it actually fires)")
}

/** Toggles auto-upload — iOS's counterpart to MainActivity's onAutoUploadToggle: request
 *  Photos permission first, then persist + (de)register background work, plus an immediate
 *  sync (mirrors AutoUploadScheduler.runOnce()). */
@OptIn(ExperimentalForeignApi::class)
suspend fun setAutoUploadEnabled(enabled: Boolean) {
    log("setAutoUploadEnabled($enabled)")
    if (enabled) {
        if (!requestPhotoLibraryAccess()) {
            log("Photo library access denied — not enabling")
            return
        }
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

/** Runs a sync immediately, meant to be called once per app open/foreground (see
 *  MainViewController) — the one *reliable* trigger auto-upload has on iOS, since
 *  BGTaskScheduler alone is best-effort only (see registerAutoUploadTask's doc comment).
 *  No-ops quietly if auto-upload isn't actually turned on or the app isn't logged in yet. */
suspend fun syncOnAppOpen() {
    if (!AppState.autoUploadEnabled) {
        log("syncOnAppOpen: auto-upload is off, skipping")
        return
    }
    log("syncOnAppOpen: starting")
    syncNow()
}

/** Uploads every local photo/video newer than the last sync, using each asset's *original*
 *  file (see LocalPhotoLibrary.exportOriginal) — same incremental approach as
 *  AutoUploadWorker.doWork() on Android, just PHPhotoLibrary instead of MediaStore.
 *  fetchMediaSince() already returns newest-first (see LocalPhotoLibrary.fetchMedia's sort
 *  descriptor), so on a big backlog the most recent photos land on the server first. */
private suspend fun syncNow() {
    val startedAt = NSDate().timeIntervalSince1970
    if (AppState.token == null || AppState.folderPassword == null) {
        log("syncNow: skipping — not authenticated (token=${AppState.token != null}, folderPassword=${AppState.folderPassword != null})")
        return
    }

    val defaults = NSUserDefaults.standardUserDefaults
    val lastSync = defaults.doubleForKey(KEY_LAST_SYNC)
    // 0 (not "60 seconds ago") the first time this ever runs — without a manual Upload
    // screen as a fallback, this is now the *only* path anything already in the library
    // gets to the server through, so the first sync has to catch the whole existing
    // library, not just whatever's taken in the next minute.
    val since = if (lastSync > 0) lastSync else 0.0

    val newMedia = fetchMediaSince(since.toLong())
    log("syncNow: found ${newMedia.size} item(s) newer than $since")
    if (newMedia.isEmpty()) return

    val client = buildKtorClient()
    val photoApi = PhotoApi(API_BASE_URL, client)
    val videoApi = VideoApi(API_BASE_URL, client)
    var uploaded = 0
    var failed = 0

    newMedia.forEach { item ->
        val exported = exportOriginal(item.id)
        if (exported == null) {
            failed++
            log("Skipping ${item.id} — exportOriginal() returned nothing")
            return@forEach
        }
        val (path, filename) = exported
        val bytes = readBytesAtPath("file://$path")
        if (bytes == null) {
            failed++
            log("Skipping $filename — couldn't read bytes from $path")
            return@forEach
        }
        val mimeType = if (item.isVideo) mimeTypeForFilename(filename, isVideo = true) else mimeTypeForFilename(filename, isVideo = false)
        log("Uploading ${if (item.isVideo) "video" else "image"} $filename (${bytes.size} bytes, $mimeType)…")
        if (item.isVideo) {
            runCatching { videoApi.uploadVideo(bytes, filename, mimeType) }
                .onSuccess { response -> uploaded++; log("Uploaded $filename -> video id=${response.id}") }
                .onFailure { e -> failed++; log("Failed to upload $filename: ${e::class.simpleName}: ${e.message}") }
        } else {
            runCatching { photoApi.uploadImage(bytes, filename, mimeType) }
                .onSuccess { response -> uploaded++; log("Uploaded $filename -> photo id=${response.id}") }
                .onFailure { e -> failed++; log("Failed to upload $filename: ${e::class.simpleName}: ${e.message}") }
        }
    }

    defaults.setDouble(NSDate().timeIntervalSince1970, KEY_LAST_SYNC)
    log("syncNow complete: uploaded=$uploaded, failed=$failed, elapsed=${NSDate().timeIntervalSince1970 - startedAt}s")
}

private fun mimeTypeForFilename(filename: String, isVideo: Boolean): String {
    val ext = filename.substringAfterLast('.', "").lowercase()
    return if (isVideo) {
        when (ext) { "mp4" -> "video/mp4"; "mov" -> "video/quicktime"; else -> "video/mp4" }
    } else {
        when (ext) { "png" -> "image/png"; "heic" -> "image/heic"; "gif" -> "image/gif"; else -> "image/jpeg" }
    }
}
