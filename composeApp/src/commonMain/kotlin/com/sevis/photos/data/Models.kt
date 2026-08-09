package com.sevis.photos.data

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(val token: String)

@Serializable
data class PhotoResponse(
    val id: Int,
    val originalFilename: String,
    val contentType: String,
    val fileSize: Long,
    val uploadedAt: String,
    val url: String = ""
)

@Serializable
data class PhotosByDate(
    val date: String,
    val photos: List<PhotoResponse>
)

/** Filenames (as recorded at upload time) of every photo already on the server for this
 *  user — used to badge on-device Gallery photos that are already backed up (see
 *  photoGridItems on both platforms). A filename match isn't a perfect identity check (a
 *  local photo could coincidentally share a name with an unrelated upload, or the same
 *  photo could show as "not uploaded" if renamed since), but it needs no new backend
 *  endpoint, no per-photo hashing, and no extra network round trip beyond the listPhotos()
 *  call the app already needs elsewhere — a reasonable tradeoff for what's just a visual
 *  hint, not a source of truth. */
fun uploadedFilenamesFrom(byDate: List<PhotosByDate>): Set<String> =
    byDate.flatMapTo(mutableSetOf()) { group -> group.photos.map { it.originalFilename } }

@Serializable
data class AlbumResponse(
    val id: Int,
    val name: String,
    val photoCount: Int,
    val createdAt: String,
    val coverPhoto: PhotoResponse? = null
)

@Serializable
data class MessageResponse(val message: String)

/** A face detected within one photo, from photo-service's server-side
 *  face-service pipeline. Box coordinates are fractions (0..1) of the
 *  photo's width/height. */
@Serializable
data class FaceResponse(
    val id: Long,
    val photoId: Long,
    val personId: Long? = null,
    val boxTop: Double,
    val boxRight: Double,
    val boxBottom: Double,
    val boxLeft: Double
)

/** A cluster of faces the server believes are the same person (see
 *  photo-service's FaceService) — unnamed until the user labels it. */
@Serializable
data class PersonResponse(
    val id: Long,
    val label: String? = null,
    val faceCount: Int,
    val coverPhotoId: Long? = null,
    val coverBoxTop: Double? = null,
    val coverBoxRight: Double? = null,
    val coverBoxBottom: Double? = null,
    val coverBoxLeft: Double? = null
)

/** Served as a static file (downloads/version.json) alongside the APKs — lets the app
 *  check whether a newer build is available without unconditionally re-downloading it. */
@Serializable
data class AppVersionResponse(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String? = null
)

@Serializable
data class FolderStatusResponse(val hasFolder: Boolean)

/** A locally-picked image file, used in the upload queue. */
data class ImageFile(
    val uri: String,
    val name: String,
    val mimeType: String
)

enum class UploadStatus { PENDING, UPLOADING, DONE, ERROR }

data class UploadItem(
    val file: ImageFile,
    val status: UploadStatus = UploadStatus.PENDING,
    val errorMsg: String? = null
)

/** A locally-picked video file, used in the video upload queue. */
data class VideoFile(
    val uri: String,
    val name: String,
    val mimeType: String
)

data class VideoUploadItem(
    val file: VideoFile,
    val status: UploadStatus = UploadStatus.PENDING,
    val errorMsg: String? = null
)

@Serializable
data class VideoResponse(
    val id: String,
    val title: String,
    val originalFilename: String,
    val status: String,
    val durationSeconds: Int = 0,
    val fileSizeBytes: Long = 0,
    val transcodeProgress: Int = 0,
    val errorMessage: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val masterPlaylistUrl: String? = null,
    val rawStreamUrl: String? = null,
    val availableQualities: List<String> = emptyList(),
    val createdAt: String? = null
)
