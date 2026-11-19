package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AudioTrack
import com.example.data.model.ThemeMode
import com.example.data.model.TrackFilterOption
import com.example.ui.screens.TracksScreen
import com.example.ui.theme.SonicBloomTheme
import com.example.ui.theme.resolveIsDarkTheme
import com.example.ui.viewmodel.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun appNameMatchesBranding() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("SonicBloom", context.getString(R.string.app_name))
    }

    @Test
    fun tracksScreen_displaysFiltersAndHandlesFilterClicksInDarkMode() {
        var selectedFilter: TrackFilterOption? = null
        var clearedFilters = false

        val track = AudioTrack(
            id = "track_1",
            contentUri = "content://media/1",
            title = "Velvet Echo",
            artist = "Kairo",
            album = "Afterglow",
            albumId = 1L,
            durationMs = 195_000L,
            albumArtUri = null,
            folderName = "Studio",
            mimeType = "audio/mpeg",
            bitrateKbps = 320,
            sizeBytes = 6_200_000L,
            dateAddedSec = 1700000000L
        )

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
}
