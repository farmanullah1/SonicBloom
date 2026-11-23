package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.MainActivity
import com.example.R
import com.example.SonicBloomApplication
import com.example.data.model.RepeatModeOption
import com.example.widget.SonicBloomWidgetProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SonicBloomPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var activePlayer: ExoPlayer? = null
    private var stagingPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var favoriteTrackIds: Set<String> = emptySet()
    private var crossfadeSeconds: Int = 0
    private var normalizationEnabled: Boolean = false
    private var crossfadeMonitorJob: Job? = null
    private var activeCrossfadeJob: Job? = null
    private var isCrossfading: Boolean = false
    private var suppressNextTransitionCancel: Boolean = false

    private val audioSessionListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            (application as? SonicBloomApplication)?.container?.audioEffectManager
                ?.attachToAudioSession(audioSessionId)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying && isCrossfading) {
                cancelActiveCrossfade()
            }
            if (isPlaying) {
                startCrossfadeMonitor()
            } else {
                crossfadeMonitorJob?.cancel()
            }
            pushWidgetUpdate()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (suppressNextTransitionCancel) {
                suppressNextTransitionCancel = false
            } else if (isCrossfading) {
                cancelActiveCrossfade()
            }
            updateTrackNormalization(mediaItem?.mediaId)
            updateCustomLayout()
            pushWidgetUpdate()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateCustomLayout()
        }
    }

    private val sessionCallback = object : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CUSTOM_CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_CMD_CYCLE_REPEAT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            val appContainer = (application as? SonicBloomApplication)?.container
            when (customCommand.customAction) {
                CUSTOM_CMD_TOGGLE_FAVORITE -> {
                    val trackId = session.player.currentMediaItem?.mediaId
                    if (trackId != null && appContainer != null) {
                        val currentlyFav = favoriteTrackIds.contains(trackId)
                        serviceScope.launch {
                            appContainer.musicLibraryRepository.toggleFavorite(trackId, currentlyFav)
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CUSTOM_CMD_CYCLE_REPEAT -> {
                    val player = session.player
                    val nextRepeat = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    player.repeatMode = nextRepeat
                    val mapped = when (nextRepeat) {
                        Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
                        Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
                        else -> RepeatModeOption.OFF
                    }
                    if (appContainer != null) {
                        serviceScope.launch {
                            appContainer.preferencesRepository.setRepeatMode(mapped)
                        }
                    }
                    updateCustomLayout()
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = buildConfiguredPlayer()
        player.addListener(audioSessionListener)
        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            (application as? SonicBloomApplication)?.container?.audioEffectManager
                ?.attachToAudioSession(player.audioSessionId)
        }

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        activePlayer = player
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(sessionCallback)
            .build()

        observeAppPreferencesAndFavorites()
        updateCustomLayout()
    }

    private fun buildConfiguredPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        return ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private fun observeAppPreferencesAndFavorites() {
        val container = (application as? SonicBloomApplication)?.container ?: return
        serviceScope.launch {
            container.musicLibraryRepository.allTracks.collect { tracks ->
                val newFavs = tracks.filter { it.isFavorite }.map { it.id }.toSet()
                if (newFavs != favoriteTrackIds) {
                    favoriteTrackIds = newFavs
                    updateCustomLayout()
                }
            }
        }
        serviceScope.launch {
            container.preferencesRepository.preferences.collect { prefs ->
                crossfadeSeconds = prefs.crossfadeSeconds
                normalizationEnabled = prefs.volumeNormalizationEnabled
                container.audioEffectManager.setNormalizationEnabled(prefs.volumeNormalizationEnabled)
                if (prefs.volumeNormalizationEnabled) {
                    updateTrackNormalization(activePlayer?.currentMediaItem?.mediaId)
                }
                if (prefs.crossfadeSeconds <= 0 && isCrossfading) {
                    cancelActiveCrossfade()
                } else if (prefs.crossfadeSeconds > 0 && activePlayer?.isPlaying == true) {
                    startCrossfadeMonitor()
                }
            }
        }
    }

    private fun updateTrackNormalization(trackId: String?) {
        if (!normalizationEnabled || trackId.isNullOrBlank()) return
        val container = (application as? SonicBloomApplication)?.container ?: return
        serviceScope.launch {
            val cachedGain = container.musicLibraryRepository.getTrackNormalizationGainDb(trackId)
            container.audioEffectManager.updateTrackNormalizationGain(cachedGain)
            if (cachedGain == 0f) {
                val track = container.musicLibraryRepository.allTracks.first().find { it.id == trackId }
                if (track != null) {
                    val waveform = container.musicLibraryRepository.getOrExtractWaveform(track)
                    if (activePlayer?.currentMediaItem?.mediaId == trackId) {
                        container.audioEffectManager.updateTrackNormalizationGain(waveform.suggestedGainDb)
                    }
                }
            }
        }
    }

    private fun startCrossfadeMonitor() {
        crossfadeMonitorJob?.cancel()
        if (crossfadeSeconds <= 0) return
        crossfadeMonitorJob = serviceScope.launch {
            while (true) {
                val player = activePlayer
                val cfSec = crossfadeSeconds
                if (player != null && cfSec > 0 && player.isPlaying && !isCrossfading) {
                    if (player.repeatMode != Player.REPEAT_MODE_ONE) {
                        val nextIdx = player.nextMediaItemIndex
                        val dur = player.duration
                        val pos = player.currentPosition
                        val cfWindowMs = cfSec * 1000L
                        if (nextIdx != C.INDEX_UNSET &&
                            dur > cfWindowMs * 2L &&
                            (dur - pos) in 250L..cfWindowMs
                        ) {
                            beginCrossfade(player, nextIdx, (dur - pos).coerceIn(800L, cfWindowMs))
                        }
                    }
                }
                delay(200L)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun beginCrossfade(outgoing: ExoPlayer, nextIndex: Int, fadeDurationMs: Long) {
        if (isCrossfading) return
        isCrossfading = true
        activeCrossfadeJob?.cancel()
        activeCrossfadeJob = serviceScope.launch {
            val incoming = stagingPlayer ?: buildConfiguredPlayer().also { stagingPlayer = it }
            try {
                val items = (0 until outgoing.mediaItemCount).map { outgoing.getMediaItemAt(it) }
                if (nextIndex !in items.indices) {
                    isCrossfading = false
                    return@launch
                }

                incoming.volume = 0f
                incoming.repeatMode = outgoing.repeatMode
                incoming.shuffleModeEnabled = outgoing.shuffleModeEnabled
                incoming.playbackParameters = outgoing.playbackParameters
                incoming.setMediaItems(items, nextIndex, 0L)
                incoming.prepare()
                incoming.play()

                val stepMs = 50L
                val steps = (fadeDurationMs / stepMs).toInt().coerceAtLeast(10)
                for (step in 1..steps) {
                    if (!outgoing.isPlaying && !incoming.isPlaying) {
                        cancelActiveCrossfade()
                        return@launch
                    }
                    val t = step.toFloat() / steps.toFloat()
                    outgoing.volume = cos(t * PI / 2.0).toFloat().coerceIn(0f, 1f)
                    incoming.volume = sin(t * PI / 2.0).toFloat().coerceIn(0f, 1f)
                    delay(stepMs)
                }

                incoming.volume = 1f
                outgoing.removeListener(audioSessionListener)
                incoming.addListener(audioSessionListener)

                suppressNextTransitionCancel = true
                mediaSession?.player = incoming
                activePlayer = incoming
                stagingPlayer = outgoing

                if (incoming.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    (application as? SonicBloomApplication)?.container?.audioEffectManager
                        ?.attachToAudioSession(incoming.audioSessionId)
                }
                updateTrackNormalization(incoming.currentMediaItem?.mediaId)

                outgoing.pause()
                outgoing.stop()
                outgoing.clearMediaItems()
                outgoing.volume = 1f

                updateCustomLayout()
                pushWidgetUpdate()
            } catch (_: Exception) {
                outgoing.volume = 1f
                runCatching {
                    incoming.stop()
                    incoming.clearMediaItems()
                    incoming.volume = 1f
                }
            } finally {
                isCrossfading = false
            }
        }
    }

    private fun cancelActiveCrossfade() {
        activeCrossfadeJob?.cancel()
        activeCrossfadeJob = null
        isCrossfading = false
        activePlayer?.volume = 1f
        stagingPlayer?.let { st ->
            runCatching {
                st.stop()
                st.clearMediaItems()
                st.volume = 1f
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateCustomLayout() {
        val session = mediaSession ?: return
        val currentId = activePlayer?.currentMediaItem?.mediaId
        val isFav = currentId != null && favoriteTrackIds.contains(currentId)
        val favButton = CommandButton.Builder(
            if (isFav) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setCustomIconResId(if (isFav) R.drawable.ic_notif_fav_filled else R.drawable.ic_notif_fav_outline)
            .setDisplayName(if (isFav) "Remove Favorite" else "Add Favorite")
            .setSessionCommand(SessionCommand(CUSTOM_CMD_TOGGLE_FAVORITE, Bundle.EMPTY))
            .build()

        val repeatMode = activePlayer?.repeatMode ?: Player.REPEAT_MODE_OFF
        val (repIconConst, repIconRes, repName) = when (repeatMode) {
            Player.REPEAT_MODE_ALL -> Triple(CommandButton.ICON_REPEAT_ALL, R.drawable.ic_notif_repeat_all, "Repeat All")
            Player.REPEAT_MODE_ONE -> Triple(CommandButton.ICON_REPEAT_ONE, R.drawable.ic_notif_repeat_one, "Repeat One")
            else -> Triple(CommandButton.ICON_REPEAT_OFF, R.drawable.ic_notif_repeat_off, "Repeat Off")
        }
        val repeatButton = CommandButton.Builder(repIconConst)
            .setCustomIconResId(repIconRes)
            .setDisplayName(repName)
            .setSessionCommand(SessionCommand(CUSTOM_CMD_CYCLE_REPEAT, Bundle.EMPTY))
            .build()

        session.setCustomLayout(listOf(favButton, repeatButton))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_WIDGET_PLAY_PAUSE ||
            action == ACTION_WIDGET_NEXT ||
            action == ACTION_WIDGET_PREVIOUS
        ) {
            serviceScope.launch {
                handleWidgetCommand(action)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private suspend fun handleWidgetCommand(action: String) {
        val player = activePlayer ?: return
        if (player.mediaItemCount == 0) {
            restoreSavedQueueIntoPlayer(player)
        }
        if (player.mediaItemCount == 0) {
            pushWidgetUpdate()
            return
        }
        when (action) {
            ACTION_WIDGET_PLAY_PAUSE -> {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_IDLE) {
                        player.prepare()
                    }
                    player.play()
                }
            }
            ACTION_WIDGET_NEXT -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else {
                    player.seekTo(0, 0L)
                }
                player.prepare()
                player.play()
            }
            ACTION_WIDGET_PREVIOUS -> {
                if (player.currentPosition > 4000L) {
                    player.seekTo(0L)
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    player.seekTo(0L)
                }
                player.prepare()
                player.play()
            }
        }
        pushWidgetUpdate()
    }

    private suspend fun restoreSavedQueueIntoPlayer(player: ExoPlayer) {
        val container = (application as? SonicBloomApplication)?.container ?: return
        val snapshot = container.preferencesRepository.getSavedPlaybackSession()
        if (snapshot.queueTrackIds.isEmpty()) return

        var tracks = container.musicLibraryRepository.allTracks.first()
        if (tracks.isEmpty()) {
            container.musicLibraryRepository.scanDeviceMediaStore()
            tracks = container.musicLibraryRepository.allTracks.first()
        }
        val byId = tracks.associateBy { it.id }
        val restoredQueue = snapshot.queueTrackIds.mapNotNull { byId[it] }
        if (restoredQueue.isEmpty()) return

        val safeIndex = snapshot.currentIndex.coerceIn(0, restoredQueue.lastIndex)
        val mediaItems = restoredQueue.map { track ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .apply {
                    track.albumArtUri?.let { setArtworkUri(Uri.parse(it)) }
                }
                .build()
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse(track.contentUri))
                .setMediaMetadata(metadata)
                .build()
        }
        player.setMediaItems(mediaItems, safeIndex, snapshot.positionMs)
        player.prepare()
    }

    private fun pushWidgetUpdate() {
        val player = activePlayer ?: return
        val metadata = player.currentMediaItem?.mediaMetadata
        SonicBloomWidgetProvider.updateWidgets(
            context = this,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            artUri = metadata?.artworkUri?.toString(),
            isPlaying = player.isPlaying
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        crossfadeMonitorJob?.cancel()
        activeCrossfadeJob?.cancel()
        serviceScope.cancel()
        (application as? SonicBloomApplication)?.container?.audioEffectManager?.releaseEffects()
        mediaSession?.run {
            player.removeListener(audioSessionListener)
            release()
        }
        activePlayer?.release()
        stagingPlayer?.release()
        mediaSession = null
        activePlayer = null
        stagingPlayer = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE = "com.example.widget.ACTION_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.example.widget.ACTION_NEXT"
        const val ACTION_WIDGET_PREVIOUS = "com.example.widget.ACTION_PREVIOUS"

        const val CUSTOM_CMD_TOGGLE_FAVORITE = "com.example.action.TOGGLE_FAVORITE"
        const val CUSTOM_CMD_CYCLE_REPEAT = "com.example.action.CYCLE_REPEAT"
    }
}
