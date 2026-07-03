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
