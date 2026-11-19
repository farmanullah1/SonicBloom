package com.example.ui.util

import java.util.Locale

fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Unknown size"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = bytes / 1024.0
        String.format(Locale.US, "%.0f KB", kb)
    }
}

fun formatFrequencyHz(freqHz: Int): String {
    return if (freqHz >= 1000) {
        val khz = freqHz / 1000f
        if (khz % 1f == 0f) {
            "${khz.toInt()}kHz"
        } else {
            String.format(Locale.US, "%.1fkHz", khz)
        }
    } else {
        "${freqHz}Hz"
    }
}
