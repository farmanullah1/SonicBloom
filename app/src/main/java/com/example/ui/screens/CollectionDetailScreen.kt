package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.ui.components.BatchSelectionBar
import com.example.ui.components.StudioEmptyState
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TrackRowItem
import com.example.ui.util.formatDurationMs
import kotlin.math.roundToInt

@Composable
fun CollectionDetailScreen(
    title: String,
    subtitle: String,
    coverArtUri: String?,
    tracks: List<AudioTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    playlistContextId: Long? = null,
    onNavigateBack: () -> Unit,
    onOpenBatchTrackPicker: (() -> Unit)? = null,
    onPlayTracks: (List<AudioTrack>, Int) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onTrackMoreClick: (AudioTrack, Long?) -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    selectedTrackIds: Set<String> = emptySet(),
    onToggleSelectTrack: (String) -> Unit = {},
    onSelectAllTracks: (List<AudioTrack>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onBatchPlayNext: (List<AudioTrack>) -> Unit = {},
    onBatchAddToQueue: (List<AudioTrack>) -> Unit = {},
    onBatchOpenPlaylistPicker: () -> Unit = {},
    onBatchRemoveFromPlaylist: ((Long) -> Unit)? = null,
    onSwipeQueueNext: ((AudioTrack) -> Unit)? = null,
    onSwipeToggleFavorite: ((AudioTrack) -> Unit)? = null,
    onRemoveTrackFromPlaylist: ((Long, String) -> Unit)? = null,
    onReorderPlaylistTracks: ((Long, Int, Int) -> Unit)? = null,
    onExportM3u: (() -> Unit)? = null
) {
    val totalDurationMs = tracks.sumOf { it.durationMs }
    var multiSelectToggle by remember(title, playlistContextId) { mutableStateOf(false) }
    val isSelectionMode = selectedTrackIds.isNotEmpty() || multiSelectToggle

    val listState = rememberLazyListState()
    val isCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 140
        }
    }
    val haptic = LocalHapticFeedback.current

    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var lastHapticStep by remember { mutableIntStateOf(0) }
    val rowStepPx = with(LocalDensity.current) { 74.dp.toPx() }
    val latestTrackCount by rememberUpdatedState(tracks.size)
    val latestReorder by rememberUpdatedState(onReorderPlaylistTracks)

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            AnimatedVisibility(visible = isCollapsed && tracks.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrackArtwork(
                        albumArtUri = coverArtUri ?: tracks.firstNotNullOfOrNull { it.albumArtUri },
                        size = 30.dp,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            AnimatedVisibility(visible = isCollapsed && tracks.isNotEmpty()) {
                IconButton(
                    onClick = { onPlayTracks(tracks, 0) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_collapsed_play")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.action_play_all),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (tracks.isNotEmpty() && onExportM3u != null) {
                IconButton(
                    onClick = onExportM3u,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_export_m3u_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.action_export_m3u),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (tracks.isNotEmpty()) {
                IconButton(
                    onClick = {
                        if (isSelectionMode) {
                            multiSelectToggle = false
                            onClearSelection()
                        } else {
                            multiSelectToggle = true
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_select_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Checklist,
                        contentDescription = stringResource(R.string.action_select_tracks),
                        tint = if (isSelectionMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if (playlistContextId != null && onOpenBatchTrackPicker != null) {
                IconButton(
                    onClick = onOpenBatchTrackPicker,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("detail_add_tracks_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.action_add_tracks),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            thickness = 1.dp
        )

        AnimatedVisibility(visible = isSelectionMode && tracks.isNotEmpty()) {
            BatchSelectionBar(
                selectedCount = selectedTrackIds.size,
                onSelectAll = { onSelectAllTracks(tracks) },
                onPlayNext = {
                    multiSelectToggle = false
                    onBatchPlayNext(tracks)
                },
                onAddToQueue = {
                    multiSelectToggle = false
                    onBatchAddToQueue(tracks)
                },
                onAddToPlaylist = onBatchOpenPlaylistPicker,
                onRemoveFromPlaylist = if (playlistContextId != null && onBatchRemoveFromPlaylist != null) {
                    {
                        multiSelectToggle = false
                        onBatchRemoveFromPlaylist(playlistContextId)
                    }
                } else null,
                onClearSelection = {
                    multiSelectToggle = false
                    onClearSelection()
                }
            )
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                StudioEmptyState(
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = "No tracks in this collection",
                    subtitle = "Add tracks from your library to start listening here.",
                    action = if (playlistContextId != null && onOpenBatchTrackPicker != null) {
                        {
                            FilledTonalButton(
                                onClick = onOpenBatchTrackPicker,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("empty_detail_add_tracks")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_add_tracks))
                            }
                        }
                    } else null
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackArtwork(
                            albumArtUri = coverArtUri ?: tracks.firstNotNullOfOrNull { it.albumArtUri },
                            size = 96.dp,
                            shape = RoundedCornerShape(18.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${tracks.size} tracks • ${formatDurationMs(totalDurationMs)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onPlayTracks(tracks, 0) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("detail_play_all")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_play_all), maxLines = 1)
                        }
                        FilledTonalButton(
                            onClick = { onPlayTracks(tracks, tracks.indices.random()) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("detail_shuffle_all")
                        ) {
                            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.action_shuffle_all), maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }

                itemsIndexed(tracks, key = { idx, item -> "${idx}_${item.id}" }) { index, track ->
                    val isDragging = index == draggedIndex
                    val latestIndex by rememberUpdatedState(index)
                    val secondaryAction: (() -> Unit)? = if (playlistContextId != null && onRemoveTrackFromPlaylist != null) {
                        { onRemoveTrackFromPlaylist(playlistContextId, track.id) }
                    } else if (onSwipeToggleFavorite != null) {
                        { onSwipeToggleFavorite(track) }
                    } else null

                    val handleComposable: (@Composable () -> Unit)? =
                        if (playlistContextId != null && onReorderPlaylistTracks != null && tracks.size > 1) {
                            {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .pointerInput(track.id, playlistContextId) {
                                            detectVerticalDragGestures(
                                                onDragStart = {
                                                    draggedIndex = latestIndex
                                                    dragOffsetY = 0f
                                                    lastHapticStep = 0
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount
                                                    val currentStep = (dragOffsetY / rowStepPx).roundToInt()
                                                    if (currentStep != lastHapticStep) {
                                                        lastHapticStep = currentStep
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                },
                                                onDragEnd = {
                                                    val currentIdx = draggedIndex
                                                    if (currentIdx >= 0 && latestTrackCount > 1) {
                                                        val shift = (dragOffsetY / rowStepPx).roundToInt()
                                                        val targetIdx = (currentIdx + shift).coerceIn(0, latestTrackCount - 1)
                                                        if (targetIdx != currentIdx) {
                                                            latestReorder?.invoke(playlistContextId, currentIdx, targetIdx)
                                                        }
                                                    }
                                                    draggedIndex = -1
                                                    dragOffsetY = 0f
                                                    lastHapticStep = 0
                                                },
                                                onDragCancel = {
                                                    draggedIndex = -1
                                                    dragOffsetY = 0f
                                                    lastHapticStep = 0
                                                }
                                            )
                                        }
                                        .testTag("playlist_drag_handle_${track.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = "Reorder track",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else null

                    TrackRowItem(
                        track = track,
                        isCurrentTrack = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelectTrack(track.id)
                            } else {
                                onPlayTracks(tracks, index)
                            }
                        },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onMoreClick = { onTrackMoreClick(track, playlistContextId) },
                        modifier = Modifier.graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            scaleX = if (isDragging) 1.02f else 1f
                            scaleY = if (isDragging) 1.02f else 1f
                        },
                        selectionMode = isSelectionMode,
                        isSelected = selectedTrackIds.contains(track.id),
                        onLongPress = {
                            multiSelectToggle = true
                            onToggleSelectTrack(track.id)
                        },
                        onSwipeQueueNext = onSwipeQueueNext?.let { fn -> { fn(track) } },
                        onSwipeSecondaryAction = secondaryAction,
                        swipeSecondaryIsRemove = playlistContextId != null && onRemoveTrackFromPlaylist != null,
                        dragHandle = handleComposable
                    )
                }
            }
        }
    }
}
