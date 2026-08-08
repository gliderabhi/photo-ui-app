package com.sevis.photos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMediaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(media: List<LocalMediaEntity>): List<Long>

    @Query("SELECT * FROM local_media ORDER BY dateTakenMillis DESC")
    fun observeAll(): Flow<List<LocalMediaEntity>>

    @Query("SELECT mediaStoreId FROM local_media")
    suspend fun allMediaStoreIds(): List<Long>

    @Query("SELECT COUNT(*) FROM local_media")
    suspend fun count(): Int

    @Query("SELECT * FROM local_media WHERE id IN (:ids) ORDER BY dateTakenMillis DESC")
    suspend fun byIds(ids: List<Long>): List<LocalMediaEntity>

    @Query("SELECT * FROM local_media WHERE placeResolved = 0")
    suspend fun unresolvedForPlace(): List<LocalMediaEntity>

    @Query("UPDATE local_media SET placeResolved = 1, placeName = :placeName WHERE id IN (:ids)")
    suspend fun markPlaceResolved(ids: List<Long>, placeName: String?)
}

// FaceDao/PersonDao were removed along with on-device face detection — see
// Entities.kt and LocalScanWorker for where that pipeline moved.
