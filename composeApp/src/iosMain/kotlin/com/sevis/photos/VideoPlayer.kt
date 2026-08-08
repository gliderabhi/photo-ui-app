package com.sevis.photos

import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIApplication

private val playerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * iOS's counterpart to VideoPlayerActivity — presents the system AVPlayerViewController
 * (native playback UI, AirPlay, PiP) modally over the current screen, same as Android
 * launching a dedicated player Activity.
 *
 * Caveat vs. Android: ExoPlayer's DefaultHttpDataSource forwards a custom Authorization
 * header on every underlying HTTP request, including each individual HLS segment.
 * AVURLAsset's equivalent (AVURLAssetHTTPHeaderFieldsKey) isn't available in this SDK's
 * Kotlin/Native bindings at all (Apple has been deprecating it in favor of a custom
 * AVAssetResourceLoaderDelegate, real but substantially more code). So instead: when a
 * direct [rawUrl] (progressive MP4) is available, it's downloaded once through the same
 * authenticated Ktor client used for uploads and played back from a local file — no
 * per-request headers needed at all once it's local. The HLS [url] fallback (no rawUrl
 * yet) plays directly from the remote URL with no auth header, so it only works today
 * if that particular stream happens to be reachable unauthenticated.
 */
fun presentVideoPlayer(url: String, rawUrl: String?) {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
    val controller = AVPlayerViewController()
    root.presentViewController(controller, animated = true, completion = null)

    if (rawUrl != null) {
        playerScope.launch {
            val localPath = downloadToTmp(rawUrl) ?: return@launch
            val player = AVPlayer(uRL = NSURL.fileURLWithPath(localPath))
            controller.player = player
            player.play()
        }
    } else {
        val nsUrl = NSURL(string = url) ?: return
        val player = AVPlayer(uRL = nsUrl)
        controller.player = player
        player.play()
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun downloadToTmp(url: String): String? {
    val bytes: ByteArray = buildKtorClient().get(url).body()
    val dir = NSTemporaryDirectory() + "videos/"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    val path = dir + "${NSUUID().UUIDString()}.mp4"
    return if (writeBytesToFile(bytes, path)) path else null
}
