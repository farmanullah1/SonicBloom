package com.example.ui.util

import android.content.Context
import android.net.Uri
import com.example.data.model.LyricsLine
import com.example.data.model.TrackLyrics
import java.nio.charset.Charset

object LyricsParser {

    private val timestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val metadataTagRegex = Regex("""^\[(ar|ti|al|by|offset|length|re|ve):.*]$""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String, trackId: String, sourceLabel: String): TrackLyrics {
        val syncedLines = mutableListOf<LyricsLine>()
        val plainLines = mutableListOf<LyricsLine>()

        rawText.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || metadataTagRegex.matches(trimmed)) return@forEach

            val matches = timestampRegex.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val lyricText = timestampRegex.replace(trimmed, "").trim()
                matches.forEach { match ->
                    val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                    val fracStr = match.groupValues[3]
                    val millis = when (fracStr.length) {
                        0 -> 0L
                        1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                        else -> fracStr.take(3).toLongOrNull() ?: 0L
                    }
                    val totalMs = (minutes * 60_000L) + (seconds * 1_000L) + millis
                    if (lyricText.isNotEmpty()) {
                        syncedLines.add(LyricsLine(timestampMs = totalMs, text = lyricText))
                    }
                }
            } else {
                plainLines.add(LyricsLine(timestampMs = null, text = trimmed))
            }
        }

        return if (syncedLines.isNotEmpty()) {
            TrackLyrics(
                trackId = trackId,
                lines = syncedLines.sortedBy { it.timestampMs ?: 0L },
                isSynced = true,
                sourceLabel = sourceLabel
            )
        } else {
            TrackLyrics(
                trackId = trackId,
                lines = plainLines,
                isSynced = false,
                sourceLabel = sourceLabel
            )
        }
    }

    fun findActiveLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var active = -1
        for (i in lines.indices) {
            val ts = lines[i].timestampMs ?: continue
            if (ts <= positionMs + 250L) {
                active = i
            } else {
                break
            }
        }
        return active
    }

    /**
     * Best-effort lightweight ID3v2 USLT (unsynchronized lyrics) frame extractor
     * from the header region of local MP3 audio streams.
     */
    fun extractEmbeddedId3Lyrics(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = ByteArray(10)
                if (stream.read(header) < 10) return null
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                    return null
                }
                val majorVersion = header[3].toInt() and 0xFF
                if (majorVersion !in 3..4) return null

                val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                    ((header[7].toInt() and 0x7F) shl 14) or
                    ((header[8].toInt() and 0x7F) shl 7) or
                    (header[9].toInt() and 0x7F)

                val boundedSize = tagSize.coerceAtMost(512 * 1024)
                val tagBytes = ByteArray(boundedSize)
                var readTotal = 0
                while (readTotal < boundedSize) {
                    val count = stream.read(tagBytes, readTotal, boundedSize - readTotal)
                    if (count <= 0) break
                    readTotal += count
                }

                var pos = 0
                while (pos + 10 <= readTotal) {
                    val frameId = String(tagBytes, pos, 4, Charsets.ISO_8859_1)
                    if (frameId.all { it == '\u0000' }) break
                    val frameSize = if (majorVersion == 4) {
                        ((tagBytes[pos + 4].toInt() and 0x7F) shl 21) or
                            ((tagBytes[pos + 5].toInt() and 0x7F) shl 14) or
                            ((tagBytes[pos + 6].toInt() and 0x7F) shl 7) or
                            (tagBytes[pos + 7].toInt() and 0x7F)
                    } else {
                        ((tagBytes[pos + 4].toInt() and 0xFF) shl 24) or
                            ((tagBytes[pos + 5].toInt() and 0xFF) shl 16) or
                            ((tagBytes[pos + 6].toInt() and 0xFF) shl 8) or
                            (tagBytes[pos + 7].toInt() and 0xFF)
                    }
                    if (frameSize <= 0 || pos + 10 + frameSize > readTotal) break
                    if (frameId == "USLT") {
                        return decodeUsltFrame(tagBytes, pos + 10, frameSize)
                    }
                    pos += 10 + frameSize
                }
                null
            }
        }.getOrNull()
    }

    private fun decodeUsltFrame(data: ByteArray, offset: Int, length: Int): String? {
        if (length < 5) return null
        val encoding = data[offset].toInt() and 0xFF
        val charset: Charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val contentStart = offset + 4 // 1 byte encoding + 3 bytes language
        var idx = contentStart
        val end = offset + length
        val isTwoByte = encoding == 1 || encoding == 2
        if (isTwoByte) {
            while (idx + 1 < end) {
                if (data[idx] == 0.toByte() && data[idx + 1] == 0.toByte()) {
                    idx += 2
                    break
                }
                idx += 2
            }
        } else {
            while (idx < end) {
                if (data[idx] == 0.toByte()) {
                    idx += 1
                    break
                }
                idx += 1
            }
        }
        if (idx >= end) idx = contentStart
        return String(data, idx, end - idx, charset)
            .replace("\u0000", "")
            .trim()
            .takeIf { it.isNotEmpty() }
    }
}
