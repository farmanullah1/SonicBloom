package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.TrackFilterOption
import com.example.data.model.TrackSortOption
import com.example.ui.components.BatchSelectionBar
import com.example.ui.components.SonicBloomTopBar
import com.example.ui.components.StudioEmptyState
import com.example.ui.components.StudioErrorBanner
import com.example.ui.components.TrackRowItem
import com.example.ui.viewmodel.LibraryUiState

@Composable
fun TracksScreen(
    libraryState: LibraryUiState,
    currentTrackId: String?,
    isPlaying: Boolean,
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFilterOptionChange: (TrackFilterOption) -> Unit,
    onSortOptionChange: (TrackSortOption) -> Unit,
    onToggleSortDirection: () -> Unit,
    onClearFilters: () -> Unit,
    onRescanLibrary: () -> Unit,
    onDismissError: () -> Unit,
    onImportAudioFiles: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onPlayTracks: (List<AudioTrack>, Int) -> Unit,
    onToggleFavorite: (AudioTrack) -> Unit,
    onTrackMoreClick: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier,
    selectedTrackIds: Set<String> = emptySet(),
    onToggleSelectTrack: (String) -> Unit = {},
    onSelectAllTracks: (List<AudioTrack>) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onBatchPlayNext: (List<AudioTrack>) -> Unit = {},
    onBatchAddToQueue: (List<AudioTrack>) -> Unit = {},
    onBatchOpenPlaylistPicker: () -> Unit = {},
    onSwipeQueueNext: ((AudioTrack) -> Unit)? = null,
    onSwipeToggleFavorite: ((AudioTrack) -> Unit)? = null
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var multiSelectToggle by remember { mutableStateOf(false) }
    val isSelectionMode = selectedTrackIds.isNotEmpty() || multiSelectToggle

    Column(modifier = modifier.fillMaxSize()) {
        SonicBloomTopBar(
            title = stringResource(R.string.nav_tracks),
            subtitle = "${libraryState.filteredTracks.size} of ${libraryState.allTracks.size} audio files",
            themeMode = libraryState.themeMode,
            onCycleTheme = onCycleTheme,
            onOpenSettings = onOpenSettings,
            trailingAction = {
                Row {
                    IconButton(
                        onClick = onImportAudioFiles,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tracks_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AudioFile,
                            contentDescription = stringResource(R.string.action_import_audio_files),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(
                        onClick = onRescanLibrary,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tracks_rescan_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_rescan_library),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        )

        if (libraryState.isScanning) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (libraryState.libraryError != null) {
            StudioErrorBanner(
                message = libraryState.libraryError,
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRescanLibrary,
                onDismiss = onDismissError
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = libraryState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.search_tracks_hint),
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
                if (libraryState.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear search",
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
                .testTag("tracks_search_field")
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filters = listOf(
                TrackFilterOption.ALL to stringResource(R.string.filter_all),
                TrackFilterOption.FAVORITES to stringResource(R.string.filter_favorites),
                TrackFilterOption.MP3 to stringResource(R.string.filter_mp3),
                TrackFilterOption.LOSSLESS to stringResource(R.string.filter_lossless),
                TrackFilterOption.IMPORTED to stringResource(R.string.filter_imported)
            )
            filters.forEach { (option, label) ->
                val selected = libraryState.filterOption == option
                FilterChip(
                    selected = selected,
                    onClick = { onFilterOptionChange(option) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    label = { Text(label, maxLines = 1, softWrap = false) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("filter_chip_${option.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val sortOptions = listOf(
            TrackSortOption.TITLE to stringResource(R.string.sort_by_title),
            TrackSortOption.ARTIST to stringResource(R.string.sort_by_artist),
            TrackSortOption.DURATION to stringResource(R.string.sort_by_duration),
            TrackSortOption.RECENT to stringResource(R.string.sort_by_recent),
            TrackSortOption.PLAYS to stringResource(R.string.sort_by_plays)
        )
        val activeSortLabel = sortOptions.firstOrNull { it.first == libraryState.sortOption }?.second
            ?: stringResource(R.string.sort_by_title)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp),
                    onClick = { sortMenuExpanded = true },
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .testTag("tracks_sort_menu_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Sort: $activeSortLabel",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    sortOptions.forEach { (option, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    color = if (option == libraryState.sortOption) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            trailingIcon = if (option == libraryState.sortOption) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null,
                            onClick = {
                                sortMenuExpanded = false
                                onSortOptionChange(option)
                            },
                            modifier = Modifier.testTag("sort_option_${option.name}")
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (libraryState.filteredTracks.isNotEmpty()) {
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
                            .testTag("tracks_select_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Checklist,
                            contentDescription = stringResource(R.string.action_select_tracks),
                            tint = if (isSelectionMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                IconButton(
                    onClick = onToggleSortDirection,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("tracks_sort_direction_button")
                ) {
                    Icon(
                        imageVector = if (libraryState.sortAscending) {
                            Icons.Filled.ArrowUpward
                        } else {
                            Icons.Filled.ArrowDownward
                        },
                        contentDescription = if (libraryState.sortAscending) "Ascending" else "Descending",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        AnimatedVisibility(visible = isSelectionMode && libraryState.filteredTracks.isNotEmpty()) {
            BatchSelectionBar(
                selectedCount = selectedTrackIds.size,
                onSelectAll = { onSelectAllTracks(libraryState.filteredTracks) },
                onPlayNext = {
                    multiSelectToggle = false
                    onBatchPlayNext(libraryState.filteredTracks)
                },
                onAddToQueue = {
                    multiSelectToggle = false
                    onBatchAddToQueue(libraryState.filteredTracks)
                },
                onAddToPlaylist = {
                    onBatchOpenPlaylistPicker()
                },
                onClearSelection = {
                    multiSelectToggle = false
                    onClearSelection()
                }
            )
        }

        if (libraryState.filteredTracks.isNotEmpty() && !isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onPlayTracks(libraryState.filteredTracks, 0) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tracks_play_all_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_play_all), maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        val idx = libraryState.filteredTracks.indices.random()
                        onPlayTracks(libraryState.filteredTracks, idx)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("tracks_shuffle_all_button")
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_shuffle_all), maxLines = 1)
                }
            }
        }

        if (libraryState.filteredTracks.isEmpty()) {
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    title = if (libraryState.hasActiveTrackFilters) {
                        stringResource(R.string.empty_search_title)
                    } else {
                        stringResource(R.string.empty_library_title)
                    },
                    subtitle = if (libraryState.hasActiveTrackFilters) {
                        stringResource(R.string.empty_search_subtitle)
                    } else {
                        stringResource(R.string.empty_library_subtitle)
                    },
                    action = {
                        if (libraryState.hasActiveTrackFilters) {
                            OutlinedButton(
                                onClick = onClearFilters,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("tracks_clear_filters_button")
                            ) {
                                Text(stringResource(R.string.action_clear_filters))
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (!libraryState.hasAudioPermission) {
                                    Button(
                                        onClick = onRequestAudioPermission,
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text(stringResource(R.string.action_grant_audio_access))
                                    }
                                }
                                OutlinedButton(
                                    onClick = onImportAudioFiles,
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text(stringResource(R.string.action_import_audio_files))
                                }
                            }
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
            ) {
                itemsIndexed(
                    libraryState.filteredTracks,
                    key = { _, item -> item.id }
                ) { index, track ->
                    val selected = selectedTrackIds.contains(track.id)
                    TrackRowItem(
                        track = track,
                        isCurrentTrack = track.id == currentTrackId,
                        isPlaying = isPlaying && track.id == currentTrackId,
                        onClick = {
                            if (isSelectionMode) {
                                onToggleSelectTrack(track.id)
                            } else {
                                onPlayTracks(libraryState.filteredTracks, index)
                            }
                        },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onMoreClick = { onTrackMoreClick(track) },
                        selectionMode = isSelectionMode,
                        isSelected = selected,
                        onLongPress = {
                            multiSelectToggle = true
                            onToggleSelectTrack(track.id)
                        },
                        onSwipeQueueNext = onSwipeQueueNext?.let { fn -> { fn(track) } },
                        onSwipeSecondaryAction = onSwipeToggleFavorite?.let { fn -> { fn(track) } }
                    )
                }
            }
        }
    }
}
