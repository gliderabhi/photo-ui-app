package com.sevis.photos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [LocalMediaEntity::class],
    version = 5,
    exportSchema = false
)
abstract class PhotosDatabase : RoomDatabase() {
    abstract fun localMediaDao(): LocalMediaDao

    companion object {
        @Volatile private var instance: PhotosDatabase? = null

        fun get(context: Context): PhotosDatabase =
            instance ?: synchronized(this) {
                // Everything in this DB is a re-derivable cache (rescanned from
                // MediaStore, re-resolved) — never irreplaceable user data — so a
                // destructive migration on schema change is an acceptable,
                // low-maintenance tradeoff over writing Migrations. v5 drops the
                // face/person tables (face detection moved server-side, see
                // LocalScanWorker).
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PhotosDatabase::class.java,
                    "photos_local.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
