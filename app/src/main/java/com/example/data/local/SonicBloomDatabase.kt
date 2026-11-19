package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FavoriteTrackEntity::class,
        PlayHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        ImportedTrackEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SonicBloomDatabase : RoomDatabase() {
    abstract fun sonicBloomDao(): SonicBloomDao

    companion object {
        @Volatile
        private var INSTANCE: SonicBloomDatabase? = null

        fun getInstance(context: Context): SonicBloomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SonicBloomDatabase::class.java,
                    "sonicbloom_library.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
