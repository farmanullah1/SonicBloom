package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.RepeatModeOption
import com.example.data.model.ThemeMode
import com.example.data.model.TrackSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.sonicBloomDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonicbloom_prefs"
)

data class PlayerPreferences(
    val playbackSpeed: Float = 1.0f,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val eqEnabled: Boolean = false,
    val eqPresetName: String = "Flat",
    val bassBoostStrength: Int = 0,
    val virtualizerStrength: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sortOption: TrackSortOption = TrackSortOption.TITLE,
    val sortAscending: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val volumeNormalizationEnabled: Boolean = false,
    val minTrackDurationSec: Int = 0,
    val excludedFolders: Set<String> = emptySet()
)

data class PlaybackSessionSnapshot(
    val queueTrackIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val lastTrackTitle: String = "",
    val lastTrackArtist: String = "",
    val lastTrackArtUri: String? = null,
    val lastIsPlaying: Boolean = false
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")

        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val VOLUME_NORMALIZATION = booleanPreferencesKey("volume_normalization")
        val MIN_TRACK_DURATION_SEC = intPreferencesKey("min_track_duration_sec")
        val EXCLUDED_FOLDERS = stringPreferencesKey("excluded_folders_json")

        val SESSION_QUEUE_IDS = stringPreferencesKey("session_queue_ids")
        val SESSION_CURRENT_INDEX = intPreferencesKey("session_current_index")
        val SESSION_POSITION_MS = longPreferencesKey("session_position_ms")
        val SESSION_LAST_TITLE = stringPreferencesKey("session_last_title")
        val SESSION_LAST_ARTIST = stringPreferencesKey("session_last_artist")
        val SESSION_LAST_ART_URI = stringPreferencesKey("session_last_art_uri")
        val SESSION_LAST_IS_PLAYING = booleanPreferencesKey("session_last_is_playing")
    }

    val preferences: Flow<PlayerPreferences> = context.sonicBloomDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val repeatMode = runCatching {
                RepeatModeOption.valueOf(prefs[Keys.REPEAT_MODE] ?: RepeatModeOption.OFF.name)
            }.getOrDefault(RepeatModeOption.OFF)

            val themeMode = runCatching {
                ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM)

            val sortOption = runCatching {
                TrackSortOption.valueOf(prefs[Keys.SORT_OPTION] ?: TrackSortOption.TITLE.name)
            }.getOrDefault(TrackSortOption.TITLE)

            val rawExcluded = prefs[Keys.EXCLUDED_FOLDERS].orEmpty()
            val excludedFolders = if (rawExcluded.isNotBlank()) {
                runCatching { Json.decodeFromString<List<String>>(rawExcluded).toSet() }
                    .getOrDefault(emptySet())
            } else {
                emptySet()
            }

            PlayerPreferences(
                playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
                shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
                repeatMode = repeatMode,
                eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
                eqPresetName = prefs[Keys.EQ_PRESET] ?: "Flat",
                bassBoostStrength = (prefs[Keys.BASS_BOOST] ?: 0).coerceIn(0, 1000),
                virtualizerStrength = (prefs[Keys.VIRTUALIZER_STRENGTH] ?: 0).coerceIn(0, 1000),
                themeMode = themeMode,
                sortOption = sortOption,
                sortAscending = prefs[Keys.SORT_ASCENDING] ?: true,
                crossfadeSeconds = (prefs[Keys.CROSSFADE_SECONDS] ?: 0).coerceIn(0, 12),
                volumeNormalizationEnabled = prefs[Keys.VOLUME_NORMALIZATION] ?: false,
                minTrackDurationSec = (prefs[Keys.MIN_TRACK_DURATION_SEC] ?: 0).coerceAtLeast(0),
                excludedFolders = excludedFolders
            )
        }

    val playbackSession: Flow<PlaybackSessionSnapshot> = context.sonicBloomDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val rawIds = prefs[Keys.SESSION_QUEUE_IDS].orEmpty()
            val decodedIds = if (rawIds.isNotBlank()) {
                runCatching { Json.decodeFromString<List<String>>(rawIds) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            PlaybackSessionSnapshot(
                queueTrackIds = decodedIds,
                currentIndex = prefs[Keys.SESSION_CURRENT_INDEX] ?: 0,
                positionMs = (prefs[Keys.SESSION_POSITION_MS] ?: 0L).coerceAtLeast(0L),
                lastTrackTitle = prefs[Keys.SESSION_LAST_TITLE].orEmpty(),
                lastTrackArtist = prefs[Keys.SESSION_LAST_ARTIST].orEmpty(),
                lastTrackArtUri = prefs[Keys.SESSION_LAST_ART_URI]?.takeIf { it.isNotBlank() },
                lastIsPlaying = prefs[Keys.SESSION_LAST_IS_PLAYING] ?: false
            )
        }

    suspend fun getSavedPlaybackSession(): PlaybackSessionSnapshot = playbackSession.first()

    suspend fun savePlaybackSession(
        queueTrackIds: List<String>,
        currentIndex: Int,
        positionMs: Long,
        lastTrackTitle: String,
        lastTrackArtist: String,
        lastTrackArtUri: String?,
        isPlaying: Boolean
    ) {
        val encodedIds = Json.encodeToString(queueTrackIds)
        context.sonicBloomDataStore.edit { prefs ->
            prefs[Keys.SESSION_QUEUE_IDS] = encodedIds
            prefs[Keys.SESSION_CURRENT_INDEX] = currentIndex.coerceAtLeast(0)
            prefs[Keys.SESSION_POSITION_MS] = positionMs.coerceAtLeast(0L)
            prefs[Keys.SESSION_LAST_TITLE] = lastTrackTitle
            prefs[Keys.SESSION_LAST_ARTIST] = lastTrackArtist
            if (lastTrackArtUri != null) {
                prefs[Keys.SESSION_LAST_ART_URI] = lastTrackArtUri
            } else {
                prefs.remove(Keys.SESSION_LAST_ART_URI)
            }
            prefs[Keys.SESSION_LAST_IS_PLAYING] = isPlaying
        }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.sonicBloomDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.sonicBloomDataStore.edit { it[Keys.SHUFFLE_ENABLED] = enabled }
    }

    suspend fun setRepeatMode(mode: RepeatModeOption) {
        context.sonicBloomDataStore.edit { it[Keys.REPEAT_MODE] = mode.name }
    }

    suspend fun setEqSettings(
        enabled: Boolean,
        presetName: String,
        bassBoostStrength: Int,
        virtualizerStrength: Int = 0
    ) {
        context.sonicBloomDataStore.edit {
            it[Keys.EQ_ENABLED] = enabled
            it[Keys.EQ_PRESET] = presetName
            it[Keys.BASS_BOOST] = bassBoostStrength.coerceIn(0, 1000)
            it[Keys.VIRTUALIZER_STRENGTH] = virtualizerStrength.coerceIn(0, 1000)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.sonicBloomDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setSortSettings(option: TrackSortOption, ascending: Boolean) {
        context.sonicBloomDataStore.edit {
            it[Keys.SORT_OPTION] = option.name
            it[Keys.SORT_ASCENDING] = ascending
        }
    }

    suspend fun setCrossfadeSeconds(seconds: Int) {
        context.sonicBloomDataStore.edit {
            it[Keys.CROSSFADE_SECONDS] = seconds.coerceIn(0, 12)
        }
    }

    suspend fun setVolumeNormalizationEnabled(enabled: Boolean) {
        context.sonicBloomDataStore.edit {
            it[Keys.VOLUME_NORMALIZATION] = enabled
        }
    }

    suspend fun setMinTrackDurationSec(seconds: Int) {
        context.sonicBloomDataStore.edit {
            it[Keys.MIN_TRACK_DURATION_SEC] = seconds.coerceAtLeast(0)
        }
    }

    suspend fun setExcludedFolders(folders: Set<String>) {
        val encoded = Json.encodeToString(folders.toList().sorted())
        context.sonicBloomDataStore.edit {
            it[Keys.EXCLUDED_FOLDERS] = encoded
        }
    }
}
