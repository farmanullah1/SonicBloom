package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.local.PreferencesRepository
import com.example.data.model.AudioTrack
import com.example.data.model.RepeatModeOption
import com.example.data.model.SleepTimerState
import com.example.data.repository.MusicLibraryRepository
import com.example.widget.SonicBloomWidgetProvider
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val queue: List<AudioTrack> = emptyList(),
    val currentIndex: Int = -1,
    val currentTrack: AudioTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val playbackSpeed: Float = 1.0f,
    val sleepTimer: SleepTimerState = SleepTimerState(),
    val loopStartMs: Long? = null,
    val loopEndMs: Long? = null,
    val isReady: Boolean = false,
    val errorMessage: String? = null
) {
    val isAbLoopActive: Boolean
        get() = loopStartMs != null && loopEndMs != null && loopEndMs > loopStartMs
}

object AbLoopCoordinator {
    const val MIN_SPAN_MS = 500L

    fun nextLoopBounds(
        currentStartMs: Long?,
        currentEndMs: Long?,
        cursorMs: Long,
        durationMs: Long
    ): Pair<Long?, Long?> {
        val safeDur = durationMs.coerceAtLeast(1L)
        val clampedCursor = cursorMs.coerceIn(0L, safeDur)
        return when {
            currentStartMs == null -> clampedCursor to null
            currentEndMs == null -> {
                if (kotlin.math.abs(clampedCursor - currentStartMs) < MIN_SPAN_MS) {
                    currentStartMs to null
                } else if (clampedCursor < currentStartMs) {
                    clampedCursor to currentStartMs
                } else {
                    currentStartMs to clampedCursor
                }
            }
            else -> null to null
        }
    }

    fun shouldWrapToLoopStart(
        positionMs: Long,
        loopStartMs: Long?,
        loopEndMs: Long?
    ): Boolean {
        if (loopStartMs == null || loopEndMs == null || loopEndMs <= loopStartMs) return false
        return positionMs >= loopEndMs
    }
}

