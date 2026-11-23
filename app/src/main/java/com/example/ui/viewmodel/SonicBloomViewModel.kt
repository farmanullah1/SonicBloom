package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.local.PlayerPreferences
import com.example.data.local.PreferencesRepository
import com.example.data.model.AlbumSummary
import com.example.data.model.ArtistSummary
import com.example.data.model.AudioTrack
import com.example.data.model.BrowseTab
import com.example.data.model.DynamicTrackColors
import com.example.data.model.EqualizerState
import com.example.data.model.FolderSummary
import com.example.data.model.PlaylistInfo
import com.example.data.model.SmartPlaylistType
import com.example.data.model.ThemeMode
import com.example.data.model.TrackFilterOption
import com.example.data.model.TrackLyrics
import com.example.data.model.TrackSortOption
import com.example.data.model.WaveformData
import com.example.data.repository.LibraryScanResult
import com.example.data.repository.MusicLibraryRepository
import com.example.playback.AudioEffectManager
import com.example.playback.PlaybackController
import com.example.playback.PlaybackUiState
import com.example.ui.util.ArtworkColorExtractor
import com.example.ui.util.WaveformExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    val losslessVaultTracks: List<AudioTrack> = emptyList(),
    val unplayedGemsTracks: List<AudioTrack> = emptyList(),
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
    val totalSizeBytes: Long = 0L,
    val artCacheSizeBytes: Long = 0L,
    val crossfadeSeconds: Int = 0,
    val volumeNormalizationEnabled: Boolean = false,
    val minTrackDurationSec: Int = 0,
    val excludedFolders: Set<String> = emptySet(),
    val availableFolders: List<String> = emptyList()
) {
    val hasActiveTrackFilters: Boolean
        get() = searchQuery.isNotBlank() || filterOption != TrackFilterOption.ALL
}

