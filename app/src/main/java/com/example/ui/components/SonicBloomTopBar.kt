package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.example.data.model.ThemeMode
import com.example.ui.util.formatDurationMs
import com.example.ui.util.formatFileSize
import com.example.ui.viewmodel.LibraryUiState
import kotlin.math.roundToInt

@Composable
fun SonicBloomTopBar(
    title: String,
    subtitle: String? = null,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    trailingAction: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (trailingAction != null) {
                    trailingAction()
                }
                IconButton(
                    onClick = onCycleTheme,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_bar_theme_toggle")
                ) {
                    val themeIcon = when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                        ThemeMode.LIGHT -> Icons.Filled.LightMode
                        ThemeMode.DARK -> Icons.Filled.DarkMode
                    }
                    Icon(
                        imageVector = themeIcon,
                        contentDescription = stringResource(R.string.cd_theme_toggle),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_bar_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.cd_studio_settings),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            thickness = 1.dp
        )
    }
}

@Composable
fun StudioErrorBanner(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .border(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            )
            .testTag("studio_error_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(
                        text = actionLabel,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.action_dismiss),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioSettingsSheet(
    libraryState: LibraryUiState,
    onDismiss: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onOpenEqualizer: () -> Unit,
    onRescanLibrary: () -> Unit,
    onImportAudioFiles: () -> Unit,
    onCrossfadeChange: (Int) -> Unit = {},
    onVolumeNormalizationChange: (Boolean) -> Unit = {},
    onMinDurationChange: (Int) -> Unit = {},
    onToggleExcludedFolder: (String) -> Unit = {},
    onClearExcludedFolders: () -> Unit = {},
    onClearArtworkCache: () -> Unit = {}
) {
    var showExcludedFoldersDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.theme_mode_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val modes = listOf(
                    ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    ThemeMode.LIGHT to stringResource(R.string.theme_light),
                    ThemeMode.DARK to stringResource(R.string.theme_dark)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = libraryState.themeMode == mode,
                            onClick = { onSelectThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                            modifier = Modifier.testTag("settings_theme_${mode.name}")
                        ) {
                            Text(label, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_audio_engine),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_crossfade),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (libraryState.crossfadeSeconds == 0) "Off" else "${libraryState.crossfadeSeconds}s",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = libraryState.crossfadeSeconds.toFloat(),
                        onValueChange = { onCrossfadeChange(it.roundToInt().coerceIn(0, 12)) },
                        valueRange = 0f..12f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_crossfade_slider")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_normalization),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Balance loudness across tracks automatically",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = libraryState.volumeNormalizationEnabled,
                            onCheckedChange = onVolumeNormalizationChange,
                            modifier = Modifier.testTag("settings_normalization_switch")
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_library_filters),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_min_duration),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val durationChoices = listOf(0 to "Off", 15 to "15s", 30 to "30s", 60 to "60s")
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        durationChoices.forEachIndexed { index, (sec, label) ->
                            SegmentedButton(
                                selected = libraryState.minTrackDurationSec == sec,
                                onClick = { onMinDurationChange(sec) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = durationChoices.size),
                                modifier = Modifier.testTag("settings_clip_filter_$sec")
                            ) {
                                Text(label, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showExcludedFoldersDialog = true }
                            .padding(vertical = 6.dp)
                            .testTag("settings_excluded_folders_button"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_excluded_folders),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (libraryState.excludedFolders.isEmpty()) {
                                        "No folders excluded"
                                    } else {
                                        "${libraryState.excludedFolders.size} folder(s) hidden"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "Manage",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Library Footprint",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatMetric(label = "Tracks", value = libraryState.allTracks.size.toString())
                        StatMetric(label = "Albums", value = libraryState.albums.size.toString())
                        StatMetric(label = "Duration", value = formatDurationMs(libraryState.totalDurationMs))
                        StatMetric(label = "Storage", value = formatFileSize(libraryState.totalSizeBytes))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Art & Palette Cache",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${formatFileSize(libraryState.artCacheSizeBytes)} / 25 MB cap",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onClearArtworkCache,
                            modifier = Modifier.testTag("settings_clear_cache_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.action_clear_cache))
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            onDismiss()
                            onOpenEqualizer()
                        }
                        .testTag("settings_open_eq")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Equalizer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.equalizer_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onRescanLibrary()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("settings_rescan_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_rescan_library), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onImportAudioFiles()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("settings_import_button")
                    ) {
                        Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_import_audio_files), maxLines = 1)
                    }
                }
            }
        }
    }

    if (showExcludedFoldersDialog) {
        ExcludedFoldersDialog(
            availableFolders = libraryState.availableFolders,
            excludedFolders = libraryState.excludedFolders,
            onToggleFolder = onToggleExcludedFolder,
            onClearAll = onClearExcludedFolders,
            onDismiss = { showExcludedFoldersDialog = false }
        )
    }
}

@Composable
private fun ExcludedFoldersDialog(
    availableFolders: List<String>,
    excludedFolders: Set<String>,
    onToggleFolder: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val allFolders = remember(availableFolders, excludedFolders) {
        (availableFolders + excludedFolders).distinct().sortedBy { it.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text(stringResource(R.string.settings_excluded_folders)) },
        text = {
            if (allFolders.isEmpty()) {
                Text(
                    text = "Scan or import audio tracks to see folders here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                ) {
                    items(allFolders, key = { it }) { folder ->
                        val isExcluded = excludedFolders.contains(folder)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleFolder(folder) }
                                .padding(vertical = 6.dp)
                                .testTag("excluded_folder_row_$folder"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isExcluded,
                                onCheckedChange = { onToggleFolder(folder) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isExcluded) "Hidden from library" else "Included",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isExcluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            if (excludedFolders.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    modifier = Modifier.testTag("clear_excluded_folders_button")
                ) {
                    Text("Include All")
                }
            }
        }
    )
}

@Composable
private fun StatMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
