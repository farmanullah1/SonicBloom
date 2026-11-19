package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.RepeatModeOption
import com.example.data.model.ThemeMode
import com.example.data.model.TrackSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val sortOption: TrackSortOption = TrackSortOption.TITLE,
    val sortAscending: Boolean = true
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val SORT_ASCENDING = booleanPreferencesKey("sort_ascending")
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

            PlayerPreferences(
                playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
                shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
                repeatMode = repeatMode,
                eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
                eqPresetName = prefs[Keys.EQ_PRESET] ?: "Flat",
                bassBoostStrength = prefs[Keys.BASS_BOOST] ?: 0,
                themeMode = themeMode,
                sortOption = sortOption,
                sortAscending = prefs[Keys.SORT_ASCENDING] ?: true
            )
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

    suspend fun setEqSettings(enabled: Boolean, presetName: String, bassBoostStrength: Int) {
        context.sonicBloomDataStore.edit {
            it[Keys.EQ_ENABLED] = enabled
            it[Keys.EQ_PRESET] = presetName
            it[Keys.BASS_BOOST] = bassBoostStrength
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
}
