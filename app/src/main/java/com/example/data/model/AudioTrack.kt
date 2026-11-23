package com.example.data.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

data class AudioTrack(
    val id: String,
    val contentUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val albumArtUri: String?,
    val folderName: String,
    val mimeType: String,
    val bitrateKbps: Int?,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val isImported: Boolean = false,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L,
    val isMetadataOverridden: Boolean = false
) {
    val isMp3: Boolean
        get() = mimeType.contains("mpeg", ignoreCase = true) || mimeType.contains("mp3", ignoreCase = true)

    val isLossless: Boolean
        get() = mimeType.contains("flac", ignoreCase = true) || mimeType.contains("wav", ignoreCase = true)

    val formatLabel: String
        get() {
            val ext = when {
                isMp3 -> "MP3"
                mimeType.contains("flac", ignoreCase = true) -> "FLAC"
                mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("m4a", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true) -> "AAC"
                mimeType.contains("wav", ignoreCase = true) -> "WAV"
                mimeType.contains("ogg", ignoreCase = true) || mimeType.contains("opus", ignoreCase = true) -> "OGG"
                else -> "AUDIO"
            }
            return if (bitrateKbps != null && bitrateKbps > 0) {
                "$ext • ${bitrateKbps} kbps"
            } else {
                ext
            }
        }
}

data class AlbumSummary(
    val key: String,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val trackCount: Int,
    val totalDurationMs: Long
)

data class ArtistSummary(
    val name: String,
    val trackCount: Int,
    val albumCount: Int,
    val coverArtUri: String?
)

data class FolderSummary(
    val name: String,
    val trackCount: Int,
    val totalDurationMs: Long
)

data class PlaylistInfo(
    val id: Long,
    val name: String,
    val trackIds: List<String>,
    val coverArtUri: String?
)

@Serializable
enum class SmartPlaylistType {
    LIKED,
    MOST_PLAYED,
    RECENTLY_ADDED,
    LOSSLESS_VAULT,
    UNPLAYED_GEMS
}

enum class TrackSortOption {
    TITLE,
    ARTIST,
    DURATION,
    RECENT,
    PLAYS
}

enum class TrackFilterOption {
    ALL,
    FAVORITES,
    MP3,
    LOSSLESS,
    IMPORTED
}

enum class BrowseTab {
    ALBUMS,
    ARTISTS,
    FOLDERS
}

enum class RepeatModeOption {
    OFF,
    ALL,
    ONE
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class LyricsLine(
    val timestampMs: Long?,
    val text: String
)

data class TrackLyrics(
    val trackId: String,
    val lines: List<LyricsLine>,
    val isSynced: Boolean,
    val sourceLabel: String
)

data class DynamicTrackColors(
    val accentColor: Color? = null,
    val surfaceTint: Color? = null
)

data class WaveformData(
    val trackId: String = "",
    val amplitudes: List<Float> = emptyList(),
    val isRealPcm: Boolean = false,
    val suggestedGainDb: Float = 0f
)

data class CustomEqPreset(
    val name: String,
    val bandLevelsMb: List<Int>,
    val bassBoostStrength: Int,
    val virtualizerStrength: Int
)

data class EqualizerBandUi(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Int
)

data class EqualizerState(
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val minLevelMb: Int = -1500,
    val maxLevelMb: Int = 1500,
    val bands: List<EqualizerBandUi> = emptyList(),
    val activePreset: String = "Flat",
    val bassBoostStrength: Int = 0,
    val virtualizerAvailable: Boolean = false,
    val virtualizerStrength: Int = 0,
    val normalizationAvailable: Boolean = true,
    val customPresets: List<String> = emptyList(),
    val statusMessage: String? = null
)

data class SleepTimerState(
    val remainingMs: Long = 0L,
    val stopAfterCurrentTrack: Boolean = false
) {
    val isActive: Boolean
        get() = remainingMs > 0L || stopAfterCurrentTrack
}
