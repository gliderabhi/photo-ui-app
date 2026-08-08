package com.sevis.photos.data.local

import androidx.room.Entity
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
    /** Reverse-geocoded label (e.g. "Varanasi"), resolved lazily by LocalScanWorker. */
    val placeName: String? = null,
    val placeResolved: Boolean = false,
    /** MediaStore folder name (e.g. "Camera", "WhatsApp Images"), used for Albums grouping. */
    val bucketName: String? = null
)

// Face/Person local tables were removed along with on-device face detection
// (ML Kit, then SFace/ONNX Runtime) — that pipeline now runs server-side in
// face-service, with results fetched via GET /api/photos/people and
// GET /api/photos/{id}/faces. See LocalScanWorker and [[project-photos-mobile]].
