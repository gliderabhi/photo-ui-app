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

    @Query("SELECT COUNT(*) FROM local_media")
    suspend fun count(): Int
}

@Dao
interface FaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(face: FaceEntity): Long

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
