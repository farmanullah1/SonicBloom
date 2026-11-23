package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.data.model.PlaylistInfo
import com.example.ui.util.formatDurationMs
import com.example.ui.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsBottomSheet(
    track: AudioTrack,
    playlistContextId: Long? = null,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onShowDetails: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(
                    albumArtUri = track.albumArtUri,
                    size = 52.dp,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            SheetActionItem(
                icon = {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                label = stringResource(R.string.action_play_next),
                testTag = "sheet_play_next",
                onClick = {
                    onPlayNext()
                    onDismiss()
                }
            )
            SheetActionItem(
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                label = stringResource(R.string.action_add_to_queue),
                testTag = "sheet_add_to_queue",
                onClick = {
                    onAddToQueue()
                    onDismiss()
                }
            )
            SheetActionItem(
                icon = {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                label = stringResource(R.string.action_add_to_playlist),
                testTag = "sheet_add_to_playlist",
                onClick = {
                    onDismiss()
                    onOpenAddToPlaylist()
                }
            )
            SheetActionItem(
                icon = {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                },
                label = if (track.isFavorite) "Remove from Liked Songs" else "Add to Liked Songs",
                testTag = "sheet_toggle_favorite",
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                }
            )
            if (playlistContextId != null && onRemoveFromPlaylist != null) {
                SheetActionItem(
                    icon = {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    label = stringResource(R.string.action_remove_from_playlist),
                    labelColor = MaterialTheme.colorScheme.error,
                    testTag = "sheet_remove_from_playlist",
                    onClick = {
                        onRemoveFromPlaylist()
                        onDismiss()
                    }
                )
            }
            SheetActionItem(
                icon = {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                },
                label = stringResource(R.string.action_track_info),
                testTag = "sheet_track_info",
                onClick = {
                    onDismiss()
                    onShowDetails()
                }
            )
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: @Composable () -> Unit,
    label: String,
    testTag: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = labelColor)
        },
        leadingContent = icon,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = labelColor,
            leadingIconColor = labelColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    )
}

