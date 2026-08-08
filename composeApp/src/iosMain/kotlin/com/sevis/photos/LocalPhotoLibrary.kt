package com.sevis.photos

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.*
import platform.Photos.*
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.coroutines.resume

/** A photo/video on-device — PHPhotoLibrary's counterpart to Android's LocalMediaEntity
 *  (see data/local/Entities.kt). [uri] is a cached thumbnail JPEG the app wrote to its own
 *  Caches directory (see thumbnailFile()), not a live reference into Photos — same reason
 *  MediaPicker copies picked files out rather than holding onto PHPicker's own handles. */
data class LocalMedia(
    val id: String,
    val uri: String,
    val displayName: String,
    val dateTakenMillis: Long,
    val bucketName: String?,
    val isVideo: Boolean,
)

data class LocalAlbum(val id: String, val name: String, val photoCount: Int, val coverUri: String?)

@OptIn(ExperimentalForeignApi::class)
fun hasPhotoLibraryAccess(): Boolean {
    val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
    return status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
}

@OptIn(ExperimentalForeignApi::class)
suspend fun requestPhotoLibraryAccess(): Boolean {
    if (hasPhotoLibraryAccess()) return true
    return suspendCancellableCoroutine { cont ->
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { status ->
            if (cont.isActive) {
                cont.resume(status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited)
            }
        }
    }
}

/** All photos+videos on-device, newest first — backs the Gallery pane. */
fun fetchAllMedia(): List<LocalMedia> = fetchMedia(sinceEpochSeconds = null, albumId = null)

/** Only media created after [sinceEpochSeconds] — AutoUpload's incremental-sync query
 *  (mirrors MediaStoreHelper.getImagesSince/getVideosSince). */
fun fetchMediaSince(sinceEpochSeconds: Long): List<LocalMedia> = fetchMedia(sinceEpochSeconds, albumId = null)

/** Photos+videos in one album — backs LocalAlbumPhotosScreen. */
fun fetchAlbumMedia(albumId: String): List<LocalMedia> = fetchMedia(sinceEpochSeconds = null, albumId = albumId)

/** User-created albums on-device (PHAssetCollectionType album) — backs LocalAlbumsScreen,
 *  the on-device counterpart to Android's MediaStore BUCKET_DISPLAY_NAME grouping. */
@OptIn(ExperimentalForeignApi::class)
fun fetchAlbums(): List<LocalAlbum> {
    val options = PHFetchOptions().apply {
        sortDescriptors = listOf(NSSortDescriptor(key = "localizedTitle", ascending = true))
    }
    val collections = PHAssetCollection.fetchAssetCollectionsWithType(
        PHAssetCollectionTypeAlbum,
        PHAssetCollectionSubtypeAny,
        options,
    )
    val albums = mutableListOf<LocalAlbum>()
    for (i in 0 until collections.count.toInt()) {
        val collection = collections.objectAtIndex(i.toULong()) as PHAssetCollection
        val assetOptions = PHFetchOptions().apply {
            sortDescriptors = listOf(NSSortDescriptor(key = "creationDate", ascending = false))
        }
        val assets = PHAsset.fetchAssetsInAssetCollection(collection, assetOptions)
        val count = assets.count.toInt()
        if (count == 0) continue
        val cover = assets.objectAtIndex(0U) as PHAsset
        albums += LocalAlbum(
            id = collection.localIdentifier,
            name = collection.localizedTitle ?: "Untitled",
            photoCount = count,
            coverUri = thumbnailFile(cover),
        )
    }
    return albums
}

