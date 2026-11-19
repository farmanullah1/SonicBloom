package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.PreferencesRepository
import com.example.data.model.AlbumSummary
import com.example.data.model.ArtistSummary
import com.example.data.model.AudioTrack
import com.example.data.model.BrowseTab
import com.example.data.model.EqualizerState
import com.example.data.model.FolderSummary
import com.example.data.model.PlaylistInfo
import com.example.data.model.SmartPlaylistType
import com.example.data.model.ThemeMode
import com.example.data.model.TrackFilterOption
import com.example.data.model.TrackSortOption
import com.example.data.repository.LibraryScanResult
import com.example.data.repository.MusicLibraryRepository
import com.example.playback.AudioEffectManager
import com.example.playback.PlaybackController
import com.example.playback.PlaybackUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiEvent {
    data class ShowMessage(
        val message: String,
        val actionLabel: String? = null,
        val isError: Boolean = false
    ) : UiEvent
}

data class LibraryUiState(
    val allTracks: List<AudioTrack> = emptyList(),
    val filteredTracks: List<AudioTrack> = emptyList(),
    val recentlyPlayed: List<AudioTrack> = emptyList(),
    val favoriteTracks: List<AudioTrack> = emptyList(),
    val mostPlayedTracks: List<AudioTrack> = emptyList(),
    val recentlyAddedTracks: List<AudioTrack> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val folders: List<FolderSummary> = emptyList(),
    val playlists: List<PlaylistInfo> = emptyList(),
    val searchQuery: String = "",
    val browseSearchQuery: String = "",
    val filterOption: TrackFilterOption = TrackFilterOption.ALL,
    val sortOption: TrackSortOption = TrackSortOption.TITLE,
    val sortAscending: Boolean = true,
    val browseTab: BrowseTab = BrowseTab.ALBUMS,
    val isScanning: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val libraryError: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val totalDurationMs: Long = 0L,
    val totalSizeBytes: Long = 0L
) {
    val hasActiveTrackFilters: Boolean
        get() = searchQuery.isNotBlank() || filterOption != TrackFilterOption.ALL
}

