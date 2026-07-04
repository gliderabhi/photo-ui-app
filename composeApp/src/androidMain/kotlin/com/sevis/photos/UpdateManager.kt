package com.sevis.photos

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Same in-app "download the latest APK and launch the system installer" flow
 *  the stream-tv app uses — mobile has a browser to fall back on, but TV
 *  doesn't, so this needs to work without one on either flavor. */
object UpdateManager {

    fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val conn = URL(apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 120_000
                val total = conn.contentLength.toLong()

                val outDir = File(context.filesDir, "updates").also { it.mkdirs() }
                val apkFile = File(outDir, "update.apk")

                conn.inputStream.use { inp ->
                    apkFile.outputStream().use { out ->
                        val buf = ByteArray(32_768)
                        var downloaded = 0L
                        var n: Int
                        while (inp.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) handler.post { onProgress((downloaded * 100 / total).toInt()) }
                        }
                    }
                }
                conn.disconnect()

                handler.post {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
                    val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                handler.post { onError(e.message ?: "Download failed") }
            }
        }.start()
    }
}
