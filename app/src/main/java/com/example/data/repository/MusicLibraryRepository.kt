package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.local.FavoriteTrackEntity
import com.example.data.local.ImportedTrackEntity
import com.example.data.local.PlayHistoryEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.SonicBloomDao
import com.example.data.model.AudioTrack
import com.example.data.model.PlaylistInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

sealed interface LibraryScanResult {
    data class Success(val count: Int) : LibraryScanResult
    data object PermissionDenied : LibraryScanResult
    data class Failure(val message: String) : LibraryScanResult
}

data class ImportAudioResult(
    val importedTracks: List<AudioTrack>,
    val failedCount: Int,
    val errorMessage: String? = null
)

class MusicLibraryRepository(
    private val context: Context,
    private val dao: SonicBloomDao
) {
    private val scannedMediaStoreTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val isScanningFlow = MutableStateFlow(false)

    val isScanning: Flow<Boolean> = isScanningFlow

    val allTracks: Flow<List<AudioTrack>> = combine(
        scannedMediaStoreTracks,
        dao.observeImportedTracks(),
        dao.observeFavorites(),
        dao.observePlayHistory()
    ) { scanned, imported, favorites, history ->
        val favIds = favorites.map { it.trackId }.toSet()
        val historyMap = history.associateBy { it.trackId }

        val importedTracks = imported.map { entity ->
            AudioTrack(
                id = entity.id,
                contentUri = entity.contentUri,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                albumId = -1L,
                durationMs = entity.durationMs,
                albumArtUri = null,
                folderName = entity.folderName,
                mimeType = entity.mimeType,
                bitrateKbps = entity.bitrateKbps,
                sizeBytes = entity.sizeBytes,
                dateAddedSec = entity.addedAt / 1000L,
                isImported = true
            )
        }

        val merged = (scanned + importedTracks).distinctBy { it.contentUri }
        merged.map { track ->
            val hist = historyMap[track.id]
            track.copy(
                isFavorite = favIds.contains(track.id),
                playCount = hist?.playCount ?: 0,
                lastPlayedAt = hist?.lastPlayedAt ?: 0L
            )
        }
    }

    val playlists: Flow<List<PlaylistInfo>> = combine(
        dao.observePlaylists(),
        dao.observeAllPlaylistTracks(),
        allTracks
    ) { entities, crossRefs, tracks ->
        val tracksById = tracks.associateBy { it.id }
        val refsByPlaylist = crossRefs.groupBy { it.playlistId }
        entities.map { entity ->
            val refs = refsByPlaylist[entity.id].orEmpty()
            val validTrackIds = refs.mapNotNull { ref ->
                if (tracksById.containsKey(ref.trackId)) ref.trackId else null
            }
            val cover = validTrackIds.firstOrNull()?.let { tracksById[it]?.albumArtUri }
            PlaylistInfo(
                id = entity.id,
                name = entity.name,
                trackIds = validTrackIds,
                coverArtUri = cover
            )
        }
    }

    suspend fun scanDeviceMediaStore(): LibraryScanResult {
        isScanningFlow.value = true
        return try {
            val tracks = withContext(Dispatchers.IO) {
                queryMediaStoreAudio()
            }
            scannedMediaStoreTracks.value = tracks
            LibraryScanResult.Success(tracks.size)
        } catch (_: SecurityException) {
            LibraryScanResult.PermissionDenied
        } catch (e: Exception) {
            LibraryScanResult.Failure(
                e.localizedMessage ?: "Unable to scan audio storage."
            )
        } finally {
            isScanningFlow.value = false
        }
    }

    private fun queryMediaStoreAudio(): List<AudioTrack> {
        val result = mutableListOf<AudioTrack>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Audio.Media.DATA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Audio.Media.BITRATE)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%'"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val displayNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            }
            val bitrateCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndex(MediaStore.Audio.Media.BITRATE)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val durationMs = cursor.getLong(durationCol)
                if (durationMs < 1000L) continue

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                val albumId = cursor.getLong(albumIdCol)
                val albumArtUri = if (albumId > 0) {
                    ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()
                } else {
                    null
                }

                val displayName = cursor.getString(displayNameCol).orEmpty()
                val rawTitle = cursor.getString(titleCol).orEmpty()
                val title = rawTitle.ifBlank {
                    displayName.substringBeforeLast('.').ifBlank { "Untitled Track" }
                }
                val rawArtist = cursor.getString(artistCol).orEmpty()
                val artist = if (rawArtist.isBlank() || rawArtist == "<unknown>") "Unknown Artist" else rawArtist
                val rawAlbum = cursor.getString(albumCol).orEmpty()
                val album = if (rawAlbum.isBlank() || rawAlbum == "<unknown>") "Single / Unknown Album" else rawAlbum

                val rawPath = if (pathCol >= 0) cursor.getString(pathCol).orEmpty() else ""
                val folderName = extractFolderName(rawPath)

                val bitrateBps = if (bitrateCol >= 0 && !cursor.isNull(bitrateCol)) {
                    cursor.getInt(bitrateCol)
                } else {
                    0
                }
                val bitrateKbps = if (bitrateBps > 0) bitrateBps / 1000 else null

                result.add(
                    AudioTrack(
                        id = "ms_$id",
                        contentUri = contentUri.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        durationMs = durationMs,
                        albumArtUri = albumArtUri,
                        folderName = folderName,
                        mimeType = cursor.getString(mimeCol) ?: "audio/mpeg",
                        bitrateKbps = bitrateKbps,
                        sizeBytes = cursor.getLong(sizeCol),
                        dateAddedSec = cursor.getLong(dateCol),
                        isImported = false
                    )
                )
            }
        }
        return result
    }

    private fun extractFolderName(rawPath: String): String {
        val cleaned = rawPath.trim('/')
        if (cleaned.isBlank()) return "Music"
        val segments = cleaned.split('/').filter { it.isNotBlank() }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            segments.lastOrNull() ?: "Music"
        } else {
            if (segments.size >= 2) segments[segments.size - 2] else "Music"
        }
    }

    suspend fun importAudioUris(uris: List<Uri>): ImportAudioResult = withContext(Dispatchers.IO) {
        val imported = mutableListOf<AudioTrack>()
        var failedCount = 0
        var lastError: String? = null

        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val result = extractImportedEntity(uri)
            result.onSuccess { entity ->
                dao.insertImportedTrack(entity)
                imported.add(
                    AudioTrack(
                        id = entity.id,
                        contentUri = entity.contentUri,
                        title = entity.title,
                        artist = entity.artist,
                        album = entity.album,
                        albumId = -1L,
                        durationMs = entity.durationMs,
                        albumArtUri = null,
                        folderName = entity.folderName,
                        mimeType = entity.mimeType,
                        bitrateKbps = entity.bitrateKbps,
                        sizeBytes = entity.sizeBytes,
                        dateAddedSec = entity.addedAt / 1000L,
                        isImported = true
                    )
                )
            }.onFailure { err ->
                failedCount++
                lastError = err.localizedMessage ?: "Unsupported or unreadable audio file."
            }
        }
        ImportAudioResult(
            importedTracks = imported,
            failedCount = failedCount,
            errorMessage = lastError
        )
    }

    private fun extractImportedEntity(uri: Uri): Result<ImportedTrackEntity> = runCatching {
        var displayName: String? = null
        var sizeBytes = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
            }
        }

        val retriever = MediaMetadataRetriever()
        var title = ""
        var artist = "Unknown Artist"
        var album = "Imported Audio"
        var durationMs = 0L
        var bitrateKbps: Int? = null
        val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"

        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty()
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "Imported Audio"
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
            bitrateKbps = if (bitrateBps != null && bitrateBps > 0) bitrateBps / 1000 else null
        } finally {
            runCatching { retriever.release() }
        }

        val finalTitle = title.ifBlank {
            displayName?.substringBeforeLast('.')?.ifBlank { "Imported Track" } ?: "Imported Track"
        }
        val uriString = uri.toString()
        ImportedTrackEntity(
            id = "imp_${uriString.hashCode().toUInt()}",
            contentUri = uriString,
            title = finalTitle,
            artist = artist,
            album = album,
            durationMs = durationMs,
            mimeType = mimeType,
            bitrateKbps = bitrateKbps,
            sizeBytes = sizeBytes,
            folderName = "Imported Files"
        )
    }

    suspend fun toggleFavorite(trackId: String, currentlyFavorite: Boolean) {
        if (currentlyFavorite) {
            dao.deleteFavorite(trackId)
        } else {
            dao.insertFavorite(FavoriteTrackEntity(trackId = trackId))
        }
    }

    suspend fun recordPlayback(trackId: String) {
        val existing = dao.getPlayHistoryForTrack(trackId)
        val updated = PlayHistoryEntity(
            trackId = trackId,
            playCount = (existing?.playCount ?: 0) + 1,
            lastPlayedAt = System.currentTimeMillis()
        )
        dao.upsertPlayHistory(updated)
    }

    suspend fun createPlaylist(name: String): Long {
        return dao.insertPlaylist(PlaylistEntity(name = name.trim()))
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        dao.renamePlaylist(playlistId, newName.trim())
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        val nextPos = dao.getMaxPlaylistPosition(playlistId) + 1
        dao.insertPlaylistTrack(
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = trackId,
                position = nextPos
            )
        )
    }

    suspend fun addTracksToPlaylist(playlistId: Long, trackIds: List<String>) {
        var pos = dao.getMaxPlaylistPosition(playlistId) + 1
        for (trackId in trackIds) {
            dao.insertPlaylistTrack(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = pos++
                )
            )
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }
}
