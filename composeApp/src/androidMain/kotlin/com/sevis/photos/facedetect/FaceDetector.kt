package com.sevis.photos.facedetect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.scale
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream

data class DetectedFace(
    val bounds: Rect,
    val thumbnailPath: String,
    /** ML Kit FaceLandmark.LandmarkType -> position, in the same bitmap coordinate space as [bounds]. */
    val landmarks: Map<Int, PointF>,
    /** SFace embedding (see FaceEmbedder) — null if landmarks weren't sufficient to align the face. */
    val embedding: FloatArray?
)

/**
 * On-device face detection via ML Kit's bundled face-detection model — no network calls,
 * detection only (no identity/recognition; see FaceEmbedder for the "same person" step).
 */
object FaceDetector {

    // Extra margin beyond the tight ML Kit bounding box for the *saved thumbnail* only
    // (nicer-looking person avatars that include some neck/ears/hair, not just a tight
    // face crop) — has no effect on matching, which uses landmark geometry instead.
    private const val PAD_SIDES = 0.25f
    private const val PAD_TOP = 0.35f
    private const val PAD_BOTTOM = 0.45f

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()
        )
    }

    /** Runs detection synchronously — call from a background thread/worker only. */
    fun detectFaces(context: Context, mediaId: Long, uri: Uri): List<DetectedFace> {
        val bitmap = loadDownsampledBitmap(context, uri) ?: return emptyList()
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val faces = Tasks.await(detector.process(inputImage))
        return faces.mapIndexedNotNull { index, face ->
            val bounds = face.boundingBox
            val padded = paddedBounds(bounds, bitmap.width, bitmap.height)
            if (padded.width() <= 0 || padded.height() <= 0) return@mapIndexedNotNull null
            val crop = Bitmap.createBitmap(bitmap, padded.left, padded.top, padded.width(), padded.height())
            val path = saveThumbnail(context, mediaId, index, crop)
            val landmarks = face.allLandmarks.associate { it.landmarkType to it.position }
            val embedding = FaceEmbedder.embed(context, bitmap, landmarks)
            DetectedFace(padded, path, landmarks, embedding)
        }
    }

    private fun paddedBounds(bounds: Rect, maxWidth: Int, maxHeight: Int): Rect {
        val padX = (bounds.width() * PAD_SIDES).toInt()
        val padTop = (bounds.height() * PAD_TOP).toInt()
        val padBottom = (bounds.height() * PAD_BOTTOM).toInt()
        return Rect(
            (bounds.left - padX).coerceIn(0, maxWidth),
            (bounds.top - padTop).coerceIn(0, maxHeight),
            (bounds.right + padX).coerceIn(0, maxWidth),
            (bounds.bottom + padBottom).coerceIn(0, maxHeight)
        )
    }

    private fun loadDownsampledBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val original = android.graphics.BitmapFactory.decodeStream(stream) ?: return null
                // Cap the long edge so detection stays fast on large camera photos.
                val maxDim = 1600
                val scale = maxDim.toFloat() / maxOf(original.width, original.height)
                if (scale < 1f) original.scale((original.width * scale).toInt(), (original.height * scale).toInt())
                else original
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveThumbnail(context: Context, mediaId: Long, index: Int, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "face_thumbs").apply { mkdirs() }
        val file = File(dir, "${mediaId}_$index.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }
}
