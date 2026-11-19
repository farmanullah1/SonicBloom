package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SonicBloomDao {
    @Query("SELECT * FROM favorite_tracks ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<FavoriteTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteTrackEntity)

    @Query("DELETE FROM favorite_tracks WHERE trackId = :trackId")
    suspend fun deleteFavorite(trackId: String)

    @Query("SELECT * FROM play_history ORDER BY lastPlayedAt DESC")
    fun observePlayHistory(): Flow<List<PlayHistoryEntity>>

    @Query("SELECT * FROM play_history WHERE trackId = :trackId LIMIT 1")
    suspend fun getPlayHistoryForTrack(trackId: String): PlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayHistory(entity: PlayHistoryEntity)

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT * FROM playlist_tracks ORDER BY position ASC, addedAt ASC")
    fun observeAllPlaylistTracks(): Flow<List<PlaylistTrackCrossRef>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getMaxPlaylistPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrack(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    @Query("SELECT * FROM imported_tracks ORDER BY addedAt DESC")
    fun observeImportedTracks(): Flow<List<ImportedTrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedTrack(entity: ImportedTrackEntity)

    @Query("DELETE FROM imported_tracks WHERE id = :trackId")
    suspend fun deleteImportedTrack(trackId: String)
}
