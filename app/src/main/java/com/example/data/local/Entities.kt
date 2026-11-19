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
    val addedAt: Long = System.currentTimeMillis()
)
