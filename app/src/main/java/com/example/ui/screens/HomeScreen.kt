package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.SleepTimerState
import com.example.data.model.SmartPlaylistType
import com.example.ui.components.SonicBloomTopBar
import com.example.ui.components.StudioEmptyState
import com.example.ui.components.StudioErrorBanner
import com.example.ui.components.TrackArtwork
import com.example.ui.components.TrackRowItem
import com.example.ui.theme.AlabasterText
import com.example.ui.theme.BloomCoralDark
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SageTealDark
import com.example.ui.util.formatDurationMs
import com.example.ui.viewmodel.LibraryUiState

@Composable
fun HomeScreen(
    libraryState: LibraryUiState,
    currentTrackId: String?,
    isPlaying: Boolean,
    sleepTimer: SleepTimerState,
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onImportAudioFiles: () -> Unit,
    onRescanLibrary: () -> Unit,
    onDismissError: () -> Unit,
    onShuffleAll: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onPlayTracks: (List<AudioTrack>, Int) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onTrackMoreClick: (AudioTrack) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNavigateToTracks: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSmartPlaylist: (SmartPlaylistType) -> Unit = {},
    onSwipeQueueNext: ((AudioTrack) -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxSize()) {
        SonicBloomTopBar(
            title = stringResource(R.string.app_name),
            subtitle = if (libraryState.allTracks.isNotEmpty()) {
                stringResource(
                    R.string.home_library_stats,
                    libraryState.allTracks.size,
                    libraryState.albums.size
                )
            } else {
                stringResource(R.string.app_tagline)
            },
            themeMode = libraryState.themeMode,
            onCycleTheme = onCycleTheme,
            onOpenSettings = onOpenSettings
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (libraryState.libraryError != null) {
                item {
                    StudioErrorBanner(
                        message = libraryState.libraryError,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRescanLibrary,
                        onDismiss = onDismissError
                    )
                }
            }

            item {
                HeroStudioBanner(
                    trackCount = libraryState.allTracks.size,
                    albumCount = libraryState.albums.size,
                    onShuffleAll = onShuffleAll
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickStudioChip(
                        icon = Icons.Filled.Shuffle,
                        label = stringResource(R.string.action_shuffle_all),
                        accentColor = MaterialTheme.colorScheme.primary,
                        testTag = "home_chip_shuffle",
                        onClick = onShuffleAll
                    )
                    QuickStudioChip(
                        icon = Icons.Filled.AudioFile,
                        label = stringResource(R.string.action_import_audio_files),
                        accentColor = MaterialTheme.colorScheme.secondary,
                        testTag = "home_chip_import",
                        onClick = onImportAudioFiles
                    )
                    QuickStudioChip(
                        icon = Icons.Filled.Equalizer,
                        label = stringResource(R.string.action_equalizer),
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        testTag = "home_chip_eq",
                        onClick = onOpenEqualizer
                    )
                    QuickStudioChip(
                        icon = Icons.Filled.Timer,
                        label = if (sleepTimer.isActive) {
                            if (sleepTimer.stopAfterCurrentTrack) "End of Track"
                            else formatDurationMs(sleepTimer.remainingMs)
                        } else {
                            stringResource(R.string.action_sleep_timer)
                        },
                        accentColor = if (sleepTimer.isActive) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        testTag = "home_chip_sleep",
                        onClick = onOpenSleepTimer
                    )
                }
            }

            if (!libraryState.hasAudioPermission || libraryState.allTracks.isEmpty()) {
                item {
                    LibraryAccessCard(
                        hasPermission = libraryState.hasAudioPermission,
                        onGrantAccess = onRequestAudioPermission,
                        onOpenAppSettings = onOpenAppSettings,
                        onImportFiles = onImportAudioFiles
                    )
                }
            }

            if (libraryState.allTracks.isNotEmpty()) {
                item {
                    SectionHeader(title = "Smart Auto-Mixes")
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "mix_lossless") {
                            HomeMixCard(
                                title = stringResource(R.string.playlist_lossless_vault),
                                count = libraryState.losslessVaultTracks.size,
                                icon = Icons.Filled.HighQuality,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTag = "home_mix_lossless",
                                onClick = { onOpenSmartPlaylist(SmartPlaylistType.LOSSLESS_VAULT) }
                            )
                        }
                        item(key = "mix_unplayed") {
                            HomeMixCard(
                                title = stringResource(R.string.playlist_unplayed_gems),
                                count = libraryState.unplayedGemsTracks.size,
                                icon = Icons.Filled.AutoAwesome,
                                accentColor = MaterialTheme.colorScheme.tertiary,
                                testTag = "home_mix_unplayed",
                                onClick = { onOpenSmartPlaylist(SmartPlaylistType.UNPLAYED_GEMS) }
                            )
                        }
                        item(key = "mix_most_played") {
                            HomeMixCard(
                                title = stringResource(R.string.playlist_most_played),
                                count = libraryState.mostPlayedTracks.size,
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                accentColor = MaterialTheme.colorScheme.primary,
                                testTag = "home_mix_most_played",
                                onClick = { onOpenSmartPlaylist(SmartPlaylistType.MOST_PLAYED) }
                            )
                        }
                        item(key = "mix_recent") {
                            HomeMixCard(
                                title = stringResource(R.string.playlist_recently_added),
                                count = libraryState.recentlyAddedTracks.size,
                                icon = Icons.Filled.History,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTag = "home_mix_recent",
                                onClick = { onOpenSmartPlaylist(SmartPlaylistType.RECENTLY_ADDED) }
                            )
                        }
                    }
                }
            }

            if (libraryState.recentlyPlayed.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.home_recently_played),
                        actionLabel = "See All",
                        onAction = onNavigateToTracks
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            libraryState.recentlyPlayed,
                            key = { _, item -> "recent_${item.id}" }
                        ) { index, track ->
                            RecentTrackCard(
                                track = track,
                                isCurrent = track.id == currentTrackId,
                                onClick = { onPlayTracks(libraryState.recentlyPlayed, index) }
                            )
                        }
                    }
                }
            }

            if (libraryState.favoriteTracks.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = stringResource(R.string.home_favorites_mix),
                        actionLabel = "${libraryState.favoriteTracks.size} tracks",
                        onAction = { onPlayTracks(libraryState.favoriteTracks, 0) }
                    )
                }
                itemsIndexed(
                    libraryState.favoriteTracks.take(5),
                    key = { _, item -> "fav_${item.id}" }
                ) { index, track ->
                    TrackRowItem(
                        track = track,
                        isCurrentTrack = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        onClick = { onPlayTracks(libraryState.favoriteTracks, index) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onMoreClick = { onTrackMoreClick(track) },
                        onSwipeQueueNext = onSwipeQueueNext?.let { fn -> { fn(track) } },
                        onSwipeSecondaryAction = { onToggleFavorite(track) }
                    )
                }
            }

            if (libraryState.folders.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.home_folders_overview))
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(libraryState.folders, key = { "folder_${it.name}" }) { folder ->
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .width(170.dp)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(18.dp)
                                    )
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { onOpenFolder(folder.name) }
                                    .testTag("home_folder_${folder.name}")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary
                                    )
                                    Text(
                                        text = folder.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${folder.trackCount} tracks • ${formatDurationMs(folder.totalDurationMs)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (libraryState.allTracks.isNotEmpty() && libraryState.recentlyPlayed.isEmpty() && libraryState.favoriteTracks.isEmpty()) {
                item {
                    SectionHeader(
                        title = "Ready to Play",
                        actionLabel = "All Tracks",
                        onAction = onNavigateToTracks
                    )
                }
                itemsIndexed(
                    libraryState.allTracks.take(8),
                    key = { _, item -> "starter_${item.id}" }
                ) { index, track ->
                    TrackRowItem(
                        track = track,
                        isCurrentTrack = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        onClick = { onPlayTracks(libraryState.allTracks, index) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onMoreClick = { onTrackMoreClick(track) },
                        onSwipeQueueNext = onSwipeQueueNext?.let { fn -> { fn(track) } },
                        onSwipeSecondaryAction = { onToggleFavorite(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMixCard(
    title: String,
    count: Int,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(172.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(18.dp)
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.playlist_track_count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HeroStudioBanner(
    trackCount: Int,
    albumCount: Int,
    onShuffleAll: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(188.dp)
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.img_sonicbloom_hero),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianNight.copy(alpha = 0.28f),
                            ObsidianNight.copy(alpha = 0.88f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ObsidianNight.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.border(
                        1.dp,
                        SageTealDark.copy(alpha = 0.45f),
                        RoundedCornerShape(50)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = SageTealDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.home_hero_eyebrow),
                            style = MaterialTheme.typography.labelSmall,
                            color = SageTealDark
                        )
                    }
                }

                if (trackCount > 0) {
                    Text(
                        text = stringResource(R.string.home_library_stats, trackCount, albumCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = AlabasterText
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = AlabasterText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.home_hero_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlabasterText.copy(alpha = 0.82f)
                    )
                }

                if (trackCount > 0) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(BloomCoralDark)
                            .clickable(onClick = onShuffleAll)
                            .testTag("hero_shuffle_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = stringResource(R.string.action_shuffle_all),
                            tint = ObsidianNight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStudioChip(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .height(48.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun LibraryAccessCard(
    hasPermission: Boolean,
    onGrantAccess: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onImportFiles: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StudioEmptyState(
                icon = {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                },
                title = if (!hasPermission) {
                    "Unlock your local MP3 library"
                } else {
                    stringResource(R.string.empty_library_title)
                },
                subtitle = stringResource(R.string.empty_library_subtitle),
                modifier = Modifier.padding(0.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!hasPermission) {
                    Button(
                        onClick = onGrantAccess,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("grant_audio_permission_button")
                    ) {
                        Text(stringResource(R.string.action_grant_audio_access), maxLines = 1)
                    }
                }
                OutlinedButton(
                    onClick = onImportFiles,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("import_audio_files_button")
                ) {
                    Text(stringResource(R.string.action_import_audio_files), maxLines = 1)
                }
            }
            if (!hasPermission) {
                TextButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("open_app_settings_button")
                ) {
                    Text(
                        text = "Permission blocked? Open ${stringResource(R.string.action_open_app_settings)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun RecentTrackCard(
    track: AudioTrack,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("recent_card_${track.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TrackArtwork(
            albumArtUri = track.albumArtUri,
            size = 128.dp,
            shape = RoundedCornerShape(16.dp)
        )
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