class SonicBloomViewModel(
    private val libraryRepository: MusicLibraryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val playbackController: PlaybackController,
    private val audioEffectManager: AudioEffectManager
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val browseSearchQueryFlow = MutableStateFlow("")
    private val filterOptionFlow = MutableStateFlow(TrackFilterOption.ALL)
    private val sortOptionFlow = MutableStateFlow(TrackSortOption.TITLE)
    private val sortAscendingFlow = MutableStateFlow(true)
    private val browseTabFlow = MutableStateFlow(BrowseTab.ALBUMS)
    private val hasPermissionFlow = MutableStateFlow(false)
    private val libraryErrorFlow = MutableStateFlow<String?>(null)
    private val themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val playbackState: StateFlow<PlaybackUiState> = playbackController.uiState
    val equalizerState: StateFlow<EqualizerState> = audioEffectManager.state
    val equalizerPresets: List<String> = audioEffectManager.presetNames

    val libraryState: StateFlow<LibraryUiState> = combine(
        libraryRepository.allTracks,
        libraryRepository.playlists,
        libraryRepository.isScanning,
        searchQueryFlow,
        browseSearchQueryFlow,
        filterOptionFlow,
        sortOptionFlow,
        sortAscendingFlow,
        browseTabFlow,
        hasPermissionFlow,
        libraryErrorFlow,
        themeModeFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tracks = values[0] as List<AudioTrack>
        @Suppress("UNCHECKED_CAST")
        val playlists = values[1] as List<PlaylistInfo>
        val isScanning = values[2] as Boolean
        val query = values[3] as String
        val browseQuery = values[4] as String
        val filterOption = values[5] as TrackFilterOption
        val sortOption = values[6] as TrackSortOption
        val sortAscending = values[7] as Boolean
        val browseTab = values[8] as BrowseTab
        val hasPerm = values[9] as Boolean
        val libError = values[10] as String?
        val themeMode = values[11] as ThemeMode

        val filteredByType = when (filterOption) {
            TrackFilterOption.ALL -> tracks
            TrackFilterOption.FAVORITES -> tracks.filter { it.isFavorite }
            TrackFilterOption.MP3 -> tracks.filter { it.isMp3 }
            TrackFilterOption.LOSSLESS -> tracks.filter { it.isLossless }
            TrackFilterOption.IMPORTED -> tracks.filter { it.isImported }
        }

        val trimmed = query.trim().lowercase()
        val matched = if (trimmed.isEmpty()) {
            filteredByType
        } else {
            filteredByType.filter { track ->
                track.title.lowercase().contains(trimmed) ||
                    track.artist.lowercase().contains(trimmed) ||
                    track.album.lowercase().contains(trimmed) ||
                    track.folderName.lowercase().contains(trimmed)
            }
        }

        val sortedTracks = when (sortOption) {
            TrackSortOption.TITLE -> if (sortAscending) {
                matched.sortedBy { it.title.lowercase() }
            } else {
                matched.sortedByDescending { it.title.lowercase() }
            }
            TrackSortOption.ARTIST -> if (sortAscending) {
                matched.sortedBy { it.artist.lowercase() }
            } else {
                matched.sortedByDescending { it.artist.lowercase() }
            }
            TrackSortOption.DURATION -> if (sortAscending) {
                matched.sortedBy { it.durationMs }
            } else {
                matched.sortedByDescending { it.durationMs }
            }
            TrackSortOption.RECENT -> if (sortAscending) {
                matched.sortedBy { it.dateAddedSec }
            } else {
                matched.sortedByDescending { it.dateAddedSec }
            }
            TrackSortOption.PLAYS -> if (sortAscending) {
                matched.sortedBy { it.playCount }
            } else {
                matched.sortedByDescending { it.playCount }
            }
        }

        val recentlyPlayed = tracks
            .filter { it.lastPlayedAt > 0L }
            .sortedByDescending { it.lastPlayedAt }
            .take(15)

        val favorites = tracks.filter { it.isFavorite }
        val mostPlayed = tracks.filter { it.playCount > 0 }.sortedByDescending { it.playCount }.take(25)
        val recentlyAdded = tracks.sortedByDescending { it.dateAddedSec }.take(25)

        val bq = browseQuery.trim().lowercase()
        val albums = tracks
            .groupBy { "${it.album}||${it.artist}" }
            .map { (key, list) ->
                val first = list.first()
                AlbumSummary(
                    key = key,
                    title = first.album,
                    artist = first.artist,
                    albumArtUri = list.firstNotNullOfOrNull { it.albumArtUri },
                    trackCount = list.size,
                    totalDurationMs = list.sumOf { it.durationMs }
                )
            }
            .filter { bq.isEmpty() || it.title.lowercase().contains(bq) || it.artist.lowercase().contains(bq) }
            .sortedBy { it.title.lowercase() }

        val artists = tracks
            .groupBy { it.artist }
            .map { (artistName, list) ->
                ArtistSummary(
                    name = artistName,
                    trackCount = list.size,
                    albumCount = list.map { it.album }.distinct().size,
                    coverArtUri = list.firstNotNullOfOrNull { it.albumArtUri }
                )
            }
            .filter { bq.isEmpty() || it.name.lowercase().contains(bq) }
            .sortedBy { it.name.lowercase() }

        val folders = tracks
            .groupBy { it.folderName }
            .map { (folderName, list) ->
                FolderSummary(
                    name = folderName,
                    trackCount = list.size,
                    totalDurationMs = list.sumOf { it.durationMs }
                )
            }
            .filter { bq.isEmpty() || it.name.lowercase().contains(bq) }
            .sortedBy { it.name.lowercase() }

        LibraryUiState(
            allTracks = tracks,
            filteredTracks = sortedTracks,
            recentlyPlayed = recentlyPlayed,
            favoriteTracks = favorites,
            mostPlayedTracks = mostPlayed,
            recentlyAddedTracks = recentlyAdded,
            albums = albums,
            artists = artists,
            folders = folders,
            playlists = playlists,
            searchQuery = query,
            browseSearchQuery = browseQuery,
            filterOption = filterOption,
            sortOption = sortOption,
            sortAscending = sortAscending,
            browseTab = browseTab,
            isScanning = isScanning,
            hasAudioPermission = hasPerm,
            libraryError = libError,
            themeMode = themeMode,
            totalDurationMs = tracks.sumOf { it.durationMs },
            totalSizeBytes = tracks.sumOf { it.sizeBytes }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = LibraryUiState()
    )

    init {
        playbackController.connect()
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            themeModeFlow.value = prefs.themeMode
            sortOptionFlow.value = prefs.sortOption
            sortAscendingFlow.value = prefs.sortAscending
            audioEffectManager.setEnabled(prefs.eqEnabled)
            audioEffectManager.setBassBoost(prefs.bassBoostStrength)
            audioEffectManager.applyPreset(prefs.eqPresetName)
        }
    }

    fun onPermissionUpdated(granted: Boolean, userInitiated: Boolean = false) {
        hasPermissionFlow.value = granted
        if (granted) {
            libraryErrorFlow.value = null
            rescanLibrary(showFeedback = userInitiated)
        } else if (userInitiated) {
            libraryErrorFlow.value = "Audio storage permission is needed to scan local MP3s, or use Import MP3s."
            _events.tryEmit(
                UiEvent.ShowMessage(
                    message = "Audio permission denied. You can still import MP3 files manually.",
                    isError = true
                )
            )
        }
    }

    fun rescanLibrary(showFeedback: Boolean = true) {
        viewModelScope.launch {
            when (val result = libraryRepository.scanDeviceMediaStore()) {
                is LibraryScanResult.Success -> {
                    libraryErrorFlow.value = null
                    if (showFeedback) {
                        _events.emit(
                            UiEvent.ShowMessage("Indexed ${result.count} audio tracks on device.")
                        )
                    }
                }
                LibraryScanResult.PermissionDenied -> {
                    hasPermissionFlow.value = false
                    libraryErrorFlow.value = "Audio permission required to scan device folders."
                    if (showFeedback) {
                        _events.emit(
                            UiEvent.ShowMessage(
                                message = "Grant audio permission to scan device music.",
                                isError = true
                            )
                        )
                    }
                }
                is LibraryScanResult.Failure -> {
                    libraryErrorFlow.value = result.message
                    _events.emit(
                        UiEvent.ShowMessage(
                            message = result.message,
                            isError = true
                        )
                    )
                }
            }
        }
    }

    fun dismissLibraryError() {
        libraryErrorFlow.value = null
    }

    fun importAudioFiles(uris: List<Uri>, playImmediately: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val result = libraryRepository.importAudioUris(uris)
            if (result.importedTracks.isNotEmpty()) {
                libraryErrorFlow.value = null
                if (playImmediately) {
                    playbackController.playTracks(result.importedTracks, 0)
                }
                val suffix = if (result.failedCount > 0) {
                    " (${result.failedCount} unreadable skipped)"
                } else ""
                _events.emit(
                    UiEvent.ShowMessage(
                        message = "Imported ${result.importedTracks.size} audio file(s)$suffix."
                    )
                )
            } else {
                val msg = result.errorMessage ?: "Selected files could not be read as audio."
                libraryErrorFlow.value = msg
                _events.emit(UiEvent.ShowMessage(message = msg, isError = true))
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        themeModeFlow.value = mode
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun cycleThemeMode() {
        val next = when (themeModeFlow.value) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        setThemeMode(next)
        val label = when (next) {
            ThemeMode.SYSTEM -> "Theme: System Auto"
            ThemeMode.LIGHT -> "Theme: Light Mode"
            ThemeMode.DARK -> "Theme: Dark Mode"
        }
        _events.tryEmit(UiEvent.ShowMessage(label))
    }

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun updateBrowseSearchQuery(query: String) {
        browseSearchQueryFlow.value = query
    }

    fun updateFilterOption(option: TrackFilterOption) {
        filterOptionFlow.value = option
    }

    fun updateSortOption(option: TrackSortOption) {
        if (sortOptionFlow.value == option) {
            toggleSortDirection()
            return
        }
        sortOptionFlow.value = option
        val defaultAsc = option == TrackSortOption.TITLE || option == TrackSortOption.ARTIST
        sortAscendingFlow.value = defaultAsc
        viewModelScope.launch {
            preferencesRepository.setSortSettings(option, defaultAsc)
        }
    }

    fun toggleSortDirection() {
        val nextAsc = !sortAscendingFlow.value
        sortAscendingFlow.value = nextAsc
        viewModelScope.launch {
            preferencesRepository.setSortSettings(sortOptionFlow.value, nextAsc)
        }
    }

    fun clearTrackFilters() {
        searchQueryFlow.value = ""
        filterOptionFlow.value = TrackFilterOption.ALL
    }

    fun updateBrowseTab(tab: BrowseTab) {
        browseTabFlow.value = tab
    }

    fun playTracks(tracks: List<AudioTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) {
            _events.tryEmit(UiEvent.ShowMessage("No playable tracks in this selection.", isError = true))
            return
        }
        playbackController.playTracks(tracks, startIndex)
    }

    fun shuffleAll() {
        val tracks = libraryState.value.allTracks
        if (tracks.isEmpty()) {
            _events.tryEmit(UiEvent.ShowMessage("Add or scan audio tracks before shuffling.", isError = true))
            return
        }
        if (!playbackState.value.shuffleEnabled) {
            playbackController.toggleShuffle()
        }
        val randomStart = tracks.indices.random()
        playbackController.playTracks(tracks, randomStart)
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekTo(ms: Long) = playbackController.seekTo(ms)
    fun seekRelative(deltaMs: Long) = playbackController.seekRelative(deltaMs)
    fun skipNext() = playbackController.skipNext()
    fun skipPrevious() = playbackController.skipPrevious()
    fun toggleShuffle() = playbackController.toggleShuffle()
    fun cycleRepeatMode() = playbackController.cycleRepeatMode()
    fun setPlaybackSpeed(speed: Float) = playbackController.setPlaybackSpeed(speed)
    fun clearPlaybackError() = playbackController.clearError()

    fun playNext(track: AudioTrack) {
        playbackController.playNext(track)
        _events.tryEmit(UiEvent.ShowMessage("Queued \"${track.title}\" to play next."))
    }

    fun addToQueue(track: AudioTrack) {
        playbackController.addToQueue(track)
        _events.tryEmit(UiEvent.ShowMessage("Added \"${track.title}\" to queue."))
    }

    fun jumpToQueueIndex(index: Int) = playbackController.jumpToQueueIndex(index)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playbackController.moveQueueItem(fromIndex, toIndex)
    fun removeFromQueue(index: Int) = playbackController.removeFromQueue(index)
    fun clearQueue() {
        playbackController.clearQueue()
        _events.tryEmit(UiEvent.ShowMessage("Playback queue cleared."))
    }

    fun startSleepTimerMinutes(minutes: Int) {
        playbackController.startSleepTimerMinutes(minutes)
        _events.tryEmit(UiEvent.ShowMessage("Sleep timer set for $minutes min."))
    }

    fun startSleepAfterCurrentTrack() {
        playbackController.startSleepAfterCurrentTrack()
        _events.tryEmit(UiEvent.ShowMessage("Playback will pause after current track."))
    }

    fun cancelSleepTimer() {
        playbackController.cancelSleepTimer()
        _events.tryEmit(UiEvent.ShowMessage("Sleep timer turned off."))
    }

    fun toggleFavorite(track: AudioTrack) {
        viewModelScope.launch {
            runCatching {
                libraryRepository.toggleFavorite(track.id, track.isFavorite)
            }.onSuccess {
                val msg = if (track.isFavorite) "Removed from Liked Songs" else "Added to Liked Songs"
                _events.emit(UiEvent.ShowMessage(msg))
            }.onFailure { err ->
                _events.emit(UiEvent.ShowMessage(err.localizedMessage ?: "Failed to update favorite.", isError = true))
            }
        }
    }

    fun isPlaylistNameDuplicate(name: String, excludeId: Long? = null): Boolean {
        val normalized = name.trim().lowercase()
        return libraryState.value.playlists.any {
            it.id != excludeId && it.name.trim().lowercase() == normalized
        }
    }

    fun createPlaylist(name: String, initialTrackId: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (isPlaylistNameDuplicate(trimmed)) {
            _events.tryEmit(UiEvent.ShowMessage("A playlist named \"$trimmed\" already exists.", isError = true))
            return
        }
        viewModelScope.launch {
            runCatching {
                val id = libraryRepository.createPlaylist(trimmed)
                if (initialTrackId != null) {
                    libraryRepository.addTrackToPlaylist(id, initialTrackId)
                }
            }.onSuccess {
                _events.emit(UiEvent.ShowMessage("Created playlist \"$trimmed\"."))
            }.onFailure { err ->
                _events.emit(UiEvent.ShowMessage(err.localizedMessage ?: "Could not create playlist.", isError = true))
            }
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        if (isPlaylistNameDuplicate(trimmed, excludeId = playlistId)) {
            _events.tryEmit(UiEvent.ShowMessage("A playlist named \"$trimmed\" already exists.", isError = true))
            return
        }
        viewModelScope.launch {
            runCatching { libraryRepository.renamePlaylist(playlistId, trimmed) }
                .onSuccess { _events.emit(UiEvent.ShowMessage("Renamed playlist to \"$trimmed\".")) }
                .onFailure { _events.emit(UiEvent.ShowMessage("Could not rename playlist.", isError = true)) }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            runCatching { libraryRepository.deletePlaylist(playlistId) }
                .onSuccess { _events.emit(UiEvent.ShowMessage("Playlist deleted.")) }
                .onFailure { _events.emit(UiEvent.ShowMessage("Could not delete playlist.", isError = true)) }
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            runCatching { libraryRepository.addTrackToPlaylist(playlistId, trackId) }
                .onSuccess { _events.emit(UiEvent.ShowMessage("Added track to playlist.")) }
                .onFailure { _events.emit(UiEvent.ShowMessage("Could not add track.", isError = true)) }
        }
    }

    fun addTracksToPlaylist(playlistId: Long, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            runCatching { libraryRepository.addTracksToPlaylist(playlistId, trackIds) }
                .onSuccess { _events.emit(UiEvent.ShowMessage("Added ${trackIds.size} track(s) to playlist.")) }
                .onFailure { _events.emit(UiEvent.ShowMessage("Could not add selected tracks.", isError = true)) }
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            runCatching { libraryRepository.removeTrackFromPlaylist(playlistId, trackId) }
                .onSuccess { _events.emit(UiEvent.ShowMessage("Removed track from playlist.")) }
                .onFailure { _events.emit(UiEvent.ShowMessage("Could not remove track.", isError = true)) }
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        audioEffectManager.setEnabled(enabled)
        saveEqPrefs()
    }

    fun applyEqPreset(preset: String) {
        audioEffectManager.applyPreset(preset)
        saveEqPrefs()
    }

    fun setEqBandLevel(bandIndex: Int, levelMb: Int) {
        audioEffectManager.setBandLevel(bandIndex, levelMb)
        saveEqPrefs()
    }

    fun setBassBoost(strength: Int) {
        audioEffectManager.setBassBoost(strength)
        saveEqPrefs()
    }

    private fun saveEqPrefs() {
        val state = audioEffectManager.state.value
        viewModelScope.launch {
            preferencesRepository.setEqSettings(
                enabled = state.isEnabled,
                presetName = state.activePreset,
                bassBoostStrength = state.bassBoostStrength
            )
        }
    }

    fun getTracksForAlbum(albumKey: String): List<AudioTrack> {
        return libraryState.value.allTracks.filter { "${it.album}||${it.artist}" == albumKey }
    }

    fun getTracksForArtist(artistName: String): List<AudioTrack> {
        return libraryState.value.allTracks.filter { it.artist == artistName }
    }

    fun getTracksForFolder(folderName: String): List<AudioTrack> {
        return libraryState.value.allTracks.filter { it.folderName == folderName }
    }

    fun getTracksForPlaylist(playlistId: Long): List<AudioTrack> {
        val state = libraryState.value
        val playlist = state.playlists.find { it.id == playlistId } ?: return emptyList()
        val byId = state.allTracks.associateBy { it.id }
        return playlist.trackIds.mapNotNull { byId[it] }
    }

    fun getTracksForSmartPlaylist(type: SmartPlaylistType): List<AudioTrack> {
        val state = libraryState.value
        return when (type) {
            SmartPlaylistType.LIKED -> state.favoriteTracks
            SmartPlaylistType.MOST_PLAYED -> state.mostPlayedTracks
            SmartPlaylistType.RECENTLY_ADDED -> state.recentlyAddedTracks
        }
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SonicBloomViewModel(
                        libraryRepository = container.musicLibraryRepository,
                        preferencesRepository = container.preferencesRepository,
                        playbackController = container.playbackController,
                        audioEffectManager = container.audioEffectManager
                    ) as T
                }
            }
        }
    }
}
