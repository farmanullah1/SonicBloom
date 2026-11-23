package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.data.model.EqualizerState
import com.example.data.model.SleepTimerState
import com.example.ui.util.formatDurationMs
import com.example.ui.util.formatFrequencyHz
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerBottomSheet(
    equalizerState: EqualizerState,
    presets: List<String>,
    onDismiss: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onBandLevelChange: (Int, Int) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onVirtualizerChange: (Int) -> Unit = {},
    onSaveCustomPreset: (String) -> Unit = {},
    onDeleteCustomPreset: (String) -> Unit = {}
) {
    var showSaveDialog by remember { mutableStateOf(false) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Equalizer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.equalizer_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { showSaveDialog = true },
                        enabled = equalizerState.bands.isNotEmpty(),
                        modifier = Modifier.testTag("eq_save_preset_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.equalizer_save_preset), maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = equalizerState.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("eq_master_switch")
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val selected = equalizerState.activePreset == preset
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPreset(preset) },
                        label = { Text(preset, maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("eq_preset_$preset")
                    )
                }
                equalizerState.customPresets.forEach { customName ->
                    val selected = equalizerState.activePreset == customName
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectPreset(customName) },
                        label = { Text(customName, maxLines = 1, softWrap = false) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Delete preset $customName",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onDeleteCustomPreset(customName) }
                                    .testTag("eq_delete_preset_$customName")
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("eq_custom_preset_$customName")
                    )
                }
            }

            if (equalizerState.statusMessage != null) {
                Text(
                    text = equalizerState.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!equalizerState.isAvailable || equalizerState.bands.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    equalizerState.bands.forEach { band ->
                        val dbValue = band.levelMb / 100f
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatFrequencyHz(band.centerFreqHz),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(58.dp)
                            )
                            Slider(
                                value = band.levelMb.toFloat(),
                                onValueChange = { onBandLevelChange(band.index, it.roundToInt()) },
                                valueRange = equalizerState.minLevelMb.toFloat()..equalizerState.maxLevelMb.toFloat(),
                                enabled = equalizerState.isEnabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("eq_band_${band.index}")
                            )
                            Text(
                                text = String.format("%+.1fdB", dbValue),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(58.dp)
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_bass_boost),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(equalizerState.bassBoostStrength / 10)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Slider(
                    value = equalizerState.bassBoostStrength.toFloat(),
                    onValueChange = { onBassBoostChange(it.roundToInt()) },
                    valueRange = 0f..1000f,
                    enabled = equalizerState.isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary
                    ),
                    modifier = Modifier.testTag("eq_bass_boost_slider")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.equalizer_virtualizer),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${(equalizerState.virtualizerStrength / 10)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Slider(
                    value = equalizerState.virtualizerStrength.toFloat(),
                    onValueChange = { onVirtualizerChange(it.roundToInt()) },
                    valueRange = 0f..1000f,
                    enabled = equalizerState.isEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.tertiary,
                        activeTrackColor = MaterialTheme.colorScheme.tertiary
                    ),
                    modifier = Modifier.testTag("eq_virtualizer_slider")
                )
                if (!equalizerState.virtualizerAvailable && equalizerState.isAvailable) {
                    Text(
                        text = "Spatial virtualizer activates automatically on headphones or supported stereo routes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        var presetName by remember { mutableStateOf("") }
        val trimmed = presetName.trim()
        val invalidBuiltin = presets.any { it.equals(trimmed, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text(stringResource(R.string.equalizer_save_preset)) },
            text = {
                OutlinedTextField(
                    value = presetName,
                    onValueChange = { presetName = it },
                    label = { Text("Preset name") },
                    isError = invalidBuiltin,
                    supportingText = if (invalidBuiltin) {
                        { Text("Built-in preset names cannot be overwritten.") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("eq_save_preset_input")
                )
            },
            confirmButton = {
                TextButton(
                    enabled = trimmed.isNotEmpty() && !invalidBuiltin,
                    onClick = {
                        onSaveCustomPreset(trimmed)
                        showSaveDialog = false
                    },
                    modifier = Modifier.testTag("eq_save_preset_confirm")
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun SleepTimerDialog(
    sleepTimer: SleepTimerState,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onSelectEndOfTrack: () -> Unit,
    onCancelTimer: () -> Unit
) {
    val durations = listOf(15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        icon = {
            Icon(
                Icons.Filled.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(stringResource(R.string.sleep_timer_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (sleepTimer.isActive) {
                    val statusText = if (sleepTimer.stopAfterCurrentTrack) {
                        "Active: Stopping after current track"
                    } else {
                        "Active: ${formatDurationMs(sleepTimer.remainingMs)} remaining"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                durations.forEach { minutes ->
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(R.string.sleep_timer_minutes, minutes),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectMinutes(minutes)
                                onDismiss()
                            }
                            .testTag("sleep_timer_${minutes}m")
                    )
                }

                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.sleep_timer_end_of_track),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onSelectEndOfTrack()
                            onDismiss()
                        }
                        .testTag("sleep_timer_end_of_track")
                )

                if (sleepTimer.isActive) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.sleep_timer_off),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onCancelTimer()
                                onDismiss()
                            }
                            .testTag("sleep_timer_cancel")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSelectSpeed: (Float) -> Unit
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Playback Speed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                speeds.forEach { speed ->
                    val selected = kotlin.math.abs(currentSpeed - speed) < 0.05f
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "${speed}x",
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingContent = {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectSpeed(speed)
                                onDismiss()
                            }
                            .testTag("speed_option_$speed")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: List<AudioTrack>,
    currentIndex: Int,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onJumpToIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onClearQueue: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${stringResource(R.string.up_next_label)} (${queue.size})",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (queue.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onClearQueue()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("queue_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_clear_queue))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (queue.isEmpty()) {
                StudioEmptyState(
                    icon = {
                        Icon(
                            Icons.Filled.Equalizer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = stringResource(R.string.empty_queue_title),
                    subtitle = stringResource(R.string.empty_queue_subtitle)
                )
            } else {
                var draggedIndex by remember { mutableIntStateOf(-1) }
                var dragOffsetY by remember { mutableFloatStateOf(0f) }
                val rowStepPx = with(LocalDensity.current) { 64.dp.toPx() }
                val latestQueueSize by rememberUpdatedState(queue.size)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    itemsIndexed(queue, key = { index, item -> "${index}_${item.id}" }) { index, track ->
                        val isCurrent = index == currentIndex
                        val isDragging = index == draggedIndex
                        val latestIndex by rememberUpdatedState(index)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                    scaleX = if (isDragging) 1.02f else 1f
                                    scaleY = if (isDragging) 1.02f else 1f
                                }
                                .background(
                                    if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh
                                    else Color.Transparent
                                )
                                .clickable { onJumpToIndex(index) }
                                .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .pointerInput(track.id) {
                                        detectVerticalDragGestures(
                                            onDragStart = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggedIndex = latestIndex
                                                dragOffsetY = 0f
                                            },
                                            onVerticalDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount
                                                val currentPos = draggedIndex
                                                if (currentPos >= 0) {
                                                    if (dragOffsetY > rowStepPx && currentPos < latestQueueSize - 1) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onMoveItem(currentPos, currentPos + 1)
                                                        draggedIndex = currentPos + 1
                                                        dragOffsetY -= rowStepPx
                                                    } else if (dragOffsetY < -rowStepPx && currentPos > 0) {
                                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onMoveItem(currentPos, currentPos - 1)
                                                        draggedIndex = currentPos - 1
                                                        dragOffsetY += rowStepPx
                                                    }
                                                }
                                            },
                                            onDragEnd = {
                                                draggedIndex = -1
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = -1
                                                dragOffsetY = 0f
                                            }
                                        )
                                    }
                                    .testTag("queue_drag_handle_$index"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            TrackArtwork(
                                albumArtUri = track.albumArtUri,
                                size = 44.dp,
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                            if (isCurrent) {
                                PlayingWaveformIndicator(isPlaying = isPlaying)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            IconButton(
                                enabled = index > 0,
                                onClick = { onMoveItem(index, index - 1) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("queue_move_up_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowUpward,
                                    contentDescription = "Move up",
                                    tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                enabled = index < queue.lastIndex,
                                onClick = { onMoveItem(index, index + 1) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("queue_move_down_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowDownward,
                                    contentDescription = "Move down",
                                    tint = if (index < queue.lastIndex) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRemoveIndex(index) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("queue_remove_$index")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove from queue",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
