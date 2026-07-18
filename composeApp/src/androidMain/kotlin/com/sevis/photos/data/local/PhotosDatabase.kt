package com.sevis.photos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LocalMediaEntity::class, FaceEntity::class, PersonEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PhotosDatabase : RoomDatabase() {
    abstract fun localMediaDao(): LocalMediaDao
    abstract fun faceDao(): FaceDao
    abstract fun personDao(): PersonDao

    companion object {
        @Volatile private var instance: PhotosDatabase? = null

        fun get(context: Context): PhotosDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photos_local.db"
                ).build().also { instance = it }
            }
    }
}
