package com.sevis.photos.localscan

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sevis.photos.autoupload.MediaStoreHelper
import com.sevis.photos.data.local.LocalMediaEntity
import com.sevis.photos.data.local.PhotosDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scans the on-device MediaStore into the local Room library, then reverse-geocodes
 * GPS into place names. Entirely local — no network calls beyond Geocoder, mirrors
 * AutoUploadWorker's structure otherwise. Runs on backgrounding (see PhotosApplication's
 * onStop-triggered runOnce()), same as when face detection lived here.
 *
 * Face detection/clustering used to run here on-device (SFace via ONNX Runtime) but has
 * moved server-side: face-service now detects + clusters faces from the plaintext bytes
 * photo-service has in hand at upload time (see photo-service's FaceService), fetched via
 * GET /api/photos/people and /api/photos/{id}/faces instead of being computed and stored
 * locally. See [[project-photos-mobile]] for the wider app.
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
                    longitude = it.longitude,
                    bucketName = it.bucketName
                )
            }
        if (newEntities.isNotEmpty()) {
            mediaDao.insertAll(newEntities)
            Log.d(TAG, "Added ${newEntities.size} new local photos")
        }

        // Step 2: reverse-geocode photo GPS into place names, one lookup per
        // nearby cluster rather than per photo (see LocationLabeler).
        val unresolved = mediaDao.unresolvedForPlace()
        Log.d(TAG, "Resolving place names for ${unresolved.size} photos")
        val (withGps, withoutGps) = unresolved.partition { it.latitude != null && it.longitude != null }
        if (withoutGps.isNotEmpty()) {
            mediaDao.markPlaceResolved(withoutGps.map { it.id }, null)
        }
        withGps.groupBy { LocationLabeler.clusterKey(it.latitude!!, it.longitude!!) }
            .forEach { (_, cluster) ->
                runCatching {
                    val anchor = cluster.first()
                    val placeName = LocationLabeler.resolve(applicationContext, anchor.latitude!!, anchor.longitude!!)
                    mediaDao.markPlaceResolved(cluster.map { it.id }, placeName)
                }.onFailure { e -> Log.w(TAG, "Place resolution failed for cluster: ${e.message}") }
            }

        Result.success()
    }
}
