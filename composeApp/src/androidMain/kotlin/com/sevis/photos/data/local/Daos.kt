package com.sevis.photos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMediaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(media: List<LocalMediaEntity>): List<Long>

    @Query("SELECT * FROM local_media ORDER BY dateTakenMillis DESC")
    fun observeAll(): Flow<List<LocalMediaEntity>>

    @Query("SELECT mediaStoreId FROM local_media")
    suspend fun allMediaStoreIds(): List<Long>

    @Query("SELECT * FROM local_media WHERE faceScanned = 0")
    suspend fun unscannedForFaces(): List<LocalMediaEntity>

    @Query("UPDATE local_media SET faceScanned = 1 WHERE id = :mediaId")
    suspend fun markFaceScanned(mediaId: Long)

    // Batched version used by the scan worker: writing faceScanned one row at a
    // time (thousands of individual UPDATEs for a large library) fires Room's
    // InvalidationTracker on every single write, which re-runs observeAll()'s
    // full-table query and re-emits to every collector each time — that's what
    // made the Library grid feel slow/janky while scanning. One IN(...) update
    // per batch cuts that down by the batch size.
    @Query("UPDATE local_media SET faceScanned = 1 WHERE id IN (:mediaIds)")
    suspend fun markFacesScanned(mediaIds: List<Long>)

    @Query("SELECT COUNT(*) FROM local_media")
    suspend fun count(): Int

    @Query("SELECT * FROM local_media WHERE id IN (:ids) ORDER BY dateTakenMillis DESC")
    suspend fun byIds(ids: List<Long>): List<LocalMediaEntity>

    @Query("SELECT * FROM local_media WHERE placeResolved = 0")
    suspend fun unresolvedForPlace(): List<LocalMediaEntity>

    @Query("UPDATE local_media SET placeResolved = 1, placeName = :placeName WHERE id IN (:ids)")
    suspend fun markPlaceResolved(ids: List<Long>, placeName: String?)
}

@Dao
interface FaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(face: FaceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(faces: List<FaceEntity>): List<Long>

    @Update
    suspend fun update(face: FaceEntity)

    @Query("SELECT * FROM face WHERE mediaId = :mediaId")
    suspend fun forMedia(mediaId: Long): List<FaceEntity>

    @Query("SELECT * FROM face WHERE personId IS NULL")
    suspend fun unclustered(): List<FaceEntity>

    @Query("SELECT * FROM face WHERE personId = :personId")
    fun observeForPerson(personId: Long): Flow<List<FaceEntity>>

    @Query("SELECT COUNT(*) FROM face WHERE mediaId = :mediaId")
    fun observeCountForMedia(mediaId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM face")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM face ORDER BY id DESC")
    fun observeAllFaces(): Flow<List<FaceEntity>>

    /**
     * Every clustered face's embedding — used to match a new face against ALL of a
     * person's stored faces (not just one fixed anchor), so a bad-angle/lighting
     * anchor photo doesn't cause otherwise-good matches to fall through.
     */
    @Query("SELECT * FROM face WHERE personId IS NOT NULL AND embedding IS NOT NULL")
    suspend fun allClusteredFaceEmbeddings(): List<FaceEntity>

    @Query("SELECT DISTINCT mediaId FROM face WHERE personId = :personId")
    suspend fun mediaIdsForPerson(personId: Long): List<Long>
}

@Dao
interface PersonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM person ORDER BY id")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun byId(id: Long): PersonEntity?
}
