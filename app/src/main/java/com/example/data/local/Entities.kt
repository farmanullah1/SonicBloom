package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_tracks")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val trackId: String,
    val playCount: Int = 1,
    val lastPlayedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "imported_tracks")
data class ImportedTrackEntity(
    @PrimaryKey val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mimeType: String,
    val bitrateKbps: Int?,
    val sizeBytes: Long,
    val folderName: String,
    val albumArtUri: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "track_lyrics")
data class TrackLyricsEntity(
    @PrimaryKey val trackId: String,
    val rawLyrics: String,
    val sourceLabel: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "track_metadata_overrides")
data class TrackMetadataOverrideEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_eq_presets")
data class CustomEqPresetEntity(
    @PrimaryKey val name: String,
    val bandLevelsCsv: String,
    val bassBoostStrength: Int,
    val virtualizerStrength: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "waveform_peaks")
data class WaveformPeakEntity(
    @PrimaryKey val trackId: String,
    val fileSignature: String,
    val peaksCsv: String,
    val suggestedGainDb: Float,
    val updatedAt: Long = System.currentTimeMillis()
)
