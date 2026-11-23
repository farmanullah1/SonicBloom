package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.local.CustomEqPresetEntity
import com.example.data.local.FavoriteTrackEntity
import com.example.data.local.ImportedTrackEntity
import com.example.data.local.PlayHistoryEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistTrackCrossRef
import com.example.data.local.SonicBloomDao
import com.example.data.local.TrackLyricsEntity
import com.example.data.local.TrackMetadataOverrideEntity
import com.example.data.local.WaveformPeakEntity
import com.example.data.model.AudioTrack
import com.example.data.model.CustomEqPreset
import com.example.data.model.PlaylistInfo
import com.example.data.model.TrackLyrics
import com.example.data.model.WaveformData
import com.example.ui.util.ArtDiskCacheManager
import com.example.ui.util.ArtworkColorExtractor
import com.example.ui.util.LyricsParser
import com.example.ui.util.M3uPlaylistCodec
import com.example.ui.util.WaveformExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

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

data class M3uImportOutcome(
    val playlistId: Long?,
    val playlistName: String,
    val matchedCount: Int,
    val skippedCount: Int
)

class MusicLibraryRepository(
    private val context: Context,
    private val dao: SonicBloomDao
) {
    private val scannedMediaStoreTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val isScanningFlow = MutableStateFlow(false)
    private val _artCacheSizeBytes = MutableStateFlow(0L)

    val isScanning: Flow<Boolean> = isScanningFlow
    val artCacheSizeBytes: StateFlow<Long> = _artCacheSizeBytes.asStateFlow()

    init {
        refreshArtCacheSize()
    }

    private fun artCacheDir(): File = File(context.cacheDir, "album_art")

    private fun refreshArtCacheSize() {
        _artCacheSizeBytes.value = ArtDiskCacheManager.computeDirectorySizeBytes(artCacheDir())
    }

    val allTracks: Flow<List<AudioTrack>> = combine(
        scannedMediaStoreTracks,
        dao.observeImportedTracks(),
        dao.observeFavorites(),
        dao.observePlayHistory(),
        dao.observeMetadataOverrides()
    ) { scanned, imported, favorites, history, overrides ->
        val favIds = favorites.map { it.trackId }.toSet()
        val historyMap = history.associateBy { it.trackId }
        val overrideMap = overrides.associateBy { it.trackId }

        val importedTracks = imported.map { entity ->
            AudioTrack(
                id = entity.id,
                contentUri = entity.contentUri,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                albumId = -1L,
                durationMs = entity.durationMs,
                albumArtUri = entity.albumArtUri,
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
            val tagOverride = overrideMap[track.id]
            track.copy(
                title = tagOverride?.title?.takeIf { it.isNotBlank() } ?: track.title,
                artist = tagOverride?.artist?.takeIf { it.isNotBlank() } ?: track.artist,
                album = tagOverride?.album?.takeIf { it.isNotBlank() } ?: track.album,
                isFavorite = favIds.contains(track.id),
                playCount = hist?.playCount ?: 0,
                lastPlayedAt = hist?.lastPlayedAt ?: 0L,
                isMetadataOverridden = tagOverride != null
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

    val customEqPresets: Flow<List<CustomEqPreset>> = dao.observeCustomEqPresets().map { entities ->
        entities.map { entity ->
            val levels = runCatching {
                Json.decodeFromString<List<Int>>(entity.bandLevelsCsv)
            }.getOrElse {
                entity.bandLevelsCsv.split(',').mapNotNull { it.trim().toIntOrNull() }
            }
            CustomEqPreset(
                name = entity.name,
                bandLevelsMb = levels,
                bassBoostStrength = entity.bassBoostStrength,
                virtualizerStrength = entity.virtualizerStrength
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
                        albumArtUri = entity.albumArtUri,
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

        val uriString = uri.toString()
        val trackId = "imp_${uriString.hashCode().toUInt()}"
        val retriever = MediaMetadataRetriever()
        var title = ""
        var artist = "Unknown Artist"
        var album = "Imported Audio"
        var durationMs = 0L
        var bitrateKbps: Int? = null
        var embeddedArtUri: String? = null
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
            embeddedArtUri = saveEmbeddedArtBytes(trackId, retriever.embeddedPicture)
        } finally {
            runCatching { retriever.release() }
        }

        val finalTitle = title.ifBlank {
            displayName?.substringBeforeLast('.')?.ifBlank { "Imported Track" } ?: "Imported Track"
        }
        ImportedTrackEntity(
            id = trackId,
            contentUri = uriString,
            title = finalTitle,
            artist = artist,
            album = album,
            durationMs = durationMs,
            mimeType = mimeType,
            bitrateKbps = bitrateKbps,
            sizeBytes = sizeBytes,
            folderName = "Imported Files",
            albumArtUri = embeddedArtUri
        )
    }

    private fun saveEmbeddedArtBytes(trackId: String, bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            val dir = artCacheDir().apply { mkdirs() }
            val safeName = trackId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(dir, "$safeName.jpg")
            file.writeBytes(bytes)
            file.setLastModified(System.currentTimeMillis())
            ArtDiskCacheManager.enforceMaxCacheBytes(dir)
            refreshArtCacheSize()
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    suspend fun resolveEmbeddedArtworkIfMissing(track: AudioTrack): String? = withContext(Dispatchers.IO) {
        val dir = artCacheDir()
        val safeName = track.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val cachedFile = File(dir, "$safeName.jpg")
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            cachedFile.setLastModified(System.currentTimeMillis())
            return@withContext Uri.fromFile(cachedFile).toString()
        }
        val retriever = MediaMetadataRetriever()
        return@withContext try {
            retriever.setDataSource(context, Uri.parse(track.contentUri))
            saveEmbeddedArtBytes(track.id, retriever.embeddedPicture)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun clearArtworkCache(): Long = withContext(Dispatchers.IO) {
        val removed = ArtDiskCacheManager.clearDirectory(artCacheDir())
        ArtworkColorExtractor.clearMemoryCache()
        refreshArtCacheSize()
        removed
    }

    suspend fun getOrExtractWaveform(track: AudioTrack): WaveformData = withContext(Dispatchers.IO) {
        val signature = "${track.sizeBytes}_${track.durationMs}"
        val cached = dao.getWaveformPeak(track.id)
        if (cached != null && cached.fileSignature == signature) {
            val decoded = WaveformExtractor.decodePeaksCsv(cached.peaksCsv)
            if (decoded.isNotEmpty()) {
                return@withContext WaveformData(
                    trackId = track.id,
                    amplitudes = decoded,
                    isRealPcm = true,
                    suggestedGainDb = cached.suggestedGainDb
                )
            }
        }

        val extracted = WaveformExtractor.extractFromAudioUri(
            context = context,
            uri = Uri.parse(track.contentUri),
            durationMs = track.durationMs
        )
        if (extracted != null && extracted.amplitudes.isNotEmpty()) {
            dao.upsertWaveformPeak(
                WaveformPeakEntity(
                    trackId = track.id,
                    fileSignature = signature,
                    peaksCsv = WaveformExtractor.encodePeaksCsv(extracted.amplitudes),
                    suggestedGainDb = extracted.suggestedGainDb
                )
            )
            WaveformData(
                trackId = track.id,
                amplitudes = extracted.amplitudes,
                isRealPcm = true,
                suggestedGainDb = extracted.suggestedGainDb
            )
        } else {
            WaveformData(
                trackId = track.id,
                amplitudes = WaveformExtractor.fallbackAmplitudes(track.id),
                isRealPcm = false,
                suggestedGainDb = 0f
            )
        }
    }

    suspend fun getTrackNormalizationGainDb(trackId: String): Float = withContext(Dispatchers.IO) {
        dao.getWaveformPeak(trackId)?.suggestedGainDb ?: 0f
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

    suspend fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<String>) {
        orderedTrackIds.forEachIndexed { index, trackId ->
            dao.updatePlaylistTrackPosition(playlistId, trackId, index)
        }
    }

    suspend fun exportPlaylistToM3u(
        targetUri: Uri,
        playlistName: String,
        tracks: List<AudioTrack>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val content = M3uPlaylistCodec.encode(playlistName, tracks)
            context.contentResolver.openOutputStream(targetUri)?.bufferedWriter()?.use { writer ->
                writer.write(content)
            } ?: error("Unable to open destination file for export.")
            tracks.size
        }
    }

    suspend fun importPlaylistFromM3u(
        sourceUri: Uri,
        fallbackName: String
    ): Result<M3uImportOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
                ?.takeIf { it.isNotBlank() }
                ?: error("Selected M3U playlist is empty or unreadable.")
            val document = M3uPlaylistCodec.parse(raw)
            val currentLibrary = allTracks.first()
            val match = M3uPlaylistCodec.matchDocument(document, currentLibrary)
            if (match.matchedTrackIds.isEmpty()) {
                return@runCatching M3uImportOutcome(
                    playlistId = null,
                    playlistName = document.playlistName ?: fallbackName,
                    matchedCount = 0,
                    skippedCount = match.skippedCount
                )
            }

            val baseName = (document.playlistName ?: fallbackName).trim().ifBlank { "Imported M3U" }
            val existingNames = playlists.first().map { it.name.trim().lowercase() }.toSet()
            var candidateName = baseName
            var suffix = 2
            while (existingNames.contains(candidateName.lowercase())) {
                candidateName = "$baseName ($suffix)"
                suffix++
            }
            val newId = createPlaylist(candidateName)
            addTracksToPlaylist(newId, match.matchedTrackIds)
            M3uImportOutcome(
                playlistId = newId,
                playlistName = candidateName,
                matchedCount = match.matchedTrackIds.size,
                skippedCount = match.skippedCount
            )
        }
    }

    suspend fun saveTrackMetadataOverride(
        trackId: String,
        title: String,
        artist: String,
        album: String
    ) {
        dao.upsertMetadataOverride(
            TrackMetadataOverrideEntity(
                trackId = trackId,
                title = title.trim(),
                artist = artist.trim(),
                album = album.trim()
            )
        )
    }

    suspend fun clearTrackMetadataOverride(trackId: String) {
        dao.deleteMetadataOverride(trackId)
    }

    suspend fun saveCustomEqPreset(
        name: String,
        bandLevelsMb: List<Int>,
        bassBoostStrength: Int,
        virtualizerStrength: Int
    ) {
        dao.upsertCustomEqPreset(
            CustomEqPresetEntity(
                name = name.trim(),
                bandLevelsCsv = Json.encodeToString(bandLevelsMb),
                bassBoostStrength = bassBoostStrength.coerceIn(0, 1000),
                virtualizerStrength = virtualizerStrength.coerceIn(0, 1000)
            )
        )
    }

    suspend fun deleteCustomEqPreset(name: String) {
        dao.deleteCustomEqPreset(name)
    }

    fun observeTrackLyrics(trackId: String): Flow<TrackLyrics?> {
        return dao.observeLyricsForTrack(trackId).map { entity ->
            entity?.let {
                LyricsParser.parse(
                    rawText = it.rawLyrics,
                    trackId = it.trackId,
                    sourceLabel = it.sourceLabel
                )
            }
        }
    }

    suspend fun importLyricsFile(trackId: String, uri: Uri): Result<TrackLyrics> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?.takeIf { it.isNotBlank() }
                ?: error("Selected lyrics file is empty or unreadable.")
            val parsed = LyricsParser.parse(raw, trackId, "Imported .lrc")
            dao.upsertLyrics(
                TrackLyricsEntity(
                    trackId = trackId,
                    rawLyrics = raw,
                    sourceLabel = if (parsed.isSynced) "Synced .lrc" else "Text File"
                )
            )
            parsed
        }
    }

    suspend fun loadEmbeddedLyricsIfAvailable(track: AudioTrack): TrackLyrics? = withContext(Dispatchers.IO) {
        val raw = LyricsParser.extractEmbeddedId3Lyrics(context, Uri.parse(track.contentUri))
            ?: return@withContext null
        LyricsParser.parse(raw, track.id, "Embedded ID3")
    }

    suspend fun clearTrackLyrics(trackId: String) {
        dao.deleteLyrics(trackId)
    }
}
