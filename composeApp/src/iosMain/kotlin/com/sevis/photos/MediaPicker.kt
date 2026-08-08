package com.sevis.photos

import com.sevis.photos.data.ImageFile
import com.sevis.photos.data.VideoFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSItemProvider
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.UniformTypeIdentifiers.UTTypeMovie
import platform.darwin.NSObject
import kotlin.coroutines.resume

// Kept alive at file scope for the same reason as GoogleAuth's context provider —
// PHPickerViewController.delegate is a weak reference.
private var activePickerDelegate: NSObject? = null

/**
 * iOS's counterpart to MainActivity's mediaPicker (PickMultipleVisualMedia) — presents the
 * system PHPickerViewController for images+videos, then copies each picked item out of
 * PHPicker's ephemeral provider into the app's own tmp directory (the same reason Android
 * copies bytes out of a content:// Uri: nothing guarantees the source stays readable/stable
 * later), classifying the results the same way Android's readImageFile/readVideoFile do.
 */
@OptIn(ExperimentalForeignApi::class)
suspend fun pickMedia(): Pair<List<ImageFile>, List<VideoFile>> {
    val results = presentPicker()
    val images = mutableListOf<ImageFile>()
    val videos = mutableListOf<VideoFile>()

    for (result in results) {
        val provider = result.itemProvider
        val isVideo = provider.hasItemConformingToTypeIdentifier(UTTypeMovie.identifier)
        val typeIdentifier = if (isVideo) UTTypeMovie.identifier else UTTypeImage.identifier
        if (!provider.hasItemConformingToTypeIdentifier(typeIdentifier)) continue

        val sourceUrl = loadFileRepresentation(provider, typeIdentifier) ?: continue
        val ext = sourceUrl.pathExtension?.takeIf { it.isNotBlank() } ?: if (isVideo) "mov" else "jpg"
        val suggested = provider.suggestedName?.takeIf { it.isNotBlank() } ?: "media"
        val name = if (suggested.contains(".")) suggested else "$suggested.$ext"
        val destPath = copyToTmp(sourceUrl, name) ?: continue

        if (isVideo) videos += VideoFile(uri = destPath, name = name, mimeType = mimeTypeFor(ext, isVideo = true))
        else images += ImageFile(uri = destPath, name = name, mimeType = mimeTypeFor(ext, isVideo = false))
    }
    return images to videos
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun presentPicker(): List<PHPickerResult> = suspendCancellableCoroutine { cont ->
    val config = PHPickerConfiguration()
    config.selectionLimit = 0L // unlimited, like PickMultipleVisualMedia
    config.filter = PHPickerFilter.anyFilterMatchingSubfilters(listOf(PHPickerFilter.imagesFilter(), PHPickerFilter.videosFilter()))

    val picker = PHPickerViewController(configuration = config)
    val delegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, completion = null)
            activePickerDelegate = null
            @Suppress("UNCHECKED_CAST")
            val results = didFinishPicking as List<PHPickerResult>
            if (cont.isActive) cont.resume(results)
        }
    }
    picker.delegate = delegate
    activePickerDelegate = delegate
    cont.invokeOnCancellation { activePickerDelegate = null }

    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
    if (root != null) {
        root.presentViewController(picker, animated = true, completion = null)
    } else if (cont.isActive) {
        cont.resume(emptyList())
    }
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun loadFileRepresentation(provider: NSItemProvider, typeIdentifier: String): NSURL? =
    suspendCancellableCoroutine { cont ->
        provider.loadFileRepresentationForTypeIdentifier(typeIdentifier) { url, _ ->
            if (cont.isActive) cont.resume(url)
        }
    }

/** PHPicker's file representation only lives for the duration of its completion handler —
 *  copy it into our own tmp directory so it's still there when uploadImage/uploadVideo runs. */
@OptIn(ExperimentalForeignApi::class)
private fun copyToTmp(source: NSURL, filename: String): String? {
    val fileManager = NSFileManager.defaultManager
    val dir = NSTemporaryDirectory() + "picked_media/"
    fileManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    val destPath = dir + "${NSUUID().UUIDString()}_$filename"
    val destUrl = NSURL.fileURLWithPath(destPath)
    val copied = fileManager.copyItemAtURL(source, destUrl, error = null)
    return if (copied) destUrl.absoluteString else null
}

private fun mimeTypeFor(ext: String, isVideo: Boolean): String {
    val e = ext.lowercase()
    return if (isVideo) {
        when (e) {
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            else -> "video/mp4"
        }
    } else {
        when (e) {
            "png" -> "image/png"
            "heic" -> "image/heic"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }
}