@Composable
fun AddToPlaylistDialog(
    subtitleLabel: String,
    playlists: List<PlaylistInfo>,
    isDuplicateName: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onCreateAndAdd: (String) -> Unit
) {
    var showNewField by remember { mutableStateOf(playlists.isEmpty()) }
    var newName by remember { mutableStateOf("") }
    val trimmed = newName.trim()
    val duplicate = trimmed.isNotEmpty() && isDuplicateName(trimmed)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(stringResource(R.string.action_add_to_playlist)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = subtitleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (playlists.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        items(playlists, key = { it.id }) { playlist ->
                            ListItem(
                                headlineContent = {
                                    Text(playlist.name, color = MaterialTheme.colorScheme.onSurface)
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(R.string.playlist_track_count, playlist.trackIds.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.AutoMirrored.Filled.PlaylistPlay,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .clickable {
                                        onSelectPlaylist(playlist.id)
                                        onDismiss()
                                    }
                                    .testTag("pick_playlist_${playlist.id}")
                            )
                        }
                    }
                }

                if (showNewField) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text(stringResource(R.string.playlist_name_hint)) },
                        isError = duplicate,
                        supportingText = if (duplicate) {
                            { Text("A playlist with this name already exists.") }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_playlist_inline_input")
                    )
                } else {
                    TextButton(
                        onClick = { showNewField = true },
                        modifier = Modifier.testTag("show_new_playlist_field")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_create_playlist))
                    }
                }
            }
        },
        confirmButton = {
            if (showNewField) {
                TextButton(
                    enabled = trimmed.isNotEmpty() && !duplicate,
                    onClick = {
                        onCreateAndAdd(trimmed)
                        onDismiss()
                    },
                    modifier = Modifier.testTag("confirm_create_and_add_playlist")
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun MultiTrackPickerDialog(
    availableTracks: List<AudioTrack>,
    existingTrackIds: Set<String>,
    onDismiss: () -> Unit,
    onAddSelected: (List<String>) -> Unit
) {
    val candidates = remember(availableTracks, existingTrackIds) {
        availableTracks.filter { !existingTrackIds.contains(it.id) }
    }
    val selectedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(stringResource(R.string.action_add_tracks)) },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    text = "All tracks from your library are already in this playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    items(candidates, key = { it.id }) { track ->
                        val checked = selectedIds.contains(track.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedIds.remove(track.id)
                                    else selectedIds.add(track.id)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selectedIds.add(track.id)
                                    else selectedIds.remove(track.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${formatDurationMs(track.durationMs)}",
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
        },
        confirmButton = {
            if (candidates.isNotEmpty()) {
                TextButton(
                    enabled = selectedIds.isNotEmpty(),
                    onClick = {
                        onAddSelected(selectedIds.toList())
                        onDismiss()
                    },
                    modifier = Modifier.testTag("confirm_batch_add_tracks")
                ) {
                    Text("Add (${selectedIds.size})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun TrackDetailsDialog(
    track: AudioTrack,
    onDismiss: () -> Unit,
    onSaveMetadataOverride: ((title: String, artist: String, album: String) -> Unit)? = null,
    onResetMetadataOverride: (() -> Unit)? = null
) {
    var isEditing by remember(track.id) { mutableStateOf(false) }
    var editTitle by remember(track.id, track.title) { mutableStateOf(track.title) }
    var editArtist by remember(track.id, track.artist) { mutableStateOf(track.artist) }
    var editAlbum by remember(track.id, track.album) { mutableStateOf(track.album) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                if (isEditing) stringResource(R.string.action_edit_tags)
                else stringResource(R.string.action_track_info)
            )
        },
        text = {
            if (isEditing) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_tag_title_input")
                    )
                    OutlinedTextField(
                        value = editArtist,
                        onValueChange = { editArtist = it },
                        label = { Text("Artist") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_tag_artist_input")
                    )
                    OutlinedTextField(
                        value = editAlbum,
                        onValueChange = { editAlbum = it },
                        label = { Text("Album") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_tag_album_input")
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Title", track.title)
                    DetailRow("Artist", track.artist)
                    DetailRow("Album", track.album)
                    DetailRow("Folder", track.folderName)
                    DetailRow("Duration", formatDurationMs(track.durationMs))
                    DetailRow("Format", track.formatLabel)
                    DetailRow("Size", formatFileSize(track.sizeBytes))
                    DetailRow("Plays", track.playCount.toString())
                    if (track.isMetadataOverridden) {
                        Text(
                            text = "Custom local tag override active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                TextButton(
                    enabled = editTitle.trim().isNotEmpty(),
                    onClick = {
                        onSaveMetadataOverride?.invoke(editTitle.trim(), editArtist.trim(), editAlbum.trim())
                        onDismiss()
                    },
                    modifier = Modifier.testTag("track_details_save_tags_button")
                ) {
                    Text(stringResource(R.string.action_save))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (track.isMetadataOverridden && onResetMetadataOverride != null) {
                        TextButton(
                            onClick = {
                                onResetMetadataOverride()
                                onDismiss()
                            },
                            modifier = Modifier.testTag("track_details_reset_tags_button")
                        ) {
                            Text(
                                text = stringResource(R.string.action_reset_tags),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (onSaveMetadataOverride != null) {
                        TextButton(
                            onClick = { isEditing = true },
                            modifier = Modifier.testTag("track_details_edit_tags_button")
                        ) {
                            Text(stringResource(R.string.action_edit_tags))
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            }
        },
        dismissButton = {
            if (isEditing) {
                TextButton(onClick = { isEditing = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun PlaylistNameDialog(
    title: String,
    initialName: String = "",
    isDuplicateName: (String) -> Boolean = { false },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialName) { mutableStateOf(initialName) }
    val trimmed = text.trim()
    val duplicate = trimmed.isNotEmpty() &&
        trimmed.lowercase() != initialName.trim().lowercase() &&
        isDuplicateName(trimmed)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.playlist_name_hint)) },
                isError = duplicate,
                supportingText = if (duplicate) {
                    { Text("A playlist with this name already exists.") }
                } else null,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlist_name_input")
            )
        },
        confirmButton = {
            TextButton(
                enabled = trimmed.isNotEmpty() && !duplicate,
                onClick = {
                    onConfirm(trimmed)
                    onDismiss()
                },
                modifier = Modifier.testTag("playlist_name_confirm")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
