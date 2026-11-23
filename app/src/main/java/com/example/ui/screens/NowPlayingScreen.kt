package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.DynamicTrackColors
import com.example.data.model.RepeatModeOption
import com.example.data.model.TrackLyrics
import com.example.data.model.WaveformData
import com.example.playback.PlaybackUiState
import com.example.ui.components.StudioErrorBanner
import com.example.ui.components.TrackArtwork
import com.example.ui.util.LyricsParser
import com.example.ui.util.WaveformExtractor
import com.example.ui.util.formatDurationMs
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekRelative: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSpeedDialog: () -> Unit,
    onOpenQueue: () -> Unit,
    onMoreOptions: (AudioTrack) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
    dynamicColors: DynamicTrackColors = DynamicTrackColors(),
    trackLyrics: TrackLyrics? = null,
    onImportLyricsFile: () -> Unit = {},
    onClearLyrics: () -> Unit = {},
    waveform: WaveformData = WaveformData(),
    onAbLoopClick: () -> Unit = {},
    onClearAbLoop: () -> Unit = {}
) {
    BackHandler(onBack = onClose)

    val track = playbackState.currentTrack ?: return
    val duration = playbackState.durationMs.coerceAtLeast(1L)

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    var showLyrics by remember { mutableStateOf(false) }

    val liveFraction = (playbackState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    val displayedFraction = if (isScrubbing) scrubFraction else liveFraction
    val displayedPositionMs = (displayedFraction * duration).toLong()

    val pulseTransition = rememberInfiniteTransition(label = "bloom_pulse")
    val pulsePhase by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_phase"
    )

    val animatedAccent by animateColorAsState(
        targetValue = dynamicColors.accentColor ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(450),
        label = "now_playing_accent"
    )
    val animatedSurfaceTint by animateColorAsState(
        targetValue = dynamicColors.surfaceTint ?: MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(450),
        label = "now_playing_tint"
    )
    val onAccentColor = if (animatedAccent.luminance() > 0.45f) Color(0xFF101318) else Color(0xFFFDFCF9)
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val topBackdropColor = animatedSurfaceTint.copy(alpha = 0.52f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerLow)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        topBackdropColor,
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_collapse")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.now_playing_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = animatedAccent
                    )
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { showLyrics = !showLyrics },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_lyrics_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lyrics,
                        contentDescription = stringResource(R.string.cd_lyrics),
                        tint = if (showLyrics) animatedAccent else MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(
                    onClick = { onMoreOptions(track) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_more")
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (playbackState.errorMessage != null) {
                StudioErrorBanner(
                    message = playbackState.errorMessage,
                    actionLabel = if (playbackState.queue.size > 1) "Skip" else null,
                    onAction = if (playbackState.queue.size > 1) {
                        {
                            onClearError()
                            onSkipNext()
                        }
                    } else null,
                    onDismiss = onClearError
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val artSize = (maxWidth * 0.68f).coerceIn(190.dp, 290.dp)
                val stageSize = artSize + 36.dp

                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                    },
                    label = "now_playing_stage"
                ) { lyricsVisible ->
                    if (!lyricsVisible) {
                        Box(
                            modifier = Modifier.size(stageSize),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val baseRadius = size.minDimension * 0.39f
                                val ringExpansion = if (playbackState.isPlaying) pulsePhase * 26f else 6f
                                drawCircle(
                                    color = animatedAccent.copy(
                                        alpha = if (playbackState.isPlaying) (1f - pulsePhase) * 0.38f else 0.16f
                                    ),
                                    radius = baseRadius + ringExpansion,
                                    center = center,
                                    style = Stroke(width = 3f)
                                )
                                drawCircle(
                                    color = secondaryColor.copy(alpha = if (playbackState.isPlaying) 0.22f else 0.08f),
                                    radius = baseRadius + 12f,
                                    center = center,
                                    style = Stroke(width = 2f)
                                )
                            }
                            TrackArtwork(
                                albumArtUri = track.albumArtUri,
                                size = artSize,
                                shape = RoundedCornerShape(24.dp)
                            )
                        }
                    } else {
                        LyricsPanel(
                            trackLyrics = trackLyrics,
                            positionMs = displayedPositionMs,
                            accentColor = animatedAccent,
                            onSeekTo = onSeekTo,
                            onImportLyricsFile = onImportLyricsFile,
                            onClearLyrics = onClearLyrics,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(stageSize)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(1.dp, outlineColor, RoundedCornerShape(50))
                ) {
                    Text(
                        text = track.formatLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = animatedAccent,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                val loopStartFraction = playbackState.loopStartMs?.let {
                    (it.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                }
                val loopEndFraction = playbackState.loopEndMs?.let {
                    (it.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                }
                val resolvedAmplitudes = remember(track.id, waveform.trackId, waveform.amplitudes) {
                    if (waveform.trackId == track.id && waveform.amplitudes.isNotEmpty()) {
                        waveform.amplitudes
                    } else {
                        WaveformExtractor.fallbackAmplitudes(track.id)
                    }
                }

                InteractiveWaveformBar(
                    progress = displayedFraction,
                    trackKey = track.id,
                    amplitudes = resolvedAmplitudes,
                    loopStartFraction = loopStartFraction,
                    loopEndFraction = loopEndFraction,
                    activeColor = animatedAccent,
                    inactiveColor = outlineColor,
                    loopColor = secondaryColor,
                    onScrubStartOrMove = { fraction ->
                        isScrubbing = true
                        scrubFraction = fraction
                    },
                    onScrubEnd = { fraction ->
                        isScrubbing = false
                        onSeekTo((fraction * duration).toLong())
                    }
                )

                Slider(
                    value = displayedFraction,
                    onValueChange = {
                        isScrubbing = true
                        scrubFraction = it
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        onSeekTo((scrubFraction * duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = animatedAccent,
                        activeTrackColor = animatedAccent,
                        inactiveTrackColor = outlineColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("now_playing_seek_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDurationMs(displayedPositionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val abLabel = when {
                            playbackState.loopStartMs != null && playbackState.loopEndMs != null ->
                                "A ${formatDurationMs(playbackState.loopStartMs)} – B ${formatDurationMs(playbackState.loopEndMs)}"
                            playbackState.loopStartMs != null ->
                                "A ${formatDurationMs(playbackState.loopStartMs)} • ${stringResource(R.string.ab_loop_set_b)}"
                            else -> stringResource(R.string.ab_loop_idle)
                        }
                        val abActive = playbackState.loopStartMs != null
                        Surface(
                            color = if (abActive) animatedAccent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (abActive) animatedAccent else outlineColor,
                                    RoundedCornerShape(50)
                                )
                                .clip(RoundedCornerShape(50))
                                .clickable(onClick = onAbLoopClick)
                                .testTag("now_playing_ab_loop_button")
                        ) {
                            Text(
                                text = abLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (abActive) animatedAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        if (abActive) {
                            IconButton(
                                onClick = onClearAbLoop,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("now_playing_ab_clear_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear A-B loop",
                                    tint = animatedAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = formatDurationMs(playbackState.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_shuffle")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = stringResource(R.string.cd_shuffle),
                        tint = if (playbackState.shuffleEnabled) animatedAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_previous")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous_track),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = { onSeekRelative(-10_000L) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_rewind_10")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay10,
                        contentDescription = stringResource(R.string.cd_rewind_10),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(animatedAccent)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("now_playing_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = onAccentColor,
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = { onSeekRelative(10_000L) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_forward_10")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forward10,
                        contentDescription = stringResource(R.string.cd_forward_10),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_next")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = stringResource(R.string.cd_next_track),
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(30.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("now_playing_repeat")
                ) {
                    val icon = when (playbackState.repeatMode) {
                        RepeatModeOption.ONE -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    }
                    val tint = when (playbackState.repeatMode) {
                        RepeatModeOption.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> animatedAccent
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.cd_repeat),
                        tint = tint
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, outlineColor, RoundedCornerShape(18.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onOpenSpeedDialog,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("now_playing_speed")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = "Playback speed",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "${playbackState.playbackSpeed}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("now_playing_eq")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Equalizer,
                            contentDescription = stringResource(R.string.cd_equalizer),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onOpenSleepTimer,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("now_playing_sleep")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = stringResource(R.string.cd_sleep_timer),
                            tint = if (playbackState.sleepTimer.isActive) secondaryColor else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { onToggleFavorite(track) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("now_playing_favorite")
                    ) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint = if (track.isFavorite) animatedAccent else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("now_playing_queue")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = stringResource(R.string.cd_queue),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsPanel(
    trackLyrics: TrackLyrics?,
    positionMs: Long,
    accentColor: Color,
    onSeekTo: (Long) -> Unit,
    onImportLyricsFile: () -> Unit,
    onClearLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val lines = trackLyrics?.lines.orEmpty()
    val activeIdx = remember(lines, positionMs) {
        LyricsParser.findActiveLineIndex(lines, positionMs)
    }

    LaunchedEffect(activeIdx) {
        if (activeIdx >= 0 && activeIdx < lines.size) {
            listState.animateScrollToItem((activeIdx - 1).coerceAtLeast(0))
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .testTag("now_playing_lyrics_panel")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (trackLyrics != null) {
                        Surface(
                            color = accentColor.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = trackLyrics.sourceLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (trackLyrics?.sourceLabel == "Imported .lrc") {
                        TextButton(
                            onClick = onClearLyrics,
                            modifier = Modifier.testTag("lyrics_clear_button")
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(
                        onClick = onImportLyricsFile,
                        modifier = Modifier.testTag("lyrics_import_button")
                    ) {
                        Icon(
                            Icons.Filled.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = accentColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import .lrc", color = accentColor)
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            if (lines.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.lyrics_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    FilledTonalButton(
                        onClick = onImportLyricsFile,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("lyrics_empty_import_button")
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.lyrics_import_lrc))
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(lines) { index, line ->
                        val isActive = index == activeIdx
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) {
                                accentColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = tween(220),
                            label = "lyric_line_color"
                        )
                        Text(
                            text = line.text,
                            style = if (isActive) {
                                MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (line.timestampMs != null) {
                                        Modifier.clickable { onSeekTo(line.timestampMs) }
                                    } else Modifier
                                )
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveWaveformBar(
    progress: Float,
    trackKey: String,
    amplitudes: List<Float>,
    loopStartFraction: Float?,
    loopEndFraction: Float?,
    activeColor: Color,
    inactiveColor: Color,
    loopColor: Color,
    onScrubStartOrMove: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val latestStartOrMove by rememberUpdatedState(onScrubStartOrMove)
    val latestEnd by rememberUpdatedState(onScrubEnd)
    var latestDragFraction by remember { mutableFloatStateOf(progress) }
    var lastHapticBarIndex by remember(trackKey) { mutableIntStateOf(-1) }
    val barCount = amplitudes.size.coerceAtLeast(1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 4.dp)
            .pointerInput(trackKey) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val fraction = (offset.x / width).coerceIn(0f, 1f)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    latestEnd(fraction)
                }
            }
            .pointerInput(trackKey, barCount) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = (offset.x / width).coerceIn(0f, 1f)
                        latestDragFraction = fraction
                        lastHapticBarIndex = (fraction * barCount).roundToInt()
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        latestStartOrMove(fraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = (change.position.x / width).coerceIn(0f, 1f)
                        latestDragFraction = fraction
                        val currentBarIdx = (fraction * barCount).roundToInt()
                        if (currentBarIdx != lastHapticBarIndex) {
                            lastHapticBarIndex = currentBarIdx
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        latestStartOrMove(fraction)
                    },
                    onDragEnd = {
                        lastHapticBarIndex = -1
                        latestEnd(latestDragFraction)
                    },
                    onDragCancel = {
                        lastHapticBarIndex = -1
                        latestEnd(latestDragFraction)
                    }
                )
            }
            .testTag("now_playing_waveform")
    ) {
        if (loopStartFraction != null) {
            val startX = loopStartFraction * size.width
            if (loopEndFraction != null && loopEndFraction > loopStartFraction) {
                val endX = loopEndFraction * size.width
                drawRoundRect(
                    color = loopColor.copy(alpha = 0.16f),
                    topLeft = Offset(startX, 2f),
                    size = Size((endX - startX).coerceAtLeast(4f), size.height - 4f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawLine(
                    color = loopColor,
                    start = Offset(endX, 0f),
                    end = Offset(endX, size.height),
                    strokeWidth = 3f
                )
            }
            drawLine(
                color = loopColor,
                start = Offset(startX, 0f),
                end = Offset(startX, size.height),
                strokeWidth = 3f
            )
        }

        val totalBars = amplitudes.size
        val step = size.width / totalBars
        val barWidth = (step * 0.56f).coerceAtLeast(2.5f)
        amplitudes.forEachIndexed { idx, amp ->
            val fraction = idx.toFloat() / totalBars.toFloat()
            val barHeight = size.height * amp.coerceIn(0.14f, 1.0f)
            val top = (size.height - barHeight) / 2f
            drawRoundRect(
                color = if (fraction <= progress) activeColor else inactiveColor,
                topLeft = Offset(idx * step, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
