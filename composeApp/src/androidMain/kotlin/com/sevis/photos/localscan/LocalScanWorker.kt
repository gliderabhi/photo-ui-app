package com.sevis.photos.localscan

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sevis.photos.autoupload.MediaStoreHelper
import com.sevis.photos.data.local.FaceEntity
import com.sevis.photos.data.local.LocalMediaEntity
import com.sevis.photos.data.local.PhotosDatabase
import com.sevis.photos.facedetect.FaceDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the on-device MediaStore into the local Room library, then runs on-device face
 * detection for any photo not yet processed. Entirely local — no network calls, unlike
 * AutoUploadWorker which this mirrors the structure of.
 */
class LocalScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "LocalScanWorker"
        private const val WORK_NAME = "local_scan"

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<LocalScanWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = PhotosDatabase.get(applicationContext)
        val mediaDao = db.localMediaDao()
        val faceDao = db.faceDao()

        // Step 1: sync MediaStore -> local_media, skipping photos already known.
        val known = mediaDao.allMediaStoreIds().toSet()
        val libraryImages = MediaStoreHelper.getAllLibraryImages(applicationContext)
        val newEntities = libraryImages
            .filter { it.id !in known }
            .map {
                LocalMediaEntity(
                    mediaStoreId = it.id,
                    uri = it.uri.toString(),
                    displayName = it.name,
                    dateTakenMillis = it.dateTakenMillis,
                    latitude = it.latitude,
                    longitude = it.longitude
                )
            }
        if (newEntities.isNotEmpty()) {
            mediaDao.insertAll(newEntities)
            Log.d(TAG, "Added ${newEntities.size} new local photos")
        }

        // Step 2: on-device face detection for anything not scanned yet.
        val unscanned = mediaDao.unscannedForFaces()
        Log.d(TAG, "Running face detection on ${unscanned.size} photos")
        unscanned.forEach { media ->
            runCatching {
                val faces = FaceDetector.detectFaces(applicationContext, media.id, android.net.Uri.parse(media.uri))
                faces.forEach { face ->
                    faceDao.insert(
                        FaceEntity(
                            mediaId = media.id,
                            boundsLeft = face.bounds.left,
                            boundsTop = face.bounds.top,
                            boundsRight = face.bounds.right,
                            boundsBottom = face.bounds.bottom,
                            thumbnailPath = face.thumbnailPath
                        )
                    )
                }
                mediaDao.markFaceScanned(media.id)
            }.onFailure { e -> Log.w(TAG, "Face scan failed for media ${media.id}: ${e.message}") }
        }

        Result.success()
    }
}
