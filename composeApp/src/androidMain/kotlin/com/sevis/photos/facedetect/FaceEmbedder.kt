package com.sevis.photos.facedetect

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.google.mlkit.vision.face.FaceLandmark
import java.nio.FloatBuffer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * On-device face-embedding via SFace (a MobileFaceNet-architecture model trained
 * with SFace loss), bundled from OpenCV Zoo — Apache-2.0 licensed, weights and
 * code both redistributable:
 * https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface
 *
 * A real trained face-recognition model (unlike the earlier landmark-geometry
 * heuristic this replaces), so it can actually tell people apart: benchmarked
 * ~99.3% pair accuracy, vs. the ~55-75% ceiling measured for pure landmark
 * geometry against this app's own on-device library.
 */
object FaceEmbedder {

    private const val INPUT_SIZE = 112

    // Standard ArcFace-family 112x112 reference points (left eye, right eye, nose
    // base, left mouth corner, right mouth corner) — matches OpenCV's
    // FaceRecognizerSF::alignCrop, which is what this model was trained/
    // benchmarked against.
    private val REFERENCE_POINTS = arrayOf(
        floatArrayOf(38.2946f, 51.6963f),
        floatArrayOf(73.5318f, 51.5014f),
        floatArrayOf(56.0252f, 71.7366f),
        floatArrayOf(41.5493f, 92.3655f),
        floatArrayOf(70.7299f, 92.2041f)
    )

    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null

    private fun getSession(context: Context): OrtSession {
        session?.let { return it }
        synchronized(this) {
            session?.let { return it }
            val bytes = context.applicationContext.assets.open("sface.onnx").use { it.readBytes() }
            return ortEnv.createSession(bytes).also { session = it }
        }
    }

    /**
     * Aligns the face to the canonical 112x112 crop the model expects (via a
     * similarity transform fit from 5 landmark points) and runs the embedding
     * model. Returns null if ML Kit didn't return enough landmarks to align
     * (e.g. a strong profile shot) — such faces are stored unclustered rather
     * than matched off a degenerate alignment.
     */
    fun embed(context: Context, bitmap: Bitmap, landmarks: Map<Int, PointF>): FloatArray? {
        val srcPoints = fivePoints(landmarks) ?: return null
        val matrix = similarityTransform(srcPoints, REFERENCE_POINTS) ?: return null

        val aligned = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(aligned).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        aligned.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        aligned.recycle()

        // NCHW, RGB channel order — the graph itself applies (x-127.5)*0.0078125,
        // so raw 0..255 values go in as-is.
        val input = FloatBuffer.allocate(3 * INPUT_SIZE * INPUT_SIZE)
        for (shift in intArrayOf(16, 8, 0)) {
            for (p in pixels) input.put(((p shr shift) and 0xFF).toFloat())
        }
        input.rewind()

        val session = getSession(context)
        OnnxTensor.createTensor(ortEnv, input, longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())).use { tensor ->
            session.run(mapOf("data" to tensor)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val output = (result[0].value as Array<FloatArray>)[0]
                return l2Normalize(output)
            }
        }
    }

    /** Cosine similarity over already-L2-normalized embeddings; higher means more similar. */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    fun encode(vec: FloatArray): String = vec.joinToString(",")
    fun decode(str: String): FloatArray = str.split(",").map { it.toFloat() }.toFloatArray()

    private fun fivePoints(landmarks: Map<Int, PointF>): Array<FloatArray>? {
        val order = listOf(
            FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE,
            FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT
        )
        val points = order.map { landmarks[it] ?: return null }
        return Array(5) { i -> floatArrayOf(points[i].x, points[i].y) }
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sumSq = 0f
        for (x in v) sumSq += x * x
        val norm = sqrt(sumSq).coerceAtLeast(1e-6f)
        return FloatArray(v.size) { v[it] / norm }
    }

    /**
     * Least-squares similarity transform (rotation + uniform scale + translation)
     * mapping [src] points onto [dst] points, equivalent to OpenCV's SVD-based
     * getSimilarityTransformMatrix (Umeyama's method) but using a closed-form
     * analytic 2x2 SVD instead of a general SVD routine — verified to match
     * numpy's SVD-based result to ~1e-13 across randomized trials. Returns a
     * Matrix mapping src (original photo) coordinates onto dst (112x112
     * reference) coordinates, for use directly with Canvas.drawBitmap.
     */
    private fun similarityTransform(src: Array<FloatArray>, dst: Array<FloatArray>): Matrix? {
        val n = src.size
        val srcMeanX = src.sumOf { it[0].toDouble() } / n
        val srcMeanY = src.sumOf { it[1].toDouble() } / n
        val dstMeanX = dst.sumOf { it[0].toDouble() } / n
        val dstMeanY = dst.sumOf { it[1].toDouble() } / n

        var a00 = 0.0; var a01 = 0.0; var a10 = 0.0; var a11 = 0.0
        var varSrc = 0.0
        for (i in 0 until n) {
            val sx = src[i][0] - srcMeanX
            val sy = src[i][1] - srcMeanY
            val dx = dst[i][0] - dstMeanX
            val dy = dst[i][1] - dstMeanY
            a00 += dx * sx; a01 += dx * sy
            a10 += dy * sx; a11 += dy * sy
            varSrc += sx * sx + sy * sy
        }
        a00 /= n; a01 /= n; a10 /= n; a11 /= n; varSrc /= n
        if (varSrc < 1e-6) return null

        // Analytic 2x2 SVD of A = [[a00,a01],[a10,a11]]: A = U * diag(sx1,sy1) * Vt.
        val e = (a00 + a11) / 2.0; val f = (a00 - a11) / 2.0
        val g = (a10 + a01) / 2.0; val h = (a10 - a01) / 2.0
        val q = hypot(e, h); val r = hypot(f, g)
        val sx1 = q + r; val sy1 = q - r
        val a1 = atan2(g, f); val a2 = atan2(h, e)
        val theta = (a2 - a1) / 2.0; val phi = (a2 + a1) / 2.0

        var d2 = 1.0
        if (a00 * a11 - a01 * a10 < 0) d2 = -1.0

        val cosPhi = cos(phi); val sinPhi = sin(phi)
        val cosTheta = cos(theta); val sinTheta = sin(theta)
        // R = U * diag(1, d2) * Vt
        val u00 = cosPhi; val u01 = -sinPhi; val u10 = sinPhi; val u11 = cosPhi
        val vt00 = cosTheta; val vt01 = -sinTheta; val vt10 = sinTheta; val vt11 = cosTheta
        val ud00 = u00; val ud01 = u01 * d2
        val ud10 = u10; val ud11 = u11 * d2
        val r00 = ud00 * vt00 + ud01 * vt10
        val r01 = ud00 * vt01 + ud01 * vt11
        val r10 = ud10 * vt00 + ud11 * vt10
        val r11 = ud10 * vt01 + ud11 * vt11

        val scale = (1.0 / varSrc) * (sx1 + sy1 * d2)
        val m00 = r00 * scale; val m01 = r01 * scale
        val m10 = r10 * scale; val m11 = r11 * scale
        val tx = dstMeanX - (m00 * srcMeanX + m01 * srcMeanY)
        val ty = dstMeanY - (m10 * srcMeanX + m11 * srcMeanY)

        return Matrix().apply {
            setValues(
                floatArrayOf(
                    m00.toFloat(), m01.toFloat(), tx.toFloat(),
                    m10.toFloat(), m11.toFloat(), ty.toFloat(),
                    0f, 0f, 1f
                )
            )
        }
    }
}
