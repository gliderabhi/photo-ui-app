package com.sevis.photos.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per on-device photo discovered via MediaStore. */
@Entity(
    tableName = "local_media",
    indices = [Index(value = ["mediaStoreId"], unique = true)]
)
data class LocalMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val dateTakenMillis: Long,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val faceScanned: Boolean = false,
    /** Reverse-geocoded label (e.g. "Varanasi"), resolved lazily by LocalScanWorker. */
    val placeName: String? = null,
    val placeResolved: Boolean = false,
    /** MediaStore folder name (e.g. "Camera", "WhatsApp Images"), used for Albums grouping. */
    val bucketName: String? = null
)

@Entity(
    tableName = "person",
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String? = null,
    val coverFacePath: String? = null
)

/** One row per face ML Kit detected within a [LocalMediaEntity] photo. */
@Entity(
    tableName = "face",
    foreignKeys = [
        ForeignKey(
            entity = LocalMediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("mediaId"), Index("personId")]
)
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaId: Long,
    val personId: Long? = null,
    val boundsLeft: Int,
    val boundsTop: Int,
    val boundsRight: Int,
    val boundsBottom: Int,
    val thumbnailPath: String,
    /** MobileFaceNet embedding, flattened to a comma-separated string (Room has no native FloatArray column type). */
    val embedding: String? = null
)
