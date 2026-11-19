package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PlaylistInfo
import com.example.data.model.SmartPlaylistType
import com.example.ui.components.PlaylistNameDialog
import com.example.ui.components.SonicBloomTopBar
import com.example.ui.components.StudioEmptyState
import com.example.ui.components.TrackArtwork
import com.example.ui.viewmodel.LibraryUiState

@Composable
fun PlaylistsScreen(
    libraryState: LibraryUiState,
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSmartPlaylist: (SmartPlaylistType) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    isDuplicatePlaylistName: (String, Long?) -> Boolean,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<PlaylistInfo?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        SonicBloomTopBar(
            title = stringResource(R.string.nav_playlists),
            subtitle = "${libraryState.playlists.size} custom • 3 smart mixes",
            themeMode = libraryState.themeMode,
            onCycleTheme = onCycleTheme,
            onOpenSettings = onOpenSettings
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Smart Mixes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmartPlaylistCard(
                        title = stringResource(R.string.playlist_liked_songs),
                        trackCount = libraryState.favoriteTracks.size,
                        icon = Icons.Filled.Favorite,
                        accentColor = MaterialTheme.colorScheme.primary,
                        testTag = "smart_playlist_liked",
                        onClick = { onOpenSmartPlaylist(SmartPlaylistType.LIKED) }
                    )
                    SmartPlaylistCard(
                        title = stringResource(R.string.playlist_most_played),
                        trackCount = libraryState.mostPlayedTracks.size,
                        icon = Icons.Filled.TrendingUp,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        testTag = "smart_playlist_most_played",
                        onClick = { onOpenSmartPlaylist(SmartPlaylistType.MOST_PLAYED) }
                    )
                    SmartPlaylistCard(
                        title = stringResource(R.string.playlist_recently_added),
                        trackCount = libraryState.recentlyAddedTracks.size,
                        icon = Icons.Filled.History,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        testTag = "smart_playlist_recently_added",
                        onClick = { onOpenSmartPlaylist(SmartPlaylistType.RECENTLY_ADDED) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("create_playlist_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_create_playlist), maxLines = 1)
                    }
                }
            }

            if (libraryState.playlists.isEmpty()) {
                item {
                    StudioEmptyState(
                        icon = {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = stringResource(R.string.empty_playlists_title),
                        subtitle = stringResource(R.string.empty_playlists_subtitle),
                        action = {
                            FilledTonalButton(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.action_create_playlist))
                            }
                        }
                    )
                }
            } else {
                items(libraryState.playlists, key = { it.id }) { playlist ->
                    var menuExpanded by remember { mutableStateOf(false) }
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenPlaylist(playlist.id) }
                            .testTag("playlist_card_${playlist.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackArtwork(
                                albumArtUri = playlist.coverArtUri,
                                size = 52.dp,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(R.string.playlist_track_count, playlist.trackIds.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.cd_more_options),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.action_rename),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            renameTarget = playlist
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = stringResource(R.string.action_delete),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Filled.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            onDeletePlaylist(playlist.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = stringResource(R.string.action_create_playlist),
            isDuplicateName = { name -> isDuplicatePlaylistName(name, null) },
            onDismiss = { showCreateDialog = false },
            onConfirm = onCreatePlaylist
        )
    }

    renameTarget?.let { target ->
        PlaylistNameDialog(
            title = stringResource(R.string.action_rename),
            initialName = target.name,
            isDuplicateName = { name -> isDuplicatePlaylistName(name, target.id) },
            onDismiss = { renameTarget = null },
            onConfirm = { newName -> onRenamePlaylist(target.id, newName) }
        )
    }
}

@Composable
private fun SmartPlaylistCard(
    title: String,
    trackCount: Int,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp)
            )
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.playlist_track_count, trackCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
