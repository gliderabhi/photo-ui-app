package com.sevis.photos.facedetect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.core.graphics.scale
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.io.FileOutputStream

data class DetectedFace(val bounds: Rect, val thumbnailPath: String)

/**
 * On-device face detection via ML Kit's bundled face-detection model — no network calls,
 * detection only (no identity/recognition; see FaceEmbedder for the "same person" step).
 */
object FaceDetector {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
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
            val clamped = Rect(
                bounds.left.coerceIn(0, bitmap.width),
                bounds.top.coerceIn(0, bitmap.height),
                bounds.right.coerceIn(0, bitmap.width),
                bounds.bottom.coerceIn(0, bitmap.height)
            )
            if (clamped.width() <= 0 || clamped.height() <= 0) return@mapIndexedNotNull null
            val crop = Bitmap.createBitmap(bitmap, clamped.left, clamped.top, clamped.width(), clamped.height())
            val path = saveThumbnail(context, mediaId, index, crop)
            DetectedFace(clamped, path)
        }
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