internal fun computeLibraryUiState(
    rawTracks: List<AudioTrack>,
    playlists: List<PlaylistInfo>,
    isScanning: Boolean,
    query: String,
    browseQuery: String,
    filterOption: TrackFilterOption,
    sortOption: TrackSortOption,
    sortAscending: Boolean,
    browseTab: BrowseTab,
    hasPerm: Boolean,
    libError: String?,
    themeMode: ThemeMode,
    artCacheSizeBytes: Long,
    prefs: PlayerPreferences
): LibraryUiState {
    val availableFolders = rawTracks.map { it.folderName }
        .distinct()
        .sortedBy { it.lowercase() }

    val minDurationMs = prefs.minTrackDurationSec * 1000L
    val tracks = rawTracks.filter { track ->
        !prefs.excludedFolders.contains(track.folderName) &&
            (minDurationMs <= 0L || track.durationMs >= minDurationMs)
    }

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
    val losslessVault = tracks
        .filter { it.isLossless || (it.bitrateKbps ?: 0) >= 320 }
        .sortedByDescending { it.bitrateKbps ?: 0 }
        .take(30)
    val unplayedGems = tracks
        .filter { it.playCount == 0 }
        .sortedByDescending { it.dateAddedSec }
        .take(25)

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

    return LibraryUiState(
        allTracks = tracks,
        filteredTracks = sortedTracks,
        recentlyPlayed = recentlyPlayed,
        favoriteTracks = favorites,
        mostPlayedTracks = mostPlayed,
        recentlyAddedTracks = recentlyAdded,
        losslessVaultTracks = losslessVault,
        unplayedGemsTracks = unplayedGems,
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
        totalSizeBytes = tracks.sumOf { it.sizeBytes },
        artCacheSizeBytes = artCacheSizeBytes,
        crossfadeSeconds = prefs.crossfadeSeconds,
        volumeNormalizationEnabled = prefs.volumeNormalizationEnabled,
        minTrackDurationSec = prefs.minTrackDurationSec,
        excludedFolders = prefs.excludedFolders,
        availableFolders = availableFolders
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class SonicBloomViewModel(
    private val appContext: Context,
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
    private val isSystemDarkFlow = MutableStateFlow(false)
    private val _selectedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTrackIds: StateFlow<Set<String>> = _selectedTrackIds.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    val playbackState: StateFlow<PlaybackUiState> = playbackController.uiState
    val equalizerState: StateFlow<EqualizerState> = audioEffectManager.state
    val builtInEqualizerPresets: List<String> = audioEffectManager.builtInPresetNames

    val dynamicColors: StateFlow<DynamicTrackColors> = combine(
        playbackState.map { it.currentTrack?.albumArtUri }.distinctUntilChanged(),
        themeModeFlow,
        isSystemDarkFlow
    ) { artUri, mode, systemDark ->
        val isDark = when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        artUri to isDark
    }.flatMapLatest { (artUri, isDark) ->
        flow {
            emit(ArtworkColorExtractor.extractDynamicColors(appContext, artUri, isDark))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DynamicTrackColors()
    )

    val currentWaveform: StateFlow<WaveformData> = playbackState
        .map { it.currentTrack }
        .distinctUntilChanged { a, b -> a?.id == b?.id && a?.sizeBytes == b?.sizeBytes }
        .flatMapLatest { track ->
            if (track == null) {
                flowOf(WaveformData())
            } else {
                flow {
                    emit(
                        WaveformData(
                            trackId = track.id,
                            amplitudes = WaveformExtractor.fallbackAmplitudes(track.id),
                            isRealPcm = false
                        )
                    )
                    val realWaveform = libraryRepository.getOrExtractWaveform(track)
                    audioEffectManager.updateTrackNormalizationGain(realWaveform.suggestedGainDb)
                    emit(realWaveform)
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = WaveformData()
        )

    val currentTrackLyrics: StateFlow<TrackLyrics?> = playbackState
        .map { it.currentTrack }
        .distinctUntilChanged { a, b -> a?.id == b?.id && a?.contentUri == b?.contentUri }
        .flatMapLatest { track ->
            if (track == null) {
                flowOf(null)
            } else {
                libraryRepository.observeTrackLyrics(track.id).flatMapLatest { stored ->
                    if (stored != null) {
                        flowOf(stored)
                    } else {
                        flow<TrackLyrics?> {
                            emit(libraryRepository.loadEmbeddedLyricsIfAvailable(track))
                        }
                    }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    private val queryOptionsFlow = combine(
        searchQueryFlow,
        browseSearchQueryFlow,
        filterOptionFlow,
        sortOptionFlow,
        sortAscendingFlow
    ) { query, browseQuery, filter, sort, asc ->
        QueryOptions(query, browseQuery, filter, sort, asc)
    }

    private val statusOptionsFlow = combine(
        browseTabFlow,
        hasPermissionFlow,
        libraryErrorFlow,
        themeModeFlow,
        libraryRepository.artCacheSizeBytes
    ) { tab, perm, err, theme, cacheSize ->
        StatusOptions(tab, perm, err, theme, cacheSize)
    }

    val libraryState: StateFlow<LibraryUiState> = combine(
        libraryRepository.allTracks,
        libraryRepository.playlists,
        libraryRepository.isScanning,
        preferencesRepository.preferences,
        queryOptionsFlow,
        statusOptionsFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val rawTracks = values[0] as List<AudioTrack>
        @Suppress("UNCHECKED_CAST")
        val playlists = values[1] as List<PlaylistInfo>
        val isScanning = values[2] as Boolean
        val prefs = values[3] as PlayerPreferences
        val qOpts = values[4] as QueryOptions
        val sOpts = values[5] as StatusOptions

        computeLibraryUiState(
            rawTracks = rawTracks,
            playlists = playlists,
            isScanning = isScanning,
            query = qOpts.searchQuery,
            browseQuery = qOpts.browseSearchQuery,
            filterOption = qOpts.filterOption,
            sortOption = qOpts.sortOption,
            sortAscending = qOpts.sortAscending,
            browseTab = sOpts.browseTab,
            hasPerm = sOpts.hasPermission,
            libError = sOpts.libraryError,
            themeMode = sOpts.themeMode,
            artCacheSizeBytes = sOpts.artCacheSizeBytes,
            prefs = prefs
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = LibraryUiState()
        )

    init {
        playbackController.connect()
        viewModelScope.launch {
            libraryRepository.customEqPresets.collect { custom ->
                audioEffectManager.updateCustomPresets(custom)
            }
        }
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            themeModeFlow.value = prefs.themeMode
            sortOptionFlow.value = prefs.sortOption
            sortAscendingFlow.value = prefs.sortAscending
            audioEffectManager.setEnabled(prefs.eqEnabled)
            audioEffectManager.setBassBoost(prefs.bassBoostStrength)
            audioEffectManager.setVirtualizerStrength(prefs.virtualizerStrength)
            audioEffectManager.setNormalizationEnabled(prefs.volumeNormalizationEnabled)
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
                    playbackController.restoreSavedSessionIfIdle()
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

    fun updateSystemDarkTheme(isSystemDark: Boolean) {
        isSystemDarkFlow.value = isSystemDark
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

    fun setCrossfadeSeconds(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setCrossfadeSeconds(seconds)
        }
    }

    fun setVolumeNormalizationEnabled(enabled: Boolean) {
        audioEffectManager.setNormalizationEnabled(enabled)
        viewModelScope.launch {
            preferencesRepository.setVolumeNormalizationEnabled(enabled)
        }
    }

    fun setMinTrackDurationSec(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setMinTrackDurationSec(seconds)
        }
    }

    fun toggleExcludedFolder(folderName: String) {
        val current = libraryState.value.excludedFolders
        val next = if (current.contains(folderName)) current - folderName else current + folderName
        viewModelScope.launch {
            preferencesRepository.setExcludedFolders(next)
        }
    }

    fun clearExcludedFolders() {
        viewModelScope.launch {
            preferencesRepository.setExcludedFolders(emptySet())
        }
    }

    fun clearArtworkCache() {
        viewModelScope.launch {
            libraryRepository.clearArtworkCache()
            _events.emit(UiEvent.ShowMessage("Cleared artwork thumbnail and color cache."))
        }
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

    fun onAbLoopClick() {
        val notice = playbackController.onAbLoopClick()
        if (notice != null) {
            _events.tryEmit(UiEvent.ShowMessage(notice, isError = true))
        }
    }

    fun clearAbLoop() = playbackController.clearAbLoop()

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

    fun reorderPlaylistTracks(playlistId: Long, fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val currentTracks = getTracksForPlaylist(playlistId).toMutableList()
        if (fromIndex !in currentTracks.indices || toIndex !in currentTracks.indices) return
        val moved = currentTracks.removeAt(fromIndex)
        currentTracks.add(toIndex, moved)
        viewModelScope.launch {
            runCatching {
                libraryRepository.reorderPlaylistTracks(playlistId, currentTracks.map { it.id })
            }.onFailure {
                _events.emit(UiEvent.ShowMessage("Could not save playlist order.", isError = true))
            }
        }
    }

    fun exportTracksM3u(playlistName: String, tracks: List<AudioTrack>, targetUri: Uri) {
        if (tracks.isEmpty()) {
            _events.tryEmit(UiEvent.ShowMessage("No tracks to export.", isError = true))
            return
        }
        viewModelScope.launch {
            libraryRepository.exportPlaylistToM3u(targetUri, playlistName, tracks)
                .onSuccess { count ->
                    _events.emit(UiEvent.ShowMessage("Exported $count track(s) to M3U."))
                }
                .onFailure { err ->
                    _events.emit(UiEvent.ShowMessage(err.localizedMessage ?: "Failed to export M3U.", isError = true))
                }
        }
    }

    fun importPlaylistM3u(sourceUri: Uri, fallbackName: String) {
        viewModelScope.launch {
            libraryRepository.importPlaylistFromM3u(sourceUri, fallbackName)
                .onSuccess { outcome ->
                    if (outcome.matchedCount > 0) {
                        val extra = if (outcome.skippedCount > 0) {
                            " (${outcome.skippedCount} not found in library)"
                        } else ""
                        _events.emit(
                            UiEvent.ShowMessage(
                                "Imported \"${outcome.playlistName}\" with ${outcome.matchedCount} track(s)$extra."
                            )
                        )
                    } else {
                        _events.emit(
                            UiEvent.ShowMessage(
                                "No tracks from this M3U matched your local library.",
                                isError = true
                            )
                        )
                    }
                }
                .onFailure { err ->
                    _events.emit(UiEvent.ShowMessage(err.localizedMessage ?: "Could not read M3U file.", isError = true))
                }
        }
    }

    fun saveTrackMetadataOverride(trackId: String, title: String, artist: String, album: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            runCatching {
                libraryRepository.saveTrackMetadataOverride(
                    trackId = trackId,
                    title = title,
                    artist = artist.ifBlank { "Unknown Artist" },
                    album = album.ifBlank { "Single / Unknown Album" }
                )
            }.onSuccess {
                _events.emit(UiEvent.ShowMessage("Updated track metadata tags."))
            }.onFailure {
                _events.emit(UiEvent.ShowMessage("Failed to save tag overrides.", isError = true))
            }
        }
    }

    fun clearTrackMetadataOverride(trackId: String) {
        viewModelScope.launch {
            runCatching { libraryRepository.clearTrackMetadataOverride(trackId) }
                .onSuccess { _events.emit(UiEvent.ShowMessage("Restored original audio metadata.")) }
        }
    }

    fun toggleTrackSelection(trackId: String) {
        val current = _selectedTrackIds.value
        _selectedTrackIds.value = if (current.contains(trackId)) {
            current - trackId
        } else {
            current + trackId
        }
    }

    fun selectAllTracks(tracks: List<AudioTrack>) {
        _selectedTrackIds.value = tracks.map { it.id }.toSet()
    }

    fun clearTrackSelection() {
        _selectedTrackIds.value = emptySet()
    }

    private fun resolveSelectedTracks(pool: List<AudioTrack>): List<AudioTrack> {
        val selected = _selectedTrackIds.value
        return pool.filter { selected.contains(it.id) }
    }

    fun batchPlayNext(pool: List<AudioTrack>) {
        val chosen = resolveSelectedTracks(pool)
        if (chosen.isEmpty()) return
        playbackController.playNextBatch(chosen)
        clearTrackSelection()
        _events.tryEmit(UiEvent.ShowMessage("Queued ${chosen.size} track(s) to play next."))
    }

    fun batchAddToQueue(pool: List<AudioTrack>) {
        val chosen = resolveSelectedTracks(pool)
        if (chosen.isEmpty()) return
        playbackController.addTracksToQueue(chosen)
        clearTrackSelection()
        _events.tryEmit(UiEvent.ShowMessage("Added ${chosen.size} track(s) to queue."))
    }

    fun batchAddToPlaylist(playlistId: Long) {
        val ids = _selectedTrackIds.value.toList()
        if (ids.isEmpty()) return
        clearTrackSelection()
        addTracksToPlaylist(playlistId, ids)
    }

    fun batchCreateAndAddToPlaylist(name: String) {
        val trimmed = name.trim()
        val ids = _selectedTrackIds.value.toList()
        if (trimmed.isEmpty() || ids.isEmpty()) return
        if (isPlaylistNameDuplicate(trimmed)) {
            _events.tryEmit(UiEvent.ShowMessage("A playlist named \"$trimmed\" already exists.", isError = true))
            return
        }
        clearTrackSelection()
        viewModelScope.launch {
            runCatching {
                val id = libraryRepository.createPlaylist(trimmed)
                libraryRepository.addTracksToPlaylist(id, ids)
            }.onSuccess {
                _events.emit(UiEvent.ShowMessage("Created \"$trimmed\" with ${ids.size} track(s)."))
            }.onFailure { err ->
                _events.emit(UiEvent.ShowMessage(err.localizedMessage ?: "Could not create playlist.", isError = true))
            }
        }
    }

    fun batchRemoveFromPlaylist(playlistId: Long) {
        val ids = _selectedTrackIds.value.toList()
        if (ids.isEmpty()) return
        clearTrackSelection()
        viewModelScope.launch {
            runCatching {
                ids.forEach { libraryRepository.removeTrackFromPlaylist(playlistId, it) }
            }.onSuccess {
                _events.emit(UiEvent.ShowMessage("Removed ${ids.size} track(s) from playlist."))
            }.onFailure {
                _events.emit(UiEvent.ShowMessage("Could not remove selected tracks.", isError = true))
            }
        }
    }

    fun importLyricsForCurrentTrack(uri: Uri) {
        val track = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            libraryRepository.importLyricsFile(track.id, uri)
                .onSuccess {
                    _events.emit(UiEvent.ShowMessage("Attached lyrics to \"${track.title}\"."))
                }
                .onFailure { err ->
                    _events.emit(UiEvent.ShowMessage(err.localizedMessage ?: "Unable to read lyrics file.", isError = true))
                }
        }
    }

    fun clearLyricsForCurrentTrack() {
        val track = playbackState.value.currentTrack ?: return
        viewModelScope.launch {
            libraryRepository.clearTrackLyrics(track.id)
            _events.emit(UiEvent.ShowMessage("Cleared custom lyrics."))
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

    fun setVirtualizerStrength(strength: Int) {
        audioEffectManager.setVirtualizerStrength(strength)
        saveEqPrefs()
    }

    fun saveCustomEqPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        if (builtInEqualizerPresets.any { it.equals(trimmed, ignoreCase = true) }) {
            _events.tryEmit(UiEvent.ShowMessage("Choose a name distinct from built-in presets.", isError = true))
            return
        }
        val state = audioEffectManager.state.value
        viewModelScope.launch {
            libraryRepository.saveCustomEqPreset(
                name = trimmed,
                bandLevelsMb = state.bands.map { it.levelMb },
                bassBoostStrength = state.bassBoostStrength,
                virtualizerStrength = state.virtualizerStrength
            )
            audioEffectManager.applyPreset(trimmed)
            preferencesRepository.setEqSettings(
                enabled = state.isEnabled,
                presetName = trimmed,
                bassBoostStrength = state.bassBoostStrength,
                virtualizerStrength = state.virtualizerStrength
            )
            _events.emit(UiEvent.ShowMessage("Saved EQ preset \"$trimmed\"."))
        }
    }

    fun deleteCustomEqPreset(name: String) {
        viewModelScope.launch {
            libraryRepository.deleteCustomEqPreset(name)
            if (audioEffectManager.state.value.activePreset == name) {
                audioEffectManager.applyPreset("Flat")
                saveEqPrefs()
            }
            _events.emit(UiEvent.ShowMessage("Deleted preset \"$name\"."))
        }
    }

    private fun saveEqPrefs() {
        val state = audioEffectManager.state.value
        viewModelScope.launch {
            preferencesRepository.setEqSettings(
                enabled = state.isEnabled,
                presetName = state.activePreset,
                bassBoostStrength = state.bassBoostStrength,
                virtualizerStrength = state.virtualizerStrength
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
            SmartPlaylistType.LOSSLESS_VAULT -> state.losslessVaultTracks
            SmartPlaylistType.UNPLAYED_GEMS -> state.unplayedGemsTracks
        }
    }

    private data class QueryOptions(
        val searchQuery: String,
        val browseSearchQuery: String,
        val filterOption: TrackFilterOption,
        val sortOption: TrackSortOption,
        val sortAscending: Boolean
    )

    private data class StatusOptions(
        val browseTab: BrowseTab,
        val hasPermission: Boolean,
        val libraryError: String?,
        val themeMode: ThemeMode,
        val artCacheSizeBytes: Long
    )

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SonicBloomViewModel(
                        appContext = container.appContext,
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
