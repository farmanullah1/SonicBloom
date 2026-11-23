package com.example.ui.util

import android.net.Uri
import com.example.data.model.AudioTrack
import java.net.URLDecoder

data class M3uEntry(
    val location: String,
    val displayLabel: String? = null,
    val durationSec: Int = -1
)

data class M3uDocument(
    val playlistName: String?,
    val entries: List<M3uEntry>
)

data class M3uMatchResult(
    val playlistName: String?,
    val matchedTrackIds: List<String>,
    val skippedCount: Int
)

object M3uPlaylistCodec {

    fun encode(playlistName: String, tracks: List<AudioTrack>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        if (playlistName.isNotBlank()) {
            sb.appendLine("#PLAYLIST:${playlistName.trim()}")
        }
        tracks.forEach { track ->
            val durationSec = (track.durationMs / 1000L).coerceAtLeast(0L)
            sb.appendLine("#EXTINF:$durationSec,${track.artist} - ${track.title}")
            sb.appendLine(track.contentUri)
        }
        return sb.toString()
    }

    fun parse(rawText: String): M3uDocument {
        val entries = mutableListOf<M3uEntry>()
        var playlistName: String? = null
        var pendingLabel: String? = null
        var pendingDuration = -1

        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().trimStart('\uFEFF')
            if (line.isEmpty()) return@forEach

            if (line.startsWith("#PLAYLIST:", ignoreCase = true)) {
                playlistName = line.substringAfter(':').trim().takeIf { it.isNotBlank() }
                return@forEach
            }

            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val payload = line.substringAfter(':')
                val durationPart = payload.substringBefore(',').trim().toIntOrNull() ?: -1
                val labelPart = payload.substringAfter(',', "").trim().takeIf { it.isNotBlank() }
                pendingDuration = durationPart
                pendingLabel = labelPart
                return@forEach
            }

            if (line.startsWith("#")) {
                return@forEach
            }

            entries.add(
                M3uEntry(
                    location = line,
                    displayLabel = pendingLabel,
                    durationSec = pendingDuration
                )
            )
            pendingLabel = null
            pendingDuration = -1
        }

        return M3uDocument(
            playlistName = playlistName,
            entries = entries
        )
    }

    fun matchDocument(document: M3uDocument, library: List<AudioTrack>): M3uMatchResult {
        if (document.entries.isEmpty() || library.isEmpty()) {
            return M3uMatchResult(
                playlistName = document.playlistName,
                matchedTrackIds = emptyList(),
                skippedCount = document.entries.size
            )
        }

        val byUri = library.associateBy { it.contentUri }
        val byNormStem = library.groupBy { normalizeKey(it.title) }
        val byArtistTitle = library.associateBy { normalizeKey("${it.artist} ${it.title}") }

        val matchedIds = mutableListOf<String>()
        val seenIds = mutableSetOf<String>()
        var skipped = 0

        for (entry in document.entries) {
            val resolved: AudioTrack? = byUri[entry.location]
                ?: matchByPathOrFilename(entry.location, library)
                ?: matchByExtInfLabel(entry.displayLabel, entry.location, byArtistTitle, byNormStem, library)

            if (resolved != null) {
                if (seenIds.add(resolved.id)) {
                    matchedIds.add(resolved.id)
                }
            } else {
                skipped++
            }
        }

        return M3uMatchResult(
            playlistName = document.playlistName,
            matchedTrackIds = matchedIds,
            skippedCount = skipped
        )
    }

    private fun matchByPathOrFilename(location: String, library: List<AudioTrack>): AudioTrack? {
        val rawFile = extractFileName(location)
        val stem = rawFile.substringBeforeLast('.')
        val normStem = normalizeKey(stem)
        if (normStem.isBlank()) return null

        return library.firstOrNull { track ->
            val uriFile = extractFileName(track.contentUri)
            uriFile.equals(rawFile, ignoreCase = true) ||
                normalizeKey(uriFile.substringBeforeLast('.')) == normStem ||
                normalizeKey(track.title) == normStem
        }
    }

    private fun matchByExtInfLabel(
        displayLabel: String?,
        location: String,
        byArtistTitle: Map<String, AudioTrack>,
        byNormStem: Map<String, List<AudioTrack>>,
        library: List<AudioTrack>
    ): AudioTrack? {
        val candidate = displayLabel?.takeIf { it.isNotBlank() }
            ?: extractFileName(location).substringBeforeLast('.')
        val normFull = normalizeKey(candidate)
        if (normFull.isBlank()) return null

        byArtistTitle[normFull]?.let { return it }

        val titleOnly = if (candidate.contains(" - ")) {
            normalizeKey(candidate.substringAfter(" - "))
        } else {
            normFull
        }
        byNormStem[titleOnly]?.firstOrNull()?.let { return it }

        return library.firstOrNull { track ->
            val trackArtistTitle = normalizeKey("${track.artist} ${track.title}")
            trackArtistTitle == normFull || normalizeKey(track.title) == titleOnly
        }
    }

    internal fun extractFileName(rawLocation: String): String {
        val decoded = runCatching { URLDecoder.decode(rawLocation, "UTF-8") }.getOrDefault(rawLocation)
        val normalizedSlashes = decoded.replace('\\', '/')
        val lastSegment = normalizedSlashes.substringAfterLast('/')
        val fromUri = runCatching { Uri.parse(rawLocation).lastPathSegment }.getOrNull()
        return (fromUri ?: lastSegment).substringAfterLast(':').trim()
    }

    internal fun normalizeKey(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