class PlaybackController(
    private val context: Context,
    private val libraryRepository: MusicLibraryRepository,
    private val preferencesRepository: PreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private var positionTickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var pendingPlayRequest: Pair<List<AudioTrack>, Int>? = null

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var trackLookupById: Map<String, AudioTrack> = emptyMap()
    private var lastRecordedTrackId: String? = null
    private var lastActiveMediaId: String? = null
    private var resolvedArtByTrackId = mutableMapOf<String, String?>()
    private var lastSessionSaveElapsedMs = 0L
    private var hasAttemptedSessionRestore = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncFromPlayer()
            persistSessionSnapshot(force = true)
            if (isPlaying) startPositionTicker() else stopPositionTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncFromPlayer()
        }

        override fun onPlayerError(error: PlaybackException) {
            val friendlyMessage = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                    "Audio file could not be opened or was moved from storage."
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                    "Unsupported or corrupted audio format."
                else ->
                    error.localizedMessage ?: "Playback failed for this track."
            }
            _uiState.value = _uiState.value.copy(
                isPlaying = false,
                errorMessage = friendlyMessage
            )
            stopPositionTicker()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (_uiState.value.sleepTimer.stopAfterCurrentTrack &&
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            ) {
                controller?.pause()
                cancelSleepTimer()
            }
            val newId = mediaItem?.mediaId
            if (newId != lastActiveMediaId) {
                lastActiveMediaId = newId
                _uiState.value = _uiState.value.copy(
                    loopStartMs = null,
                    loopEndMs = null,
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = null)
            }
            syncFromPlayer()
            recordCurrentTrackPlay()
            persistSessionSnapshot(force = true)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.value = _uiState.value.copy(shuffleEnabled = shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val mapped = when (repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
                Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
                else -> RepeatModeOption.OFF
            }
            _uiState.value = _uiState.value.copy(repeatMode = mapped)
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncFromPlayer()
        }
    }

    init {
        scope.launch {
            libraryRepository.allTracks.collect { tracks ->
                trackLookupById = tracks.associateBy { it.id }
                val updatedQueue = _uiState.value.queue.map { old ->
                    val merged = trackLookupById[old.id] ?: old
                    if (merged.albumArtUri == null && resolvedArtByTrackId[merged.id] != null) {
                        merged.copy(albumArtUri = resolvedArtByTrackId[merged.id])
                    } else merged
                }
                val current = _uiState.value.currentTrack?.let { cur ->
                    val merged = trackLookupById[cur.id] ?: cur
                    if (merged.albumArtUri == null && resolvedArtByTrackId[merged.id] != null) {
                        merged.copy(albumArtUri = resolvedArtByTrackId[merged.id])
                    } else merged
                }
                _uiState.value = _uiState.value.copy(
                    queue = updatedQueue,
                    currentTrack = current
                )
                if (tracks.isNotEmpty() && _uiState.value.queue.isEmpty() && !hasAttemptedSessionRestore) {
                    restoreSavedSessionIfIdle()
                }
            }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(
            context,
            ComponentName(context, SonicBloomPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val mediaController = future.get()
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    scope.launch {
                        val prefs = preferencesRepository.preferences.first()
                        mediaController.shuffleModeEnabled = prefs.shuffleEnabled
                        mediaController.repeatMode = when (prefs.repeatMode) {
                            RepeatModeOption.ALL -> Player.REPEAT_MODE_ALL
                            RepeatModeOption.ONE -> Player.REPEAT_MODE_ONE
                            RepeatModeOption.OFF -> Player.REPEAT_MODE_OFF
                        }
                        mediaController.playbackParameters = PlaybackParameters(prefs.playbackSpeed)
                        _uiState.value = _uiState.value.copy(
                            shuffleEnabled = prefs.shuffleEnabled,
                            repeatMode = prefs.repeatMode,
                            playbackSpeed = prefs.playbackSpeed,
                            isReady = true,
                            errorMessage = null
                        )
                        pendingPlayRequest?.let { (tracks, startIdx) ->
                            pendingPlayRequest = null
                            playTracks(tracks, startIdx)
                        } ?: run {
                            if (mediaController.mediaItemCount > 0) {
                                syncFromPlayer()
                            } else {
                                restoreSavedSessionIfIdle()
                            }
                        }
                        if (mediaController.isPlaying) {
                            startPositionTicker()
                        }
                    }
                }.onFailure { err ->
                    controllerFuture = null
                    _uiState.value = _uiState.value.copy(
                        isReady = false,
                        errorMessage = err.localizedMessage ?: "Audio service failed to connect."
                    )
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun onAbLoopClick(): String? {
        val state = _uiState.value
        val curPos = controller?.currentPosition?.coerceAtLeast(0L) ?: state.positionMs
        val dur = state.durationMs.takeIf { it > 0L } ?: state.currentTrack?.durationMs ?: 0L
        if (dur <= AbLoopCoordinator.MIN_SPAN_MS) return null

        val wasWaitingB = state.loopStartMs != null && state.loopEndMs == null
        val (nextA, nextB) = AbLoopCoordinator.nextLoopBounds(
            currentStartMs = state.loopStartMs,
            currentEndMs = state.loopEndMs,
            cursorMs = curPos,
            durationMs = dur
        )

        if (wasWaitingB && nextB == null) {
            return "Move at least 0.5s away from point A before setting point B."
        }

        _uiState.value = state.copy(loopStartMs = nextA, loopEndMs = nextB)
        if (nextA != null && nextB != null) {
            seekTo(nextA)
            if (controller?.isPlaying == true) {
                startPositionTicker()
            }
        }
        return null
    }

    fun clearAbLoop() {
        _uiState.value = _uiState.value.copy(loopStartMs = null, loopEndMs = null)
    }

    fun playTracks(tracks: List<AudioTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val player = controller
        if (player == null) {
            pendingPlayRequest = tracks to startIndex
            connect()
            return
        }
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val mediaItems = tracks.map { it.toMediaItem() }
        lastActiveMediaId = tracks[safeIndex].id

        _uiState.value = _uiState.value.copy(
            queue = tracks,
            currentIndex = safeIndex,
            currentTrack = tracks[safeIndex],
            loopStartMs = null,
            loopEndMs = null,
            errorMessage = null
        )

        player.setMediaItems(mediaItems, safeIndex, 0L)
        player.prepare()
        player.play()
        recordCurrentTrackPlay(tracks[safeIndex])
        resolveMissingArtworkForCurrent(tracks[safeIndex])
        persistSessionSnapshot(force = true)
    }

    fun togglePlayPause() {
        val player = controller ?: run {
            connect()
            return
        }
        _uiState.value = _uiState.value.copy(errorMessage = null)
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) {
                player.prepare()
            }
            player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        val maxDur = _uiState.value.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        val clamped = positionMs.coerceIn(0L, maxDur)
        player.seekTo(clamped)
        _uiState.value = _uiState.value.copy(positionMs = clamped)
        persistSessionSnapshot(force = true)
    }

    fun seekRelative(deltaMs: Long) {
        val currentPos = controller?.currentPosition ?: _uiState.value.positionMs
        seekTo(currentPos + deltaMs)
    }

    fun skipNext() {
        val player = controller ?: return
        _uiState.value = _uiState.value.copy(errorMessage = null, loopStartMs = null, loopEndMs = null)
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        } else if (player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
            player.prepare()
            player.play()
        }
    }

    fun skipPrevious() {
        val player = controller ?: return
        _uiState.value = _uiState.value.copy(errorMessage = null)
        if (player.currentPosition > 4000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            _uiState.value = _uiState.value.copy(loopStartMs = null, loopEndMs = null)
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
        player.prepare()
        player.play()
    }

    fun toggleShuffle() {
        val next = !_uiState.value.shuffleEnabled
        controller?.shuffleModeEnabled = next
        _uiState.value = _uiState.value.copy(shuffleEnabled = next)
        scope.launch { preferencesRepository.setShuffleEnabled(next) }
    }

    fun cycleRepeatMode() {
        val next = when (_uiState.value.repeatMode) {
            RepeatModeOption.OFF -> RepeatModeOption.ALL
            RepeatModeOption.ALL -> RepeatModeOption.ONE
            RepeatModeOption.ONE -> RepeatModeOption.OFF
        }
        controller?.repeatMode = when (next) {
            RepeatModeOption.ALL -> Player.REPEAT_MODE_ALL
            RepeatModeOption.ONE -> Player.REPEAT_MODE_ONE
            RepeatModeOption.OFF -> Player.REPEAT_MODE_OFF
        }
        _uiState.value = _uiState.value.copy(repeatMode = next)
        scope.launch { preferencesRepository.setRepeatMode(next) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        controller?.playbackParameters = PlaybackParameters(clamped)
        _uiState.value = _uiState.value.copy(playbackSpeed = clamped)
        scope.launch { preferencesRepository.setPlaybackSpeed(clamped) }
    }

    fun playNext(track: AudioTrack) {
        val player = controller ?: run {
            playTracks(listOf(track), 0)
            return
        }
        if (player.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
            return
        }
        val insertIndex = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertIndex, track.toMediaItem())
        val currentQueue = _uiState.value.queue.toMutableList()
        currentQueue.add(insertIndex.coerceAtMost(currentQueue.size), track)
        _uiState.value = _uiState.value.copy(queue = currentQueue)
        persistSessionSnapshot(force = true)
    }

    fun playNextBatch(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val player = controller ?: run {
            playTracks(tracks, 0)
            return
        }
        if (player.mediaItemCount == 0) {
            playTracks(tracks, 0)
            return
        }
        val insertIndex = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItems(insertIndex, tracks.map { it.toMediaItem() })
        syncFromPlayer()
        persistSessionSnapshot(force = true)
    }

    fun addToQueue(track: AudioTrack) {
        val player = controller ?: run {
            playTracks(listOf(track), 0)
            return
        }
        if (player.mediaItemCount == 0) {
            playTracks(listOf(track), 0)
            return
        }
        player.addMediaItem(track.toMediaItem())
        _uiState.value = _uiState.value.copy(queue = _uiState.value.queue + track)
        persistSessionSnapshot(force = true)
    }

    fun addTracksToQueue(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        val player = controller ?: run {
            playTracks(tracks, 0)
            return
        }
        if (player.mediaItemCount == 0) {
            playTracks(tracks, 0)
            return
        }
        player.addMediaItems(tracks.map { it.toMediaItem() })
        syncFromPlayer()
        persistSessionSnapshot(force = true)
    }

    fun jumpToQueueIndex(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) {
            _uiState.value = _uiState.value.copy(errorMessage = null, loopStartMs = null, loopEndMs = null)
            player.seekTo(index, 0L)
            player.prepare()
            player.play()
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val player = controller ?: return
        if (fromIndex == toIndex) return
        if (fromIndex !in 0 until player.mediaItemCount || toIndex !in 0 until player.mediaItemCount) return
        player.moveMediaItem(fromIndex, toIndex)
        syncFromPlayer()
        persistSessionSnapshot(force = true)
    }

    fun removeFromQueue(index: Int) {
        val player = controller ?: return
        if (index in 0 until player.mediaItemCount) {
            player.removeMediaItem(index)
            val updated = _uiState.value.queue.toMutableList()
            if (index in updated.indices) {
                updated.removeAt(index)
                _uiState.value = _uiState.value.copy(queue = updated)
            }
            syncFromPlayer()
            persistSessionSnapshot(force = true)
        }
    }

    fun clearQueue() {
        val player = controller ?: return
        player.stop()
        player.clearMediaItems()
        stopPositionTicker()
        lastActiveMediaId = null
        _uiState.value = _uiState.value.copy(
            queue = emptyList(),
            currentIndex = -1,
            currentTrack = null,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            loopStartMs = null,
            loopEndMs = null,
            errorMessage = null
        )
        persistSessionSnapshot(force = true)
    }

    fun startSleepTimerMinutes(minutes: Int) {
        sleepTimerJob?.cancel()
        val totalMs = minutes * 60_000L
        _uiState.value = _uiState.value.copy(
            sleepTimer = SleepTimerState(remainingMs = totalMs, stopAfterCurrentTrack = false)
        )
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0L) {
                delay(1000L)
                remaining = (remaining - 1000L).coerceAtLeast(0L)
                _uiState.value = _uiState.value.copy(
                    sleepTimer = SleepTimerState(remainingMs = remaining, stopAfterCurrentTrack = false)
                )
            }
            controller?.pause()
            _uiState.value = _uiState.value.copy(sleepTimer = SleepTimerState())
        }
    }

    fun startSleepAfterCurrentTrack() {
        sleepTimerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            sleepTimer = SleepTimerState(remainingMs = 0L, stopAfterCurrentTrack = true)
        )
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.value = _uiState.value.copy(sleepTimer = SleepTimerState())
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (true) {
                val player = controller
                if (player != null && player.isPlaying) {
                    val dur = player.duration.takeIf { it > 0L }
                        ?: _uiState.value.currentTrack?.durationMs
                        ?: 0L
                    var pos = player.currentPosition.coerceAtLeast(0L)
                    val startMs = _uiState.value.loopStartMs
                    val endMs = _uiState.value.loopEndMs
                    if (AbLoopCoordinator.shouldWrapToLoopStart(pos, startMs, endMs) && startMs != null) {
                        player.seekTo(startMs)
                        pos = startMs
                    }
                    _uiState.value = _uiState.value.copy(
                        positionMs = pos,
                        durationMs = dur
                    )
                    persistSessionSnapshot(force = false)
                }
                val intervalMs = if (_uiState.value.isAbLoopActive) 120L else 250L
                delay(intervalMs)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = null
    }

    fun restoreSavedSessionIfIdle() {
        val player = controller ?: return
        if (player.mediaItemCount > 0 || pendingPlayRequest != null) return
        scope.launch {
            val snapshot = preferencesRepository.getSavedPlaybackSession()
            if (snapshot.queueTrackIds.isEmpty()) return@launch
            val availableTracks = libraryRepository.allTracks.first()
            if (availableTracks.isEmpty()) return@launch
            hasAttemptedSessionRestore = true
            val byId = availableTracks.associateBy { it.id }
            val restoredQueue = snapshot.queueTrackIds.mapNotNull { byId[it] }
            if (restoredQueue.isEmpty()) return@launch

            val safeIndex = snapshot.currentIndex.coerceIn(0, restoredQueue.lastIndex)
            val currentTrack = restoredQueue[safeIndex]
            val clampedPos = snapshot.positionMs.coerceIn(0L, currentTrack.durationMs.coerceAtLeast(0L))
            lastActiveMediaId = currentTrack.id

            player.setMediaItems(restoredQueue.map { it.toMediaItem() }, safeIndex, clampedPos)
            player.prepare()
            _uiState.value = _uiState.value.copy(
                queue = restoredQueue,
                currentIndex = safeIndex,
                currentTrack = currentTrack,
                isPlaying = false,
                positionMs = clampedPos,
                durationMs = currentTrack.durationMs,
                loopStartMs = null,
                loopEndMs = null,
                errorMessage = null
            )
            resolveMissingArtworkForCurrent(currentTrack)
            SonicBloomWidgetProvider.updateWidgets(
                context = context,
                title = currentTrack.title,
                artist = currentTrack.artist,
                artUri = currentTrack.albumArtUri,
                isPlaying = false
            )
        }
    }

    private fun syncFromPlayer() {
        val player = controller ?: return
        val index = player.currentMediaItemIndex
        val mediaItem = player.currentMediaItem
        val resolvedQueue = if (player.mediaItemCount > 0) {
            (0 until player.mediaItemCount).mapNotNull { i ->
                val item = player.getMediaItemAt(i)
                val base = trackLookupById[item.mediaId] ?: _uiState.value.queue.find { it.id == item.mediaId }
                if (base != null && base.albumArtUri == null && resolvedArtByTrackId[base.id] != null) {
                    base.copy(albumArtUri = resolvedArtByTrackId[base.id])
                } else base
            }
        } else {
            emptyList()
        }
        val currentTrack = mediaItem?.let { item ->
            val base = trackLookupById[item.mediaId] ?: resolvedQueue.find { it.id == item.mediaId }
            if (base != null && base.albumArtUri == null && resolvedArtByTrackId[base.id] != null) {
                base.copy(albumArtUri = resolvedArtByTrackId[base.id])
            } else base
        }
        val dur = player.duration.takeIf { it > 0L } ?: currentTrack?.durationMs ?: 0L

        _uiState.value = _uiState.value.copy(
            queue = resolvedQueue,
            currentIndex = index,
            currentTrack = currentTrack,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = dur
        )
        if (currentTrack != null) {
            resolveMissingArtworkForCurrent(currentTrack)
        }
        SonicBloomWidgetProvider.updateWidgets(
            context = context,
            title = currentTrack?.title,
            artist = currentTrack?.artist,
            artUri = currentTrack?.albumArtUri,
            isPlaying = player.isPlaying
        )
    }

    private fun resolveMissingArtworkForCurrent(track: AudioTrack) {
        if (track.albumArtUri != null || resolvedArtByTrackId.containsKey(track.id)) return
        scope.launch {
            val resolved = libraryRepository.resolveEmbeddedArtworkIfMissing(track)
            resolvedArtByTrackId[track.id] = resolved
            if (resolved != null) {
                val updatedQueue = _uiState.value.queue.map {
                    if (it.id == track.id && it.albumArtUri == null) it.copy(albumArtUri = resolved) else it
                }
                val current = _uiState.value.currentTrack
                val updatedCurrent = if (current?.id == track.id && current.albumArtUri == null) {
                    current.copy(albumArtUri = resolved)
                } else current
                _uiState.value = _uiState.value.copy(
                    queue = updatedQueue,
                    currentTrack = updatedCurrent
                )
                if (updatedCurrent?.id == track.id) {
                    SonicBloomWidgetProvider.updateWidgets(
                        context = context,
                        title = updatedCurrent.title,
                        artist = updatedCurrent.artist,
                        artUri = resolved,
                        isPlaying = _uiState.value.isPlaying
                    )
                }
            }
        }
    }

    private fun persistSessionSnapshot(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSessionSaveElapsedMs < 4000L) return
        lastSessionSaveElapsedMs = now

        val state = _uiState.value
        val ids = state.queue.map { it.id }
        val idx = state.currentIndex.coerceAtLeast(0)
        val pos = controller?.currentPosition?.coerceAtLeast(0L) ?: state.positionMs
        val cur = state.currentTrack
        scope.launch {
            preferencesRepository.savePlaybackSession(
                queueTrackIds = ids,
                currentIndex = idx,
                positionMs = pos,
                lastTrackTitle = cur?.title.orEmpty(),
                lastTrackArtist = cur?.artist.orEmpty(),
                lastTrackArtUri = cur?.albumArtUri,
                isPlaying = state.isPlaying
            )
        }
    }

    private fun recordCurrentTrackPlay(explicitTrack: AudioTrack? = null) {
        val track = explicitTrack ?: _uiState.value.currentTrack ?: return
        if (track.id == lastRecordedTrackId) return
        lastRecordedTrackId = track.id
        scope.launch {
            libraryRepository.recordPlayback(track.id)
        }
    }

    private fun AudioTrack.toMediaItem(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
        albumArtUri?.let { art ->
            metadataBuilder.setArtworkUri(Uri.parse(art))
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(Uri.parse(contentUri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun release() {
        stopPositionTicker()
        sleepTimerJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
    }
}
