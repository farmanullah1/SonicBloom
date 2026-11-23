package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteTrackEntity::class,
        PlayHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        ImportedTrackEntity::class,
        TrackLyricsEntity::class,
        TrackMetadataOverrideEntity::class,
        CustomEqPresetEntity::class,
        WaveformPeakEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SonicBloomDatabase : RoomDatabase() {
    abstract fun sonicBloomDao(): SonicBloomDao

    companion object {
        @Volatile
        private var INSTANCE: SonicBloomDatabase? = null

        internal val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE imported_tracks ADD COLUMN albumArtUri TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_lyrics` (
                        `trackId` TEXT NOT NULL,
                        `rawLyrics` TEXT NOT NULL,
                        `sourceLabel` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent()
                )
            }
        }

        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `track_metadata_overrides` (
                        `trackId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_eq_presets` (
                        `name` TEXT NOT NULL,
                        `bandLevelsCsv` TEXT NOT NULL,
                        `bassBoostStrength` INTEGER NOT NULL,
                        `virtualizerStrength` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`name`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `waveform_peaks` (
                        `trackId` TEXT NOT NULL,
                        `fileSignature` TEXT NOT NULL,
                        `peaksCsv` TEXT NOT NULL,
                        `suggestedGainDb` REAL NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`trackId`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): SonicBloomDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SonicBloomDatabase::class.java,
                    "sonicbloom_library.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
