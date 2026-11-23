package com.example.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import com.example.data.model.DynamicTrackColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

object ArtworkColorExtractor {

    private val memoryPaletteCache = object : LruCache<String, DynamicTrackColors>(64) {}

    fun clearMemoryCache() {
        memoryPaletteCache.evictAll()
    }

    suspend fun extractDynamicColors(
        context: Context,
        albumArtUri: String?,
        isDarkTheme: Boolean
    ): DynamicTrackColors {
        if (albumArtUri.isNullOrBlank()) return DynamicTrackColors()
        val cacheKey = "$albumArtUri|$isDarkTheme"
        memoryPaletteCache.get(cacheKey)?.let { cached ->
            return cached
        }

        return withContext(Dispatchers.IO) {
            memoryPaletteCache.get(cacheKey)?.let { return@withContext it }
            val bitmap = decodeSampledBitmap(context, albumArtUri) ?: return@withContext DynamicTrackColors()
            try {
                val rawRgb = extractVibrantDominantRgb(bitmap) ?: return@withContext DynamicTrackColors()
                val clampedRgb = clampAccentForTheme(rawRgb, isDarkTheme)
                val accent = Color(clampedRgb)
                val surfaceTint = accent.copy(alpha = if (isDarkTheme) 0.18f else 0.11f)
                val result = DynamicTrackColors(
                    accentColor = accent,
                    surfaceTint = surfaceTint
                )
                memoryPaletteCache.put(cacheKey, result)
                result
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun decodeSampledBitmap(context: Context, artUriString: String): Bitmap? {
        return runCatching {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (artUriString.startsWith("/")) {
                BitmapFactory.decodeFile(artUriString, boundsOptions)
            } else {
                val uri = Uri.parse(artUriString)
                if (uri.scheme == "file" && uri.path != null) {
                    BitmapFactory.decodeFile(uri.path, boundsOptions)
                } else {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, boundsOptions)
                    }
                }
            }
            val maxDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > 64) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            if (artUriString.startsWith("/")) {
                BitmapFactory.decodeFile(artUriString, decodeOptions)
            } else {
                val uri = Uri.parse(artUriString)
                if (uri.scheme == "file" && uri.path != null) {
                    BitmapFactory.decodeFile(File(uri.path!!).absolutePath, decodeOptions)
                } else {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, decodeOptions)
                    }
                }
            }
        }.getOrNull()
    }

    internal fun extractVibrantDominantRgb(bitmap: Bitmap): Int? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var bestBucketScore = -1f
        var bestR = 0L
        var bestG = 0L
        var bestB = 0L
        var bestCount = 0

        val bucketCounts = IntArray(36)
        val bucketR = LongArray(36)
        val bucketG = LongArray(36)
        val bucketB = LongArray(36)
        val bucketSat = FloatArray(36)

        val hsv = FloatArray(3)
        for (pixel in pixels) {
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 128) continue
            android.graphics.Color.colorToHSV(pixel, hsv)
            val sat = hsv[1]
            val value = hsv[2]
            if (value < 0.16f || value > 0.96f || sat < 0.18f) continue

            val hueBucket = ((hsv[0] / 10f).toInt()).coerceIn(0, 35)
            bucketCounts[hueBucket]++
            bucketR[hueBucket] += (pixel ushr 16) and 0xFF
            bucketG[hueBucket] += (pixel ushr 8) and 0xFF
            bucketB[hueBucket] += pixel and 0xFF
            bucketSat[hueBucket] += sat
        }

        for (i in 0 until 36) {
            val count = bucketCounts[i]
            if (count == 0) continue
            val avgSat = bucketSat[i] / count
            val score = count * (0.4f + avgSat * 1.4f)
            if (score > bestBucketScore) {
                bestBucketScore = score
                bestR = bucketR[i]
                bestG = bucketG[i]
                bestB = bucketB[i]
                bestCount = count
            }
        }

        if (bestCount == 0) return null
        val r = (bestR / bestCount).toInt().coerceIn(0, 255)
        val g = (bestG / bestCount).toInt().coerceIn(0, 255)
        val b = (bestB / bestCount).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * Clamps extracted RGB color in HSL space so accents always remain readable
     * against Dark Mode (#0D1017) and Light Mode (#F8F6F1) studio surfaces.
     */
    fun clampAccentForTheme(argb: Int, isDarkTheme: Boolean): Int {
        val r = ((argb ushr 16) and 0xFF) / 255f
        val g = ((argb ushr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f

        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        val delta = maxC - minC

        var l = (maxC + minC) / 2f
        var s = if (delta == 0f) {
            0f
        } else {
            delta / (1f - kotlin.math.abs(2f * l - 1f))
        }
        val h = when {
            delta == 0f -> 0f
            maxC == r -> 60f * (((g - b) / delta) % 6f)
            maxC == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        s = s.coerceIn(0.45f, 0.85f)
        l = if (isDarkTheme) {
            l.coerceIn(0.64f, 0.78f)
        } else {
            l.coerceIn(0.28f, 0.38f)
        }

        var argbOut = hslToArgb(h, s, l)
        for (step in 0 until 4) {
            val lum = perceivedBrightness(argbOut)
            if (isDarkTheme && lum < 0.40) {
                l = (l + 0.06f).coerceAtMost(0.86f)
                argbOut = hslToArgb(h, s, l)
            } else if (!isDarkTheme && lum > 0.54) {
                l = (l - 0.06f).coerceAtLeast(0.18f)
                argbOut = hslToArgb(h, s, l)
            } else {
                break
            }
        }
        return argbOut
    }

    private fun perceivedBrightness(argb: Int): Double {
        val r = ((argb ushr 16) and 0xFF) / 255.0
        val g = ((argb ushr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    private fun hslToArgb(h: Float, s: Float, l: Float): Int {
        val c = (1f - kotlin.math.abs(2f * l - 1f)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

object ArtDiskCacheManager {
    const val DEFAULT_MAX_BYTES: Long = 25L * 1024L * 1024L

    fun computeDirectorySizeBytes(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        return dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }

    fun enforceMaxCacheBytes(dir: File, maxBytes: Long = DEFAULT_MAX_BYTES) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles()?.filter { it.isFile }?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return

        files.sortBy { it.lastModified() }
        for (file in files) {
            if (total <= maxBytes) break
            val len = file.length()
            if (file.delete()) {
                total -= len
            }
        }
    }

    fun clearDirectory(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var removed = 0L
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                val len = file.length()
                if (file.delete()) {
                    removed += len
                }
            }
        }
        return removed
    }
}