@OptIn(ExperimentalForeignApi::class)
private fun fetchMedia(sinceEpochSeconds: Long?, albumId: String?): List<LocalMedia> {
    val options = PHFetchOptions().apply {
        sortDescriptors = listOf(NSSortDescriptor(key = "creationDate", ascending = false))
        if (sinceEpochSeconds != null) {
            predicate = NSPredicate.predicateWithFormat(
                "creationDate > %@",
                NSDate.dateWithTimeIntervalSince1970(sinceEpochSeconds.toDouble()),
            )
        }
    }

    val result = if (albumId != null) {
        val collectionOptions = PHFetchOptions()
        val collections = PHAssetCollection.fetchAssetCollectionsWithLocalIdentifiers(listOf(albumId), collectionOptions)
        if (collections.count.toInt() == 0) return emptyList()
        val collection = collections.objectAtIndex(0U) as PHAssetCollection
        PHAsset.fetchAssetsInAssetCollection(collection, options)
    } else {
        PHAsset.fetchAssetsWithOptions(options)
    }

    val items = mutableListOf<LocalMedia>()
    for (i in 0 until result.count.toInt()) {
        val asset = result.objectAtIndex(i.toULong()) as PHAsset
        val thumb = thumbnailFile(asset) ?: continue
        val created = asset.creationDate?.timeIntervalSince1970
        items += LocalMedia(
            id = asset.localIdentifier,
            uri = thumb,
            displayName = asset.localIdentifier.substringBefore("/"),
            dateTakenMillis = ((created ?: 0.0) * 1000).toLong(),
            bucketName = null,
            isVideo = asset.mediaType == PHAssetMediaTypeVideo,
        )
    }
    return items
}

/**
 * A reasonably-sized (1024px long edge) JPEG cached to the app's Caches directory, keyed by
 * the asset's stable localIdentifier so repeat lookups across launches are free. This is the
 * one simplification vs. Android's LocalLibraryScreen, which loads MediaStore's content:// Uri
 * directly at full resolution in the lightbox — PHAsset has no directly-loadable Uri Coil can
 * read, only an async image-data API, so everything (grid thumbnail and lightbox alike) uses
 * this same cached size here. Blocking (PHImageRequestOptions.synchronous = true) — always
 * call from a background dispatcher.
 */
@OptIn(ExperimentalForeignApi::class)
fun thumbnailFile(asset: PHAsset): String? {
    val cacheDir = (NSFileManager.defaultManager.URLsForDirectory(
        NSCachesDirectory,
        NSUserDomainMask,
    ).firstOrNull() as? NSURL)?.path ?: return null
    val dir = "$cacheDir/local_media_thumbs"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    val safeName = asset.localIdentifier.replace("/", "_")
    val path = "$dir/$safeName.jpg"

    if (NSFileManager.defaultManager.fileExistsAtPath(path)) return "file://$path"

    val options = PHImageRequestOptions().apply {
        synchronous = true
        deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
        resizeMode = PHImageRequestOptionsResizeModeFast
    }
    var jpegData: NSData? = null
    PHImageManager.defaultManager().requestImageForAsset(
        asset,
        targetSize = CGSizeMake(1024.0, 1024.0),
        contentMode = PHImageContentModeAspectFill,
        options = options,
    ) { image: UIImage?, _ ->
        if (image != null) jpegData = UIImageJPEGRepresentation(image, 0.8)
    }
    val data = jpegData ?: return null
    return if (data.writeToFile(path, atomically = true)) "file://$path" else null
}

/**
 * Writes the asset's *original* file bytes (full resolution, untranscoded — for both photos
 * and videos, unlike thumbnailFile() above) to a tmp file, for AutoUpload. A real backup
 * feature must upload the original, not the 1024px thumbnail cached for grid/lightbox display.
 * Returns (filePath, originalFilename), or null if the asset has no exportable resource.
 */
@OptIn(ExperimentalForeignApi::class)
suspend fun exportOriginal(assetLocalId: String): Pair<String, String>? {
    val fetchOptions = PHFetchOptions()
    val result = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(assetLocalId), fetchOptions)
    if (result.count.toInt() == 0) return null
    val asset = result.objectAtIndex(0U) as PHAsset

    @Suppress("UNCHECKED_CAST")
    val resources = PHAssetResource.assetResourcesForAsset(asset) as List<PHAssetResource>
    val resource = resources.firstOrNull() ?: return null
    val filename = resource.originalFilename

    val destDir = NSTemporaryDirectory() + "originals/"
    NSFileManager.defaultManager.createDirectoryAtPath(destDir, withIntermediateDirectories = true, attributes = null, error = null)
    val destPath = destDir + filename
    val destUrl = NSURL.fileURLWithPath(destPath)
    NSFileManager.defaultManager.removeItemAtURL(destUrl, error = null)

    val success = suspendCancellableCoroutine<Boolean> { cont ->
        PHAssetResourceManager.defaultManager().writeDataForAssetResource(resource, toFile = destUrl, options = null) { error ->
            if (cont.isActive) cont.resume(error == null)
        }
    }
    return if (success) destPath to filename else null
}
