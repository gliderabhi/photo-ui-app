package com.sevis.photos.localscan

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sevis.photos.AppForegroundState
import com.sevis.photos.autoupload.MediaStoreHelper
import com.sevis.photos.data.local.FaceEntity
import com.sevis.photos.data.local.LocalMediaEntity
import com.sevis.photos.data.local.PersonEntity
import com.sevis.photos.data.local.PhotosDatabase
import com.sevis.photos.facedetect.FaceDetector
import com.sevis.photos.facedetect.FaceEmbedder
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
        private const val BATCH_SIZE = 25

        // Cosine similarity over SFace embeddings (see FaceEmbedder) — 0.363 is
        // OpenCV Zoo's published match threshold for this model, not a value
        // tuned against this app's own library.
        private const val CLUSTER_THRESHOLD = 0.363f

        // Bounds how many stored faces a single person can be matched against.
        // Without this, a person's cluster (some ran to 1000+ faces on a real
        // device library) keeps gaining more chances for a spurious high-
        // similarity hit purely from face count, not genuine resemblance —
        // classic extreme-value inflation in a "max over N comparisons" scheme.
        // Capping keeps matching quality and performance stable regardless of
        // how large any one person's cluster grows, on any device/library size.
        private const val MAX_EXEMPLARS_PER_PERSON = 50

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
        val personDao = db.personDao()

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

        // Step 2: on-device face detection for anything not scanned yet, written
        // in batches — see the comment on markFacesScanned for why this can't be
        // one DB write per photo without stalling every observer of local_media.
        val unscanned = mediaDao.unscannedForFaces()
        Log.d(TAG, "Running face detection on ${unscanned.size} photos")

        // Every existing face embedding, grouped by person — matching against
        // *all* of a person's stored faces (not just one fixed anchor photo)
        // means a bad-angle/lighting anchor no longer causes every other
        // legitimate match to fall through. Held in memory and appended to as
        // new faces/people are created during this run, so later photos in
        // the same run can match against them too.
        val clusters: MutableMap<Long, MutableList<FloatArray>> = HashMap()
        faceDao.allClusteredFaceEmbeddings().forEach { f ->
            val pid = f.personId ?: return@forEach
            val emb = f.embedding?.let { FaceEmbedder.decode(it) } ?: return@forEach
            clusters.getOrPut(pid) { mutableListOf() }.add(emb)
        }

        var processedCount = 0
        for (batch in unscanned.chunked(BATCH_SIZE)) {
            if (AppForegroundState.isForeground.value) {
                // App has come back to the foreground mid-run — stop here. The
                // faceScanned flag means whatever's left picks up next time the
                // app backgrounds again, via the onStop-triggered runOnce() call.
                Log.d(TAG, "App is foreground — pausing face detection, ${unscanned.size - processedCount} photos remaining")
                return@withContext Result.success()
            }
            val newFaces = mutableListOf<FaceEntity>()
            val scannedIds = mutableListOf<Long>()
            batch.forEach { media ->
                runCatching {
                    val faces = FaceDetector.detectFaces(applicationContext, media.id, android.net.Uri.parse(media.uri))
                    faces.forEach { face ->
                        val embedding = face.embedding
                        if (embedding == null) {
                            // Not enough landmarks to align (e.g. a strong profile
                            // shot) — store the detection but leave it unclustered
                            // rather than matching off a degenerate alignment.
                            newFaces += FaceEntity(
                                mediaId = media.id,
                                personId = null,
                                boundsLeft = face.bounds.left,
                                boundsTop = face.bounds.top,
                                boundsRight = face.bounds.right,
                                boundsBottom = face.bounds.bottom,
                                thumbnailPath = face.thumbnailPath,
                                embedding = null
                            )
                            return@forEach
                        }
                        // Best match is whichever person has the single closest face
                        // among their stored faces (capped at MAX_EXEMPLARS_PER_PERSON).
                        val best = clusters.entries
                            .map { (pid, embs) -> pid to (embs.maxOfOrNull { FaceEmbedder.similarity(it, embedding) } ?: -1f) }
                            .maxByOrNull { it.second }
                        val personId = if (best != null && best.second >= CLUSTER_THRESHOLD) {
                            val embs = clusters.getValue(best.first)
                            if (embs.size < MAX_EXEMPLARS_PER_PERSON) embs += embedding
                            best.first
                        } else {
                            val newId = personDao.insert(PersonEntity(coverFacePath = face.thumbnailPath))
                            clusters[newId] = mutableListOf(embedding)
                            newId
                        }
                        newFaces += FaceEntity(
                            mediaId = media.id,
                            personId = personId,
                            boundsLeft = face.bounds.left,
                            boundsTop = face.bounds.top,
                            boundsRight = face.bounds.right,
                            boundsBottom = face.bounds.bottom,
                            thumbnailPath = face.thumbnailPath,
                            embedding = FaceEmbedder.encode(embedding)
                        )
                    }
                    scannedIds += media.id
                }.onFailure { e -> Log.w(TAG, "Face scan failed for media ${media.id}: ${e.message}") }
            }
            if (newFaces.isNotEmpty()) faceDao.insertAll(newFaces)
            if (scannedIds.isNotEmpty()) mediaDao.markFacesScanned(scannedIds)
            processedCount += batch.size
        }

        // Step 3: reverse-geocode photo GPS into place names, one lookup per
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
