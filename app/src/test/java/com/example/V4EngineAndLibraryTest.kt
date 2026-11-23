package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.CustomEqPresetEntity
import com.example.data.local.PlayerPreferences
import com.example.data.local.SonicBloomDatabase
import com.example.data.local.TrackMetadataOverrideEntity
import com.example.data.local.WaveformPeakEntity
import com.example.data.model.AudioTrack
import com.example.data.model.BrowseTab
import com.example.data.model.ThemeMode
import com.example.data.model.TrackFilterOption
import com.example.data.model.TrackSortOption
import com.example.playback.AbLoopCoordinator
import com.example.ui.util.M3uPlaylistCodec
import com.example.ui.util.WaveformExtractor
import com.example.ui.viewmodel.computeLibraryUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class V4EngineAndLibraryTest {

    @Test
    fun m3uPlaylistCodec_roundTripEncodeParseAndMatchWorksAcrossUriAndLabel() {
        val tracks = listOf(
            sampleTrack(id = "1", title = "Neon Skyline", artist = "Kairo", folder = "Studio"),
            sampleTrack(id = "2", title = "Velvet Echo", artist = "Aria", folder = "Live")
        )
        val encoded = M3uPlaylistCodec.encode("Late Night Mix", tracks)
        assertTrue(encoded.contains("#EXTM3U"))
        assertTrue(encoded.contains("#PLAYLIST:Late Night Mix"))

        val parsed = M3uPlaylistCodec.parse(encoded)
        assertEquals("Late Night Mix", parsed.playlistName)
        assertEquals(2, parsed.entries.size)

        val externalM3u = """
            #EXTM3U
            #PLAYLIST:Imported Set
            #EXTINF:195,Aria - Velvet Echo
            /storage/emulated/0/Music/unknown_file_name.mp3
            #EXTINF:210,Unknown Artist - Missing Song
            /storage/emulated/0/Music/missing_song.mp3
        """.trimIndent()

        val extDoc = M3uPlaylistCodec.parse(externalM3u)
        val matched = M3uPlaylistCodec.matchDocument(extDoc, tracks)
        assertEquals("Imported Set", matched.playlistName)
        assertEquals(listOf("2"), matched.matchedTrackIds)
        assertEquals(1, matched.skippedCount)
    }

    @Test
    fun waveformExtractor_csvRoundTripNormalizationAndSuggestedGainAreBounded() {
        val normalized = WaveformExtractor.normalizeBucketEnergies(listOf(0.05f, 0.25f, 0.5f, 0.0f))
        assertEquals(4, normalized.size)
        assertEquals(1.0f, normalized[2], 0.001f)
        assertTrue(normalized.all { it in 0.16f..1.0f })

        val csv = WaveformExtractor.encodePeaksCsv(normalized)
        val decoded = WaveformExtractor.decodePeaksCsv(csv)
        assertEquals(4, decoded.size)
        assertEquals(normalized[1], decoded[1], 0.01f)

        val quietGain = WaveformExtractor.computeSuggestedGainDb(
            overallRmsNormalized = 0.04f,
            maxPeakNormalized = 0.20f
        )
        assertTrue("Quiet track should get positive boost ($quietGain)", quietGain > 0f && quietGain <= 8f)

        val hotTrackGain = WaveformExtractor.computeSuggestedGainDb(
            overallRmsNormalized = 0.65f,
            maxPeakNormalized = 0.98f
        )
        assertTrue("Hot track should get negative/zero headroom trim ($hotTrackGain)", hotTrackGain <= 0f && hotTrackGain >= -6f)
    }

    @Test
    fun abLoopCoordinator_setsReordersClearsAndDetectsWrap() {
        val dur = 180_000L
        val step1 = AbLoopCoordinator.nextLoopBounds(null, null, cursorMs = 30_000L, durationMs = dur)
        assertEquals(30_000L to null, step1)

        // Too-close B point keeps waiting for a valid B
        val tooClose = AbLoopCoordinator.nextLoopBounds(step1.first, step1.second, cursorMs = 30_200L, durationMs = dur)
        assertEquals(30_000L to null, tooClose)

        // Setting B before A auto-swaps so start <= end
        val swapped = AbLoopCoordinator.nextLoopBounds(step1.first, step1.second, cursorMs = 15_000L, durationMs = dur)
        assertEquals(15_000L to 30_000L, swapped)

        assertFalse(AbLoopCoordinator.shouldWrapToLoopStart(29_500L, swapped.first, swapped.second))
        assertTrue(AbLoopCoordinator.shouldWrapToLoopStart(30_100L, swapped.first, swapped.second))

        // Third tap clears active A-B loop
        val cleared = AbLoopCoordinator.nextLoopBounds(swapped.first, swapped.second, cursorMs = 20_000L, durationMs = dur)
        assertEquals(null to null, cleared)
    }

    @Test
    fun computeLibraryUiState_appliesShortClipFilterAndFolderExclusions() {
        val longMusic = sampleTrack(id = "1", title = "Long Track", artist = "A", folder = "Albums", durationMs = 180_000L)
        val shortClip = sampleTrack(id = "2", title = "Voice Memo", artist = "B", folder = "Recordings", durationMs = 12_000L)
        val ringtone = sampleTrack(id = "3", title = "Alert Tone", artist = "C", folder = "Ringtones", durationMs = 45_000L)

        val state = computeLibraryUiState(
            rawTracks = listOf(longMusic, shortClip, ringtone),
            playlists = emptyList(),
            isScanning = false,
            query = "",
            browseQuery = "",
            filterOption = TrackFilterOption.ALL,
            sortOption = TrackSortOption.TITLE,
            sortAscending = true,
            browseTab = BrowseTab.ALBUMS,
            hasPerm = true,
            libError = null,
            themeMode = ThemeMode.DARK,
            artCacheSizeBytes = 1024L,
            prefs = PlayerPreferences(
                minTrackDurationSec = 30,
                excludedFolders = setOf("Ringtones")
            )
        )

        assertEquals(listOf("1"), state.allTracks.map { it.id })
        assertEquals(listOf("Albums", "Recordings", "Ringtones"), state.availableFolders)
        assertEquals(1024L, state.artCacheSizeBytes)
    }

    @Test
    fun roomDao_persistsV4MetadataOverridesCustomEqPresetsAndWaveforms() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SonicBloomDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = db.sonicBloomDao()

            dao.upsertMetadataOverride(
                TrackMetadataOverrideEntity(
                    trackId = "t1",
                    title = "Fixed Title",
                    artist = "Fixed Artist",
                    album = "Fixed Album"
                )
            )
            val overrides = dao.observeMetadataOverrides().first()
            assertEquals(1, overrides.size)
            assertEquals("Fixed Title", overrides.first().title)

            dao.upsertCustomEqPreset(
                CustomEqPresetEntity(
                    name = "Studio Warm",
                    bandLevelsCsv = "200,100,0,-100,150",
                    bassBoostStrength = 420,
                    virtualizerStrength = 310
                )
            )
            val presets = dao.observeCustomEqPresets().first()
            assertEquals(1, presets.size)
            assertEquals(310, presets.first().virtualizerStrength)

            dao.upsertWaveformPeak(
                WaveformPeakEntity(
                    trackId = "t1",
                    fileSignature = "210000_8400000",
                    peaksCsv = "0.2,0.8,0.5",
                    suggestedGainDb = 2.4f
                )
            )
            val waveform = dao.getWaveformPeak("t1")
            assertNotNull(waveform)
            assertEquals(2.4f, waveform!!.suggestedGainDb, 0.01f)

            dao.deleteMetadataOverride("t1")
            dao.deleteCustomEqPreset("Studio Warm")
            assertTrue(dao.observeMetadataOverrides().first().isEmpty())
            assertTrue(dao.observeCustomEqPresets().first().isEmpty())
        } finally {
            db.close()
        }
    }

    private fun sampleTrack(
        id: String,
        title: String,
        artist: String,
        folder: String,
        durationMs: Long = 195_000L
    ) = AudioTrack(
        id = id,
        contentUri = "content://media/external/audio/media/$id",
        title = title,
        artist = artist,
        album = "Afterglow",
        albumId = 1L,
        durationMs = durationMs,
        albumArtUri = null,
        folderName = folder,
        mimeType = "audio/mpeg",
        bitrateKbps = 320,
        sizeBytes = 6_200_000L,
        dateAddedSec = 1700000000L
    )
}
