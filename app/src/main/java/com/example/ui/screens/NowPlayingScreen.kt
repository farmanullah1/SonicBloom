package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.RepeatModeOption
import com.example.playback.PlaybackUiState
import com.example.ui.components.StudioErrorBanner
import com.example.ui.components.TrackArtwork
import com.example.ui.util.formatDurationMs
import kotlin.math.abs
import kotlin.math.sin

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
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val track = playbackState.currentTrack ?: return
    val duration = playbackState.durationMs.coerceAtLeast(1L)

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

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

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
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
                        color = MaterialTheme.colorScheme.secondary
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
                Box(
                    modifier = Modifier.size(artSize + 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val baseRadius = size.minDimension * 0.39f
                        val ringExpansion = if (playbackState.isPlaying) pulsePhase * 26f else 6f
                        drawCircle(
                            color = primaryColor.copy(alpha = if (playbackState.isPlaying) (1f - pulsePhase) * 0.35f else 0.14f),
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
                        color = MaterialTheme.colorScheme.secondary,
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
                WaveformBarPreview(
                    progress = displayedFraction,
                    seed = track.id.hashCode(),
                    activeColor = primaryColor,
                    inactiveColor = outlineColor
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
                        thumbColor = primaryColor,
                        activeTrackColor = primaryColor,
                        inactiveTrackColor = outlineColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("now_playing_seek_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDurationMs(displayedPositionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        tint = if (playbackState.shuffleEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
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
                        .background(primaryColor)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("now_playing_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.cd_play_pause),
                        tint = MaterialTheme.colorScheme.onPrimary,
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
                        else -> primaryColor
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
                            tint = if (track.isFavorite) primaryColor else MaterialTheme.colorScheme.onSurface
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
private fun WaveformBarPreview(
    progress: Float,
    seed: Int,
    activeColor: Color,
    inactiveColor: Color
) {
    val barCount = 44
    val amplitudes = remember(seed) {
        List(barCount) { idx ->
            val raw = abs(sin((idx * 0.48 + (seed % 17) * 0.35)))
            (0.25f + 0.75f * raw.toFloat()).coerceIn(0.2f, 1.0f)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 4.dp)
    ) {
        val totalBars = amplitudes.size
        val step = size.width / totalBars
        val barWidth = (step * 0.56f).coerceAtLeast(3f)
        amplitudes.forEachIndexed { idx, amp ->
            val fraction = idx.toFloat() / totalBars.toFloat()
            val barHeight = size.height * amp
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
