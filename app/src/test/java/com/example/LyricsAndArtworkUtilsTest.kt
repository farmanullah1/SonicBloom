package com.example

import com.example.ui.util.ArtworkColorExtractor
import com.example.ui.util.LyricsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAndArtworkUtilsTest {

    @Test
    fun lyricsParser_parsesSyncedLrcAndSkipsMetadata() {
        val raw = """
            [ti:Neon Echoes]
            [ar:Lumen]
            [00:02.50] First light over glass
            [00:10.00][00:40.25] Floating in the sound
            [00:18.5] Steady pulse remains
        """.trimIndent()

        val parsed = LyricsParser.parse(raw, trackId = "t1", sourceLabel = "Imported .lrc")
        assertTrue(parsed.isSynced)
        assertEquals(4, parsed.lines.size)
        assertEquals(2_500L, parsed.lines[0].timestampMs)
        assertEquals("First light over glass", parsed.lines[0].text)
        assertEquals(10_000L, parsed.lines[1].timestampMs)
        assertEquals("Floating in the sound", parsed.lines[1].text)
        assertEquals(18_500L, parsed.lines[2].timestampMs)
        assertEquals(40_250L, parsed.lines[3].timestampMs)

        assertEquals(0, LyricsParser.findActiveLineIndex(parsed.lines, 3_000L))
        assertEquals(1, LyricsParser.findActiveLineIndex(parsed.lines, 10_000L))
        assertEquals(3, LyricsParser.findActiveLineIndex(parsed.lines, 42_000L))
    }

    @Test
    fun lyricsParser_fallsBackToPlainLinesWhenNoTimestamps() {
        val raw = """
            Line one without time
            Line two without time
        """.trimIndent()

        val parsed = LyricsParser.parse(raw, trackId = "t2", sourceLabel = "Embedded ID3")
        assertFalse(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(-1, LyricsParser.findActiveLineIndex(parsed.lines, 15_000L))
    }

    @Test
    fun artworkColorExtractor_clampAccentForTheme_enforcesDarkAndLightContrast() {
        val darkBlue = 0xFF0A1033.toInt()
        val lightYellow = 0xFFFFFFCC.toInt()

        val clampedForDark = ArtworkColorExtractor.clampAccentForTheme(darkBlue, isDarkTheme = true)
        val clampedForLight = ArtworkColorExtractor.clampAccentForTheme(lightYellow, isDarkTheme = false)

        assertEquals(0xFF, (clampedForDark ushr 24) and 0xFF)
        assertEquals(0xFF, (clampedForLight ushr 24) and 0xFF)

        val darkLum = perceivedBrightness(clampedForDark)
        val lightLum = perceivedBrightness(clampedForLight)
        assertTrue("Dark theme accent should stay readable ($darkLum)", darkLum >= 0.38)
        assertTrue("Light theme accent should stay deep enough ($lightLum)", lightLum <= 0.55)

        val midCoral = 0xFFE06040.toInt()
        val coralDarkLum = perceivedBrightness(ArtworkColorExtractor.clampAccentForTheme(midCoral, isDarkTheme = true))
        val coralLightLum = perceivedBrightness(ArtworkColorExtractor.clampAccentForTheme(midCoral, isDarkTheme = false))
        assertTrue("Same-hue dark accent should be brighter than light accent", coralDarkLum > coralLightLum + 0.12)
    }

    private fun perceivedBrightness(argb: Int): Double {
        val r = ((argb ushr 16) and 0xFF) / 255.0
        val g = ((argb ushr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
