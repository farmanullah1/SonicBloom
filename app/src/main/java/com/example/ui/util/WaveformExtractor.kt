package com.example.ui.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class ExtractedWaveform(
    val amplitudes: List<Float>,
    val suggestedGainDb: Float
)

object WaveformExtractor {

    const val DEFAULT_BUCKET_COUNT = 56

    fun fallbackAmplitudes(trackId: String, bucketCount: Int = DEFAULT_BUCKET_COUNT): List<Float> {
        val seed = trackId.hashCode()
        return List(bucketCount.coerceAtLeast(16)) { idx ->
            val raw = abs(sin(idx * 0.44 + (seed % 19) * 0.31))
            (0.22f + 0.70f * raw.toFloat()).coerceIn(0.18f, 0.96f)
        }
    }

    fun encodePeaksCsv(amplitudes: List<Float>): String {
        return amplitudes.joinToString(",") { value ->
            ((value.coerceIn(0.10f, 1.0f) * 1000f).toInt() / 1000f).toString()
        }
    }

    fun decodePeaksCsv(csv: String): List<Float> {
        if (csv.isBlank()) return emptyList()
        return csv.split(',')
            .mapNotNull { it.trim().toFloatOrNull() }
            .map { it.coerceIn(0.12f, 1.0f) }
    }

    fun normalizeBucketEnergies(energies: List<Float>): List<Float> {
        if (energies.isEmpty()) return emptyList()
        val maxEnergy = energies.maxOrNull()?.takeIf { it > 0.0001f } ?: 1f
        return energies.map { e ->
            val scaled = (e / maxEnergy).coerceIn(0f, 1f)
            (0.16f + 0.84f * sqrt(scaled)).coerceIn(0.16f, 1.0f)
        }
    }

    fun computeSuggestedGainDb(overallRmsNormalized: Float, maxPeakNormalized: Float): Float {
        if (overallRmsNormalized <= 0.0005f) return 0f
        val rmsDb = 20f * log10(overallRmsNormalized)
        val targetRmsDb = -14.5f
        val desiredGain = (targetRmsDb - rmsDb).coerceIn(-6.0f, 8.0f)
        val peakDb = if (maxPeakNormalized > 0.0005f) 20f * log10(maxPeakNormalized) else 0f
        val safeHeadroomMax = (-0.5f - peakDb).coerceAtLeast(-6.0f)
        return desiredGain.coerceAtMost(safeHeadroomMax).coerceIn(-6.0f, 8.0f)
    }

    suspend fun extractFromAudioUri(
        context: Context,
        uri: Uri,
        durationMs: Long,
        bucketCount: Int = DEFAULT_BUCKET_COUNT
    ): ExtractedWaveform? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || audioFormat == null) return@withContext null

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext null
            val formatDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                durationMs * 1000L
            }.coerceAtLeast(1_000_000L)

            extractor.selectTrack(audioTrackIndex)
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(audioFormat, null, null, 0)
                start()
            }

            val buckets = MutableList(bucketCount) { 0f }
            val bufferInfo = MediaCodec.BufferInfo()
            var overallSumSq = 0.0
            var overallSampleCount = 0L
            var globalPeak = 0f
            var decodedBuckets = 0

            for (bucket in 0 until bucketCount) {
                coroutineContext.ensureActive()
                val seekUs = ((bucket.toDouble() + 0.2) / bucketCount.toDouble() * formatDurationUs).toLong()
                    .coerceIn(0L, (formatDurationUs - 50_000L).coerceAtLeast(0L))
                extractor.seekTo(seekUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()

                var sumSq = 0.0
                var sampleCount = 0L
                var eosReached = false
                var inputPackets = 0

                while (!eosReached && sampleCount < 2200 && inputPackets < 5) {
                    val inIdx = codec.dequeueInputBuffer(4_000L)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                eosReached = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                                inputPackets++
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 4_000L)
                    if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            while (shortBuf.hasRemaining()) {
                                val normalized = shortBuf.get() / 32768.0
                                val absVal = abs(normalized).toFloat()
                                if (absVal > globalPeak) globalPeak = absVal
                                sumSq += normalized * normalized
                                sampleCount++
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                    } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER && inputPackets >= 4) {
                        break
                    }
                }

                if (sampleCount > 0L) {
                    val rms = sqrt(sumSq / sampleCount).toFloat()
                    buckets[bucket] = rms
                    overallSumSq += sumSq
                    overallSampleCount += sampleCount
                    decodedBuckets++
                }
            }

            if (decodedBuckets < bucketCount / 4) return@withContext null

            // Smooth any missing individual bucket using neighbor interpolation
            for (i in buckets.indices) {
                if (buckets[i] == 0f) {
                    val prev = if (i > 0) buckets[i - 1] else 0.1f
                    val next = if (i < buckets.lastIndex) buckets[i + 1] else prev
                    buckets[i] = max(0.05f, (prev + next) / 2f)
                }
            }

            val overallRms = if (overallSampleCount > 0L) {
                sqrt(overallSumSq / overallSampleCount).toFloat()
            } else {
                0.2f
            }

            ExtractedWaveform(
                amplitudes = normalizeBucketEnergies(buckets),
                suggestedGainDb = computeSuggestedGainDb(overallRms, globalPeak)
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }
}
