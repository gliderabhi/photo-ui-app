package com.sevis.photos.autoupload

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class MediaImage(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val dateAdded: Long
)

/** A photo in the on-device library, with the date/location fields the local gallery needs. */
data class LocalLibraryImage(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateTakenMillis: Long,
    val latitude: Double?,
    val longitude: Double?
)

data class MediaVideo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val dateAdded: Long
)

object MediaStoreHelper {

    fun getVideosSince(context: Context, sinceEpochSeconds: Long): List<MediaVideo> {
        val results = mutableListOf<MediaVideo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Video.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(sinceEpochSeconds.toString())
        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} ASC"

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString())
                results.add(
                    MediaVideo(
                        id = id,
                        uri = uri,
                        name = cursor.getString(nameCol) ?: "video_$id.mp4",
                        mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                        dateAdded = cursor.getLong(dateCol)
                    )
                )
            }
        }
        return results
    }

    fun getImagesSince(context: Context, sinceEpochSeconds: Long): List<MediaImage> {
        val results = mutableListOf<MediaImage>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = "${MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(sinceEpochSeconds.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} ASC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                results.add(
                    MediaImage(
                        id = id,
                        uri = uri,
                        name = cursor.getString(nameCol) ?: "image_$id.jpg",
                        mimeType = cursor.getString(mimeCol) ?: "image/jpeg",
                        dateAdded = cursor.getLong(dateCol)
                    )
                )
            }
        }
        return results
    }

    fun readBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    /** Every image in the on-device library, for the local-first gallery scan. */
    fun getAllLibraryImages(context: Context): List<LocalLibraryImage> {
        val results = mutableListOf<LocalLibraryImage>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
                val dateTaken = cursor.getLong(dateTakenCol).takeIf { it > 0 }
                    ?: cursor.getLong(dateAddedCol) * 1000
                val latLon = readExifLatLon(context, uri)
                results.add(
                    LocalLibraryImage(
                        id = id,
                        uri = uri,
                        name = cursor.getString(nameCol) ?: "image_$id.jpg",
                        dateTakenMillis = dateTaken,
                        latitude = latLon?.first,
                        longitude = latLon?.second
                    )
                )
            }
        }
        return results
    }

    /** GPS coordinates embedded in the image's EXIF data, if any. Requires ACCESS_MEDIA_LOCATION on API 29+. */
    private fun readExifLatLon(context: Context, uri: Uri): Pair<Double, Double>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = androidx.exifinterface.media.ExifInterface(stream)
                exif.getLatLong()?.let { (lat, lon) -> lat to lon }
            }
        } catch (e: Exception) {
            null
        }
    }
}
