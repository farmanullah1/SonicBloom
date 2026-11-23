package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.AudioTrack
import com.example.ui.util.formatDurationMs

@Composable
fun TrackArtwork(
    albumArtUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    contentDescription: String = stringResource(R.string.cd_album_art)
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(albumArtUri)
                .crossfade(true)
                .build(),
            placeholder = painterResource(R.drawable.img_vinyl_bloom_art),
            error = painterResource(R.drawable.img_vinyl_bloom_art),
            fallback = painterResource(R.drawable.img_vinyl_bloom_art),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun PlayingWaveformIndicator(
    isPlaying: Boolean,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eq_bars")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Canvas(modifier = modifier.size(18.dp)) {
        val barWidth = size.width / 5f
        val gap = barWidth
        val heights = if (isPlaying) {
            listOf(bar1, bar2, bar3)
        } else {
            listOf(0.45f, 0.7f, 0.35f)
        }
        heights.forEachIndexed { idx, fraction ->
            val h = size.height * fraction
            val left = idx * (barWidth + gap)
            val top = size.height - h
            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TrackRowItem(
    track: AudioTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: (() -> Unit)? = null,
    onSwipeQueueNext: (() -> Unit)? = null,
    onSwipeSecondaryAction: (() -> Unit)? = null,
    swipeSecondaryIsRemove: Boolean = false,
    dragHandle: (@Composable () -> Unit)? = null
) {
    val latestSwipeQueue by rememberUpdatedState(onSwipeQueueNext)
    val latestSwipeSecondary by rememberUpdatedState(onSwipeSecondaryAction)

    val rowCard: @Composable () -> Unit = {
        val targetContainer = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            isCurrentTrack -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            else -> MaterialTheme.colorScheme.surface
        }
        val targetBorder = when {
            isSelected -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
            isCurrentTrack -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        }
        val containerColor by animateColorAsState(targetContainer, tween(220), label = "row_bg")
        val borderColor by animateColorAsState(targetBorder, tween(220), label = "row_border")

        Surface(
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress
                )
                .testTag("track_row_${track.id}")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Selected" else "Not selected",
                        tint = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(22.dp)
                    )
                }

                Box(contentAlignment = Alignment.Center) {
                    TrackArtwork(
                        albumArtUri = track.albumArtUri,
                        size = 48.dp,
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (isCurrentTrack && !selectionMode) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.58f)),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayingWaveformIndicator(
                                isPlaying = isPlaying,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold
                        ),
                        color = if (isCurrentTrack) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatDurationMs(track.durationMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (dragHandle != null && !selectionMode) {
                    dragHandle()
                }

                if (!selectionMode) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("favorite_button_${track.id}")
                    ) {
                        Icon(
                            imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint = if (track.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("more_button_${track.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.cd_more_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    val haptics = LocalHapticFeedback.current
    val swipeEnabled = !selectionMode && (onSwipeQueueNext != null || onSwipeSecondaryAction != null)
    if (!swipeEnabled) {
        rowCard()
    } else {
        var lastHapticTarget by remember { mutableStateOf(SwipeToDismissBoxValue.Settled) }
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestSwipeQueue?.invoke()
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        latestSwipeSecondary?.invoke()
                    }
                    SwipeToDismissBoxValue.Settled -> Unit
                }
                false
            }
        )

        LaunchedEffect(dismissState.targetValue) {
            val target = dismissState.targetValue
            if (target != SwipeToDismissBoxValue.Settled && target != lastHapticTarget) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastHapticTarget = target
        }

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = onSwipeQueueNext != null,
            enableDismissFromEndToStart = onSwipeSecondaryAction != null,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val isStart = direction == SwipeToDismissBoxValue.StartToEnd
                val bgColor = when {
                    isStart -> MaterialTheme.colorScheme.secondaryContainer
                    swipeSecondaryIsRemove -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                val contentColor = when {
                    isStart -> MaterialTheme.colorScheme.onSecondaryContainer
                    swipeSecondaryIsRemove -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = if (isStart) Arrangement.Start else Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isStart) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = contentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_swipe_queue),
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor
                        )
                    } else if (direction == SwipeToDismissBoxValue.EndToStart) {
                        Text(
                            text = if (swipeSecondaryIsRemove) {
                                stringResource(R.string.action_swipe_remove)
                            } else {
                                stringResource(R.string.action_swipe_like)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = if (swipeSecondaryIsRemove) {
                                Icons.Filled.DeleteOutline
                            } else {
                                Icons.Filled.Favorite
                            },
                            contentDescription = null,
                            tint = contentColor
                        )
                    }
                }
            },
            content = { rowCard() }
        )
    }
}

@Composable
fun BatchSelectionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .testTag("batch_selection_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onClearSelection,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("batch_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "$selectedCount selected",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onSelectAll,
                    modifier = Modifier.testTag("batch_select_all_button")
                ) {
                    Icon(Icons.Filled.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_select_all))
                }
                IconButton(
                    onClick = onPlayNext,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("batch_play_next_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                        contentDescription = stringResource(R.string.action_play_next),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onAddToQueue,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("batch_add_queue_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = stringResource(R.string.action_add_to_queue),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("batch_add_playlist_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = stringResource(R.string.action_add_to_playlist),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                if (onRemoveFromPlaylist != null) {
                    IconButton(
                        onClick = onRemoveFromPlaylist,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("batch_remove_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.action_remove_from_playlist),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudioEmptyState(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(18.dp))
            action()
        }
    }
}
