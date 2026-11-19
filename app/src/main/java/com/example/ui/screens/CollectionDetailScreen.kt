package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.ui.components.StudioEmptyState
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TrackRowItem
import com.example.ui.util.formatDurationMs

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
    modifier: Modifier = Modifier
) {
    val totalDurationMs = tracks.sumOf { it.durationMs }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
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
                    TrackRowItem(
                        track = track,
                        isCurrentTrack = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        onClick = { onPlayTracks(tracks, index) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onMoreClick = { onTrackMoreClick(track, playlistContextId) }
                    )
                }
            }
        }
    }
}
