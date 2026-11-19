package com.example

import com.example.data.model.AudioTrack
import com.example.data.model.SleepTimerState
import com.example.data.model.TrackFilterOption
import com.example.ui.util.formatDurationMs
import com.example.ui.util.formatFileSize
import com.example.ui.util.formatFrequencyHz
import com.example.ui.viewmodel.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersAndModelsTest {

    @Test
    fun formatDurationMs_formatsMinutesAndHoursCorrectly() {
        assertEquals("00:00", formatDurationMs(0L))
        assertEquals("03:45", formatDurationMs(225_000L))
        assertEquals("1:02:05", formatDurationMs(3_725_000L))
    }

    @Test
    fun formatFrequencyHz_formatsHzAndKhz() {
        assertEquals("60Hz", formatFrequencyHz(60))
        assertEquals("1kHz", formatFrequencyHz(1000))
        assertEquals("1.4kHz", formatFrequencyHz(1400))
    }

    @Test
    fun formatFileSize_formatsKbAndMb() {
        assertEquals("512 KB", formatFileSize(512L * 1024L))
        assertEquals("4.5 MB", formatFileSize((4.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun audioTrack_formatFlagsAndLabel_identifiesCodecAndBitrate() {
        val mp3 = sampleTrack(id = "1", mimeType = "audio/mpeg", bitrateKbps = 320)
        assertTrue(mp3.isMp3)
        assertFalse(mp3.isLossless)
        assertEquals("MP3 • 320 kbps", mp3.formatLabel)

        val flac = sampleTrack(id = "2", mimeType = "audio/flac", bitrateKbps = 960)
        assertFalse(flac.isMp3)
        assertTrue(flac.isLossless)
        assertEquals("FLAC • 960 kbps", flac.formatLabel)
    }

    @Test
    fun libraryUiState_hasActiveTrackFilters_tracksQueryAndChipSelection() {
        assertFalse(LibraryUiState().hasActiveTrackFilters)
        assertTrue(LibraryUiState(searchQuery = "bloom").hasActiveTrackFilters)
        assertTrue(LibraryUiState(filterOption = TrackFilterOption.LOSSLESS).hasActiveTrackFilters)
    }

    @Test
    fun sleepTimerState_isActive_reflectsRemainingOrTrackEnd() {
        assertFalse(SleepTimerState().isActive)
        assertTrue(SleepTimerState(remainingMs = 60_000L).isActive)
        assertTrue(SleepTimerState(stopAfterCurrentTrack = true).isActive)
    }

    private fun sampleTrack(
        id: String,
        mimeType: String,
        bitrateKbps: Int?
    ) = AudioTrack(
        id = id,
        contentUri = "content://media/$id",
        title = "Midnight Bloom",
        artist = "Aria",
        album = "Nocturne",
        albumId = 10L,
        durationMs = 210_000L,
        albumArtUri = null,
        folderName = "Music",
        mimeType = mimeType,
        bitrateKbps = bitrateKbps,
        sizeBytes = 8_400_000L,
        dateAddedSec = 1700000000L
    )
}
