package com.example.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import com.example.data.model.CustomEqPreset
import com.example.data.model.EqualizerBandUi
import com.example.data.model.EqualizerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class AudioEffectManager {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    private var normalizationEnabled: Boolean = false
    private var currentSuggestedGainDb: Float = 0f
    private var customPresetsMap: Map<String, CustomEqPreset> = emptyMap()

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    val builtInPresetNames: List<String> = listOf(
        "Flat",
        "Bass Boost",
        "Acoustic",
        "Electronic",
        "Vocal",
        "Late Night"
    )

    val presetNames: List<String>
        get() = builtInPresetNames

    fun attachToAudioSession(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == currentSessionId) return
        releaseEffects()
        currentSessionId = audioSessionId

        runCatching {
            val eq = Equalizer(0, audioSessionId).apply {
                enabled = _state.value.isEnabled
            }
            val bb = runCatching {
                BassBoost(0, audioSessionId).apply {
                    enabled = _state.value.isEnabled
                    if (strengthSupported) {
                        setStrength(_state.value.bassBoostStrength.toShort())
                    }
                }
            }.getOrNull()

            val virt = runCatching {
                Virtualizer(0, audioSessionId).apply {
                    enabled = _state.value.isEnabled && _state.value.virtualizerStrength > 0
                    if (strengthSupported) {
                        setStrength(_state.value.virtualizerStrength.toShort())
                    }
                }
            }.getOrNull()

            val enhancer = runCatching {
                LoudnessEnhancer(audioSessionId).apply {
                    enabled = normalizationEnabled
                    if (normalizationEnabled) {
                        setTargetGain((currentSuggestedGainDb * 100f).roundToInt())
                    }
                }
            }.getOrNull()

            equalizer = eq
            bassBoost = bb
            virtualizer = virt
            loudnessEnhancer = enhancer

            val range = eq.bandLevelRange
            val minMb = range[0].toInt()
            val maxMb = range[1].toInt()
            val count = eq.numberOfBands.toInt()
            val bands = (0 until count).map { idx ->
                val band = idx.toShort()
                EqualizerBandUi(
                    index = idx,
                    centerFreqHz = eq.getCenterFreq(band) / 1000,
                    levelMb = eq.getBandLevel(band).toInt()
                )
            }

            _state.value = _state.value.copy(
                isAvailable = count > 0,
                minLevelMb = minMb,
                maxLevelMb = maxMb,
                bands = bands,
                virtualizerAvailable = virt != null,
                normalizationAvailable = enhancer != null,
                statusMessage = null
            )
            applyPreset(_state.value.activePreset)
        }.onFailure { err ->
            _state.value = _state.value.copy(
                isAvailable = false,
                virtualizerAvailable = false,
                statusMessage = err.localizedMessage ?: "Hardware equalizer is unavailable on this audio output."
            )
        }
    }

    fun updateCustomPresets(presets: List<CustomEqPreset>) {
        val wasEmpty = customPresetsMap.isEmpty()
        customPresetsMap = presets.associateBy { it.name }
        _state.value = _state.value.copy(customPresets = presets.map { it.name })
        val active = _state.value.activePreset
        if (wasEmpty && customPresetsMap.containsKey(active)) {
            applyPreset(active)
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
            .onFailure {
                _state.value = _state.value.copy(
                    statusMessage = "Could not toggle equalizer state on this device."
                )
            }
        runCatching { bassBoost?.enabled = enabled }
        runCatching { virtualizer?.enabled = enabled && _state.value.virtualizerStrength > 0 }
        _state.value = _state.value.copy(isEnabled = enabled)
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        val eq = equalizer ?: return
        val clamped = levelMb.coerceIn(_state.value.minLevelMb, _state.value.maxLevelMb)
        runCatching {
            eq.setBandLevel(bandIndex.toShort(), clamped.toShort())
        }.onFailure {
            _state.value = _state.value.copy(statusMessage = "Unable to adjust frequency band.")
        }
        val updatedBands = _state.value.bands.map { band ->
            if (band.index == bandIndex) band.copy(levelMb = clamped) else band
        }
        _state.value = _state.value.copy(
            bands = updatedBands,
            activePreset = "Custom"
        )
    }

    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            bassBoost?.let { bb ->
                if (bb.strengthSupported) {
                    bb.setStrength(clamped.toShort())
                }
            }
        }.onFailure {
            _state.value = _state.value.copy(statusMessage = "Bass boost adjustment unsupported on this output.")
        }
        _state.value = _state.value.copy(bassBoostStrength = clamped)
    }

    fun setVirtualizerStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        runCatching {
            virtualizer?.let { virt ->
                virt.enabled = _state.value.isEnabled && clamped > 0
                if (virt.strengthSupported) {
                    virt.setStrength(clamped.toShort())
                }
            }
        }.onFailure {
            _state.value = _state.value.copy(statusMessage = "Spatial virtualizer unsupported on this audio route.")
        }
        _state.value = _state.value.copy(virtualizerStrength = clamped)
    }

    fun setNormalizationEnabled(enabled: Boolean) {
        normalizationEnabled = enabled
        runCatching {
            loudnessEnhancer?.let { enhancer ->
                enhancer.enabled = enabled
                if (enabled) {
                    enhancer.setTargetGain((currentSuggestedGainDb * 100f).roundToInt())
                }
            }
        }
    }

    fun updateTrackNormalizationGain(suggestedGainDb: Float) {
        currentSuggestedGainDb = suggestedGainDb.coerceIn(-6.0f, 8.0f)
        if (!normalizationEnabled) return
        runCatching {
            loudnessEnhancer?.let { enhancer ->
                enhancer.enabled = true
                enhancer.setTargetGain((currentSuggestedGainDb * 100f).roundToInt())
            }
        }
    }

    fun applyPreset(presetName: String) {
        val custom = customPresetsMap[presetName]
        val eq = equalizer
        val currentBands = _state.value.bands

        if (custom != null) {
            if (eq != null && currentBands.isNotEmpty()) {
                val minMb = _state.value.minLevelMb
                val maxMb = _state.value.maxLevelMb
                val updatedBands = currentBands.mapIndexed { idx, band ->
                    val level = custom.bandLevelsMb.getOrElse(idx) { 0 }.coerceIn(minMb, maxMb)
                    runCatching { eq.setBandLevel(idx.toShort(), level.toShort()) }
                    band.copy(levelMb = level)
                }
                _state.value = _state.value.copy(
                    bands = updatedBands,
                    activePreset = presetName
                )
            } else {
                _state.value = _state.value.copy(activePreset = presetName)
            }
            setBassBoost(custom.bassBoostStrength)
            setVirtualizerStrength(custom.virtualizerStrength)
            return
        }

        if (eq == null || currentBands.isEmpty()) {
            _state.value = _state.value.copy(activePreset = presetName)
            return
        }

        val minMb = _state.value.minLevelMb
        val maxMb = _state.value.maxLevelMb
        val targetNorms: List<Float> = when (presetName) {
            "Bass Boost" -> listOf(0.75f, 0.45f, 0.05f, 0.1f, 0.2f)
            "Acoustic" -> listOf(0.25f, 0.1f, 0.2f, 0.45f, 0.35f)
            "Electronic" -> listOf(0.65f, 0.25f, -0.15f, 0.35f, 0.6f)
            "Vocal" -> listOf(-0.25f, 0.1f, 0.55f, 0.45f, 0.15f)
            "Late Night" -> listOf(-0.35f, 0.15f, 0.3f, 0.2f, -0.25f)
            else -> List(currentBands.size) { 0f }
        }

        val updatedBands = currentBands.mapIndexed { idx, band ->
            val norm = targetNorms.getOrElse(idx) { 0f }
            val level = if (norm >= 0f) {
                (norm * maxMb).roundToInt()
            } else {
                (-norm * minMb).roundToInt()
            }.coerceIn(minMb, maxMb)

            runCatching { eq.setBandLevel(idx.toShort(), level.toShort()) }
            band.copy(levelMb = level)
        }

        _state.value = _state.value.copy(
            bands = updatedBands,
            activePreset = presetName
        )
    }

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        currentSessionId = 0
    }
}
