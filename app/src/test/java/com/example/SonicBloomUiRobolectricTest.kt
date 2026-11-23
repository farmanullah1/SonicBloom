package com.example

import android.content.ComponentName
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AudioTrack
import com.example.data.model.DynamicTrackColors
import com.example.data.model.EqualizerBandUi
import com.example.data.model.EqualizerState
import com.example.data.model.LyricsLine
import com.example.data.model.ThemeMode
import com.example.data.model.TrackFilterOption
import com.example.data.model.TrackLyrics
import com.example.data.model.WaveformData
import com.example.playback.PlaybackUiState
import com.example.ui.components.EqualizerBottomSheet
import com.example.ui.components.StudioSettingsSheet
import com.example.ui.components.TrackDetailsDialog
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.TracksScreen
import com.example.ui.theme.SonicBloomTheme
import com.example.ui.theme.resolveIsDarkTheme
import com.example.ui.viewmodel.LibraryUiState
import com.example.widget.SonicBloomWidgetProvider
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonicBloomUiRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appNameMatchesBranding_andWidgetIsRegistered() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("SonicBloom", context.getString(R.string.app_name))

        val receiver = context.packageManager.getReceiverInfo(
            ComponentName(context, SonicBloomWidgetProvider::class.java),
            0
        )
        assertNotNull(receiver)
    }

    @Test
    fun tracksScreen_displaysFiltersAndHandlesFilterClicksInDarkMode() {
        var selectedFilter: TrackFilterOption? = null
        var clearedFilters = false

        val track = sampleTrack("track_1", "Velvet Echo")
        val state = LibraryUiState(
            allTracks = listOf(track),
            filteredTracks = listOf(track),
            themeMode = ThemeMode.DARK,
            hasAudioPermission = true
        )

        composeTestRule.setContent {
            SonicBloomTheme(darkTheme = resolveIsDarkTheme(state.themeMode)) {
                TracksScreen(
                    libraryState = state,
                    currentTrackId = track.id,
                    isPlaying = false,
                    onCycleTheme = {},
                    onOpenSettings = {},
                    onSearchQueryChange = {},
                    onFilterOptionChange = { selectedFilter = it },
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    onClearFilters = { clearedFilters = true },
                    onRescanLibrary = {},
                    onDismissError = {},
                    onImportAudioFiles = {},
                    onRequestAudioPermission = {},
                    onPlayTracks = { _, _ -> },
                    onToggleFavorite = {},
                    onTrackMoreClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("top_bar_theme_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_bar_settings_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_chip_MP3").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("tracks_sort_menu_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tracks_sort_direction_button").assertIsDisplayed()
        composeTestRule.onNodeWithText("Velvet Echo").assertIsDisplayed()

        assertEquals(TrackFilterOption.MP3, selectedFilter)
        assertFalse(clearedFilters)
    }

    @Test
    fun tracksScreen_emptyFilteredStateShowsClearFiltersButtonInLightMode() {
        var cleared = false

        val state = LibraryUiState(
            allTracks = emptyList(),
            filteredTracks = emptyList(),
            filterOption = TrackFilterOption.LOSSLESS,
            themeMode = ThemeMode.LIGHT,
            hasAudioPermission = true,
            libraryError = "Permission denied"
        )

        composeTestRule.setContent {
            SonicBloomTheme(darkTheme = resolveIsDarkTheme(state.themeMode)) {
                TracksScreen(
                    libraryState = state,
                    currentTrackId = null,
                    isPlaying = false,
                    onCycleTheme = {},
                    onOpenSettings = {},
                    onSearchQueryChange = {},
                    onFilterOptionChange = {},
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    onClearFilters = { cleared = true },
                    onRescanLibrary = {},
                    onDismissError = {},
                    onImportAudioFiles = {},
                    onRequestAudioPermission = {},
                    onPlayTracks = { _, _ -> },
                    onToggleFavorite = {},
                    onTrackMoreClick = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_error_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tracks_clear_filters_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        assertTrue(cleared)
    }

    @Test
    fun tracksScreen_batchSelectionBarTogglesAndTriggersActions() {
        val t1 = sampleTrack("t1", "Midnight Bloom")
        val t2 = sampleTrack("t2", "Solar Drift")
        var selectedIds = setOf<String>()
        var batchQueuedNext = false

        val state = LibraryUiState(
            allTracks = listOf(t1, t2),
            filteredTracks = listOf(t1, t2),
            themeMode = ThemeMode.DARK,
            hasAudioPermission = true
        )

        composeTestRule.setContent {
            SonicBloomTheme(darkTheme = true) {
                TracksScreen(
                    libraryState = state,
                    currentTrackId = t1.id,
                    isPlaying = false,
                    onCycleTheme = {},
                    onOpenSettings = {},
                    onSearchQueryChange = {},
                    onFilterOptionChange = {},
                    onSortOptionChange = {},
                    onToggleSortDirection = {},
                    onClearFilters = {},
                    onRescanLibrary = {},
                    onDismissError = {},
                    onImportAudioFiles = {},
                    onRequestAudioPermission = {},
                    onPlayTracks = { _, _ -> },
                    onToggleFavorite = {},
                    onTrackMoreClick = {},
                    selectedTrackIds = selectedIds,
                    onSelectAllTracks = { list -> selectedIds = list.map { it.id }.toSet() },
                    onBatchPlayNext = { batchQueuedNext = true },
                    onClearSelection = { selectedIds = emptySet() }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tracks_select_mode_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("batch_selection_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("batch_play_next_button").performClick()
        assertTrue(batchQueuedNext)
    }

    @Test
    fun nowPlayingScreen_togglesLyricsPanelWithSyncedLines() {
        val track = sampleTrack("np1", "Amber Skyline")
        val lyrics = TrackLyrics(
            trackId = track.id,
            lines = listOf(
                LyricsLine(1_000L, "Golden waves across the room"),
                LyricsLine(10_000L, "Analog studio in full bloom")
            ),
            isSynced = true,
            sourceLabel = "Synced .lrc"
        )

        composeTestRule.setContent {
            SonicBloomTheme(darkTheme = true) {
                NowPlayingScreen(
                    playbackState = PlaybackUiState(
                        currentTrack = track,
                        queue = listOf(track),
                        currentIndex = 0,
                        isPlaying = false,
                        positionMs = 2_000L,
                        durationMs = 195_000L
                    ),
                    onClose = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSeekRelative = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onToggleFavorite = {},
                    onOpenEqualizer = {},
                    onOpenSleepTimer = {},
                    onOpenSpeedDialog = {},
                    onOpenQueue = {},
                    onMoreOptions = {},
                    onClearError = {},
                    dynamicColors = DynamicTrackColors(
                        accentColor = Color(0xFFFF7B54),
                        surfaceTint = Color(0xFF452319)
                    ),
                    trackLyrics = lyrics
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("now_playing_waveform").assertIsDisplayed()
        composeTestRule.onNodeWithTag("now_playing_lyrics_toggle").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("now_playing_lyrics_panel").assertIsDisplayed()
        composeTestRule.onNodeWithText("Golden waves across the room").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w960dp-h720dp")
    fun playlistsScreen_wideViewportRendersTwoPaneWithNewSmartMixes() {
        val losslessTrack = sampleTrack("fl1", "Studio Master", mime = "audio/flac", bitrate = 1100)
        val state = LibraryUiState(
            allTracks = listOf(losslessTrack),
            losslessVaultTracks = listOf(losslessTrack),
            unplayedGemsTracks = listOf(losslessTrack),
            favoriteTracks = listOf(losslessTrack),
            themeMode = ThemeMode.LIGHT
        )

        composeTestRule.setContent {
            SonicBloomTheme(darkTheme = false) {
                PlaylistsScreen(
                    libraryState = state,
                    onCycleTheme = {},
                    onOpenSettings = {},
                    onOpenSmartPlaylist = {},
                    onOpenPlaylist = {},
                    isDuplicatePlaylistName = { _, _ -> false },
                    onCreatePlaylist = {},
                    onRenamePlaylist = { _, _ -> },
                    onDeletePlaylist = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("smart_playlist_lossless_vault").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smart_playlist_unplayed_gems").assertIsDisplayed()
        composeTestRule.onNodeWithTag("import_m3u_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("playlists_detail_pane").assertIsDisplayed()
    }

    @Test
    fun nowPlayingScreen_displaysAbLoopControlsAndWaveformData() {
        val track = sampleTrack("ab1", "Horizon Pulse")
        var abClicked = false
        var abCleared = false

        composeTestRule.setContent {
            SonicBloomTheme(darkTheme = true) {
                NowPlayingScreen(
                    playbackState = PlaybackUiState(
                        currentTrack = track,
                        queue = listOf(track),
                        currentIndex = 0,
                        isPlaying = true,
                        positionMs = 45_000L,
                        durationMs = 180_000L,
                        loopStartMs = 20_000L,
                        loopEndMs = 60_000L
                    ),
                    onClose = {},
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSeekRelative = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onToggleFavorite = {},
                    onOpenEqualizer = {},
                    onOpenSleepTimer = {},
                    onOpenSpeedDialog = {},
                    onOpenQueue = {},
                    onMoreOptions = {},
                    onClearError = {},
                    waveform = WaveformData(
                        trackId = track.id,
                        amplitudes = List(56) { if (it % 2 == 0) 0.8f else 0.4f },
                        isRealPcm = true
                    ),
                    onAbLoopClick = { abClicked = true },
                    onClearAbLoop = { abCleared = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("now_playing_waveform").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("now_playing_ab_loop_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("now_playing_ab_clear_button").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(abClicked)
        assertTrue(abCleared)
    }

    @Test
    fun trackDetailsDialog_allowsTagOverrideEditAndReset() {
        var editedTitle: String? = null
        var resetCalled = false
        val track = sampleTrack("tag1", "Original Name").copy(isMetadataOverridden = true)

        composeTestRule.setContent {
            SonicBloomTheme(darkTheme = true) {
                TrackDetailsDialog(
                    track = track,
                    onDismiss = {},
                    onSaveMetadataOverride = { t, _, _ -> editedTitle = t },
                    onResetMetadataOverride = { resetCalled = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("track_details_reset_tags_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("track_details_edit_tags_button").performClick()
        composeTestRule.onNodeWithTag("edit_tag_title_input").performTextReplacement("Remastered Name")
        composeTestRule.onNodeWithTag("track_details_save_tags_button").performClick()
        assertEquals("Remastered Name", editedTitle)
        assertFalse(resetCalled)
    }

    @Test
    fun equalizerBottomSheet_supportsCustomPresetSaveAndVirtualizer() {
        var savedPresetName: String? = null
        val eqState = EqualizerState(
            isEnabled = true,
            isAvailable = true,
            bands = listOf(EqualizerBandUi(0, 60, 200)),
            activePreset = "My Mix",
            customPresets = listOf("My Mix"),
            virtualizerStrength = 400,
            virtualizerAvailable = true
        )

        composeTestRule.setContent {
            SonicBloomTheme(darkTheme = true) {
                EqualizerBottomSheet(
                    equalizerState = eqState,
                    presets = listOf("Flat", "Rock"),
                    onDismiss = {},
                    onToggleEnabled = {},
                    onSelectPreset = {},
                    onBandLevelChange = { _, _ -> },
                    onBassBoostChange = {},
                    onVirtualizerChange = {},
                    onSaveCustomPreset = { savedPresetName = it },
                    onDeleteCustomPreset = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("eq_custom_preset_My Mix").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_virtualizer_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("eq_save_preset_button").performClick()
        composeTestRule.onNodeWithTag("eq_save_preset_input").performTextReplacement("Warm Vinyl")
        composeTestRule.onNodeWithTag("eq_save_preset_confirm").performClick()
        assertEquals("Warm Vinyl", savedPresetName)
    }

    @Test
    fun studioSettingsSheet_displaysV4ControlsAndClearsCache() {
        var clearedCache = false
        val track = sampleTrack("tag1", "Original Name")
        val libState = LibraryUiState(
            allTracks = listOf(track),
            crossfadeSeconds = 4,
            volumeNormalizationEnabled = true,
            minTrackDurationSec = 30,
            artCacheSizeBytes = 2048L
        )

        composeTestRule.setContent {
            SonicBloomTheme(darkTheme = false) {
                StudioSettingsSheet(
                    libraryState = libState,
                    onDismiss = {},
                    onSelectThemeMode = {},
                    onOpenEqualizer = {},
                    onRescanLibrary = {},
                    onImportAudioFiles = {},
                    onClearArtworkCache = { clearedCache = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_crossfade_slider").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_normalization_switch").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_clip_filter_30").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_clear_cache_button").performScrollTo().performClick()
        assertTrue(clearedCache)
    }

    private fun sampleTrack(
        id: String,
        title: String,
        mime: String = "audio/mpeg",
        bitrate: Int = 320
    ) = AudioTrack(
        id = id,
        contentUri = "content://media/$id",
        title = title,
        artist = "Kairo",
        album = "Afterglow",
        albumId = 1L,
        durationMs = 195_000L,
        albumArtUri = null,
        folderName = "Studio",
        mimeType = mime,
        bitrateKbps = bitrate,
        sizeBytes = 6_200_000L,
        dateAddedSec = 1700000000L
    )
}
