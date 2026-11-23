package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.BrowseTab
import com.example.ui.components.SonicBloomTopBar
import com.example.ui.components.StudioEmptyState
import com.example.ui.components.TrackArtwork
import com.example.ui.util.formatDurationMs
import com.example.ui.viewmodel.LibraryUiState

private sealed interface BrowseSelection {
    data class Album(val key: String) : BrowseSelection
    data class Artist(val name: String) : BrowseSelection
    data class Folder(val name: String) : BrowseSelection
}

@Composable
fun BrowseScreen(
    libraryState: LibraryUiState,
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onBrowseSearchChange: (String) -> Unit,
    onSelectTab: (BrowseTab) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentTrackId: String? = null,
    isPlaying: Boolean = false,
    onPlayTracks: (List<AudioTrack>, Int) -> Unit = { _, _ -> },
    onToggleFavorite: (AudioTrack) -> Unit = {},
    onTrackMoreClick: (AudioTrack, Long?) -> Unit = { _, _ -> },
    onSwipeQueueNext: ((AudioTrack) -> Unit)? = null,
    selectedTrackIds: Set<String> = emptySet(),
    onToggleSelectTrack: (String) -> Unit = {},
    onSelectAllTracks: (List<AudioTrack>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onBatchPlayNext: (List<AudioTrack>) -> Unit = {},
    onBatchAddToQueue: (List<AudioTrack>) -> Unit = {},
    onBatchOpenPlaylistPicker: () -> Unit = {}
) {
    val activeSubtitle = "${libraryState.albums.size} albums • ${libraryState.artists.size} artists • ${libraryState.folders.size} folders"
    var paneSelection by remember { mutableStateOf<BrowseSelection?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useTwoPane = maxWidth >= 840.dp

        val effectiveSelection: BrowseSelection? = if (useTwoPane) {
            paneSelection ?: when (libraryState.browseTab) {
                BrowseTab.ALBUMS -> libraryState.albums.firstOrNull()?.let { BrowseSelection.Album(it.key) }
                BrowseTab.ARTISTS -> libraryState.artists.firstOrNull()?.let { BrowseSelection.Artist(it.name) }
                BrowseTab.FOLDERS -> libraryState.folders.firstOrNull()?.let { BrowseSelection.Folder(it.name) }
            }
        } else null

        val listPaneContent: @Composable (Modifier) -> Unit = { paneModifier ->
            Column(modifier = paneModifier) {
                SonicBloomTopBar(
                    title = stringResource(R.string.nav_browse),
                    subtitle = activeSubtitle,
                    themeMode = libraryState.themeMode,
                    onCycleTheme = onCycleTheme,
                    onOpenSettings = onOpenSettings
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = libraryState.browseSearchQuery,
                    onValueChange = onBrowseSearchChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_browse_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (libraryState.browseSearchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onBrowseSearchChange("") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear filter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("browse_search_field")
                )

                Spacer(modifier = Modifier.height(12.dp))

                val tabs = listOf(
                    BrowseTab.ALBUMS to "${stringResource(R.string.browse_tab_albums)} (${libraryState.albums.size})",
                    BrowseTab.ARTISTS to "${stringResource(R.string.browse_tab_artists)} (${libraryState.artists.size})",
                    BrowseTab.FOLDERS to "${stringResource(R.string.browse_tab_folders)} (${libraryState.folders.size})"
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    tabs.forEachIndexed { index, (tab, label) ->
                        SegmentedButton(
                            selected = libraryState.browseTab == tab,
                            onClick = { onSelectTab(tab) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                inactiveContentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.testTag("browse_tab_${tab.name}")
                        ) {
                            Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (libraryState.browseTab) {
                    BrowseTab.ALBUMS -> {
                        if (libraryState.albums.isEmpty()) {
                            StudioEmptyState(
                                icon = {
                                    Icon(
                                        Icons.Filled.Album,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                title = "No matching albums",
                                subtitle = if (libraryState.browseSearchQuery.isNotBlank()) {
                                    stringResource(R.string.empty_search_subtitle)
                                } else {
                                    stringResource(R.string.empty_library_subtitle)
                                }
                            )
                        } else {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val columns = if (!useTwoPane && maxWidth >= 600.dp) 3 else 2
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(columns),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(libraryState.albums, key = { it.key }) { album ->
                                        val isPaneSelected = (effectiveSelection as? BrowseSelection.Album)?.key == album.key
                                        Surface(
                                            color = if (isPaneSelected) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            },
                                            contentColor = MaterialTheme.colorScheme.onSurface,
                                            shape = RoundedCornerShape(18.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(
                                                    1.dp,
                                                    if (isPaneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                    RoundedCornerShape(18.dp)
                                                )
                                                .clip(RoundedCornerShape(18.dp))
                                                .clickable {
                                                    if (useTwoPane) {
                                                        paneSelection = BrowseSelection.Album(album.key)
                                                    } else {
                                                        onOpenAlbum(album.key)
                                                    }
                                                }
                                                .testTag("album_card_${album.title}")
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(1f)
                                                ) {
                                                    TrackArtwork(
                                                        albumArtUri = album.albumArtUri,
                                                        size = 220.dp,
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = album.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${album.artist} • ${album.trackCount} tracks",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    BrowseTab.ARTISTS -> {
                        if (libraryState.artists.isEmpty()) {
                            StudioEmptyState(
                                icon = {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                title = "No matching artists",
                                subtitle = if (libraryState.browseSearchQuery.isNotBlank()) {
                                    stringResource(R.string.empty_search_subtitle)
                                } else {
                                    stringResource(R.string.empty_library_subtitle)
                                }
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(libraryState.artists, key = { it.name }) { artist ->
                                    val isPaneSelected = (effectiveSelection as? BrowseSelection.Artist)?.name == artist.name
                                    Surface(
                                        color = if (isPaneSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                1.dp,
                                                if (isPaneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                if (useTwoPane) {
                                                    paneSelection = BrowseSelection.Artist(artist.name)
                                                } else {
                                                    onOpenArtist(artist.name)
                                                }
                                            }
                                            .testTag("artist_card_${artist.name}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TrackArtwork(
                                                albumArtUri = artist.coverArtUri,
                                                size = 52.dp,
                                                shape = CircleShape
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = artist.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${artist.albumCount} albums • ${artist.trackCount} tracks",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    BrowseTab.FOLDERS -> {
                        if (libraryState.folders.isEmpty()) {
                            StudioEmptyState(
                                icon = {
                                    Icon(
                                        Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                title = "No matching folders",
                                subtitle = if (libraryState.browseSearchQuery.isNotBlank()) {
                                    stringResource(R.string.empty_search_subtitle)
                                } else {
                                    stringResource(R.string.empty_library_subtitle)
                                }
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(libraryState.folders, key = { it.name }) { folder ->
                                    val isPaneSelected = (effectiveSelection as? BrowseSelection.Folder)?.name == folder.name
                                    Surface(
                                        color = if (isPaneSelected) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                1.dp,
                                                if (isPaneSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable {
                                                if (useTwoPane) {
                                                    paneSelection = BrowseSelection.Folder(folder.name)
                                                } else {
                                                    onOpenFolder(folder.name)
                                                }
                                            }
                                            .testTag("folder_card_${folder.name}")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.FolderOpen,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = folder.name,
                                                    style = MaterialTheme.typography.titleMedium,
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
                    }
                }
            }
        }

        if (!useTwoPane) {
            listPaneContent(Modifier.fillMaxSize())
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                listPaneContent(
                    Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    thickness = 1.dp
                )
                Box(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .testTag("browse_detail_pane"),
                    contentAlignment = Alignment.Center
                ) {
                    when (effectiveSelection) {
                        is BrowseSelection.Album -> {
                            val tracks = libraryState.allTracks.filter { "${it.album}||${it.artist}" == effectiveSelection.key }
                            val album = libraryState.albums.find { it.key == effectiveSelection.key }
                            CollectionDetailScreen(
                                title = album?.title ?: "Album",
                                subtitle = album?.artist ?: "Unknown Artist",
                                coverArtUri = album?.albumArtUri,
                                tracks = tracks,
                                currentTrackId = currentTrackId,
                                isPlaying = isPlaying,
                                showBackButton = false,
                                onNavigateBack = {},
                                onPlayTracks = onPlayTracks,
                                onToggleFavorite = onToggleFavorite,
                                onTrackMoreClick = onTrackMoreClick,
                                selectedTrackIds = selectedTrackIds,
                                onToggleSelectTrack = onToggleSelectTrack,
                                onSelectAllTracks = onSelectAllTracks,
                                onClearSelection = onClearSelection,
                                onBatchPlayNext = onBatchPlayNext,
                                onBatchAddToQueue = onBatchAddToQueue,
                                onBatchOpenPlaylistPicker = onBatchOpenPlaylistPicker,
                                onSwipeQueueNext = onSwipeQueueNext,
                                onSwipeToggleFavorite = onToggleFavorite
                            )
                        }
                        is BrowseSelection.Artist -> {
                            val tracks = libraryState.allTracks.filter { it.artist == effectiveSelection.name }
                            CollectionDetailScreen(
                                title = effectiveSelection.name,
                                subtitle = "Artist Collection",
                                coverArtUri = tracks.firstNotNullOfOrNull { it.albumArtUri },
                                tracks = tracks,
                                currentTrackId = currentTrackId,
                                isPlaying = isPlaying,
                                showBackButton = false,
                                onNavigateBack = {},
                                onPlayTracks = onPlayTracks,
                                onToggleFavorite = onToggleFavorite,
                                onTrackMoreClick = onTrackMoreClick,
                                selectedTrackIds = selectedTrackIds,
                                onToggleSelectTrack = onToggleSelectTrack,
                                onSelectAllTracks = onSelectAllTracks,
                                onClearSelection = onClearSelection,
                                onBatchPlayNext = onBatchPlayNext,
                                onBatchAddToQueue = onBatchAddToQueue,
                                onBatchOpenPlaylistPicker = onBatchOpenPlaylistPicker,
                                onSwipeQueueNext = onSwipeQueueNext,
                                onSwipeToggleFavorite = onToggleFavorite
                            )
                        }
                        is BrowseSelection.Folder -> {
                            val tracks = libraryState.allTracks.filter { it.folderName == effectiveSelection.name }
                            CollectionDetailScreen(
                                title = effectiveSelection.name,
                                subtitle = "Device Folder",
                                coverArtUri = tracks.firstNotNullOfOrNull { it.albumArtUri },
                                tracks = tracks,
                                currentTrackId = currentTrackId,
                                isPlaying = isPlaying,
                                showBackButton = false,
                                onNavigateBack = {},
                                onPlayTracks = onPlayTracks,
                                onToggleFavorite = onToggleFavorite,
                                onTrackMoreClick = onTrackMoreClick,
                                selectedTrackIds = selectedTrackIds,
                                onToggleSelectTrack = onToggleSelectTrack,
                                onSelectAllTracks = onSelectAllTracks,
                                onClearSelection = onClearSelection,
                                onBatchPlayNext = onBatchPlayNext,
                                onBatchAddToQueue = onBatchAddToQueue,
                                onBatchOpenPlaylistPicker = onBatchOpenPlaylistPicker,
                                onSwipeQueueNext = onSwipeQueueNext,
                                onSwipeToggleFavorite = onToggleFavorite
                            )
                        }
                        null -> {
                            StudioEmptyState(
                                icon = {
                                    Icon(
                                        Icons.Filled.LibraryMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                title = "Select an album, artist, or folder",
                                subtitle = stringResource(R.string.empty_library_subtitle)
                            )
                        }
                    }
                }
            }
        }
    }
}
