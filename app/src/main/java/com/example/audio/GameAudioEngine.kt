package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

enum class SoundEffect {
    LAUNCH_CAW,
    WING_FLAP,
    NEAR_MISS,
    COIN_PICKUP,
    HIT_DAMAGE,
    COLLISION,
    UI_TAP,
    NEW_RECORD,
    PURCHASE,
    BONUS_HP,
}

class GameAudioEngine {
    private val sampleRate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var sfxEnabled = true
    private var ambientEnabled = true
    private var masterVolume = 0.85f
    private var flightIntensity = 0.25f

    private var ambientJob: Job? = null
    private var ambientTrack: AudioTrack? = null

    private val sfxBuffers: Map<SoundEffect, ShortArray> by lazy {
        mapOf(
            SoundEffect.LAUNCH_CAW to buildGentleLaunchBuffer(),
            SoundEffect.WING_FLAP to buildSoftWingFlapBuffer(),
            SoundEffect.NEAR_MISS to buildNearMissBuffer(),
            SoundEffect.COIN_PICKUP to buildCoinPickupBuffer(),
            SoundEffect.HIT_DAMAGE to buildHitDamageBuffer(),
            SoundEffect.COLLISION to buildCollisionBuffer(),
            SoundEffect.UI_TAP to buildUiTapBuffer(),
            SoundEffect.NEW_RECORD to buildNewRecordBuffer(),
            SoundEffect.PURCHASE to buildPurchaseBuffer(),
            SoundEffect.BONUS_HP to buildBonusHpBuffer(),
        )
    }

    fun updateSettings(
        sfxEnabled: Boolean,
        ambientEnabled: Boolean,
        masterVolume: Float,
    ) {
        this.sfxEnabled = sfxEnabled
        this.ambientEnabled = ambientEnabled
        this.masterVolume = masterVolume.coerceIn(0f, 1f)
        if (ambientEnabled && masterVolume > 0.01f) {
            ensureAmbientPlaying()
        } else {
            stopAmbient()
        }
    }

    fun setFlightIntensity(intensity: Float) {
        flightIntensity = intensity.coerceIn(0.15f, 1.0f)
    }

    fun playSfx(effect: SoundEffect) {
        if (!sfxEnabled || masterVolume <= 0.01f) return
        val buffer = sfxBuffers[effect] ?: return
        scope.launch {
            playStaticBuffer(buffer, masterVolume)
        }
    }

    fun onResume() {
        if (ambientEnabled && masterVolume > 0.01f) {
            ensureAmbientPlaying()
        }
    }

    fun onPause() {
        stopAmbient()
    }

    fun release() {
        stopAmbient()
        scope.cancel()
    }

    /**
     * Synthesizes a soothing, focus-oriented ambient soundtrack in D Dorian:
     * - Warm breathing string/pad chords (Dm9 -> Bbmaj7 -> Fmaj7 -> Asus4)
     * - Gentle felt-piano / harp pentatonic notes with soft delay echo
     * - Subtle ultra-low silk breeze during flight (zero harsh static hiss)
     */
    private fun ensureAmbientPlaying() {
        if (ambientJob?.isActive == true) return
        ambientJob = scope.launch {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4096)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@launch

            ambientTrack = track
            runCatching { track.play() }

            val chunkSize = 1024
            val pcm = ShortArray(chunkSize)

            // 4 relaxing warm ambient chords (Hz): Dm9, Bbmaj7, Fmaj7, C6/9
            val chords = arrayOf(
                doubleArrayOf(146.83, 220.00, 261.63, 329.63), // Dm9 (D3, A3, C4, E4)
                doubleArrayOf(116.54, 174.61, 220.00, 293.66), // Bbmaj7 (Bb2, F3, A3, D4)
                doubleArrayOf(174.61, 220.00, 261.63, 329.63), // Fmaj7 (F3, A3, C4, E4)
                doubleArrayOf(130.81, 196.00, 220.00, 293.66), // C6/9 (C3, G3, A3, D4)
            )
            // Meditative felt-piano / harp melody notes in D minor pentatonic
            val melodyNotes = doubleArrayOf(
                293.66, 349.23, 440.00, 523.25, 440.00, 392.00, 349.23, 329.63,
                293.66, 440.00, 587.33, 523.25, 440.00, 349.23, 329.63, 261.63,
            )

            val padPhases = DoubleArray(4)
            val prevPadPhases = DoubleArray(4)
            var chordIndex = 0
            var prevChordIndex = 0
            var chordTimer = 0.0
            val chordDuration = 6.0

            var notePhase = 0.0
            var noteFreq = melodyNotes[0]
            var noteEnv = 0.0
            var noteAge = 0.0
            var noteIdx = 0
            var noteTimer = 0.8

            // Gentle circular echo buffer (~380ms delay) for warm spaciousness
            val echoDelaySamples = (sampleRate * 0.38f).toInt()
            val echoBuf = FloatArray(echoDelaySamples)
            var echoPos = 0

            var lfoPhase = 0.0
            var breezeLp1 = 0f
            var breezeLp2 = 0f
            val rng = Random(137)
            val invSr = 1.0 / sampleRate

            while (isActive && ambientEnabled) {
                val vol = masterVolume * 0.42f
                val breezeVol = (flightIntensity - 0.25f).coerceIn(0f, 0.75f) * 0.055f

                for (i in 0 until chunkSize) {
                    chordTimer += invSr
                    if (chordTimer >= chordDuration) {
                        chordTimer -= chordDuration
                        prevChordIndex = chordIndex
                        chordIndex = (chordIndex + 1) % chords.size
                    }

                    // Smooth 1.8s cosine crossfade between chords
                    val xfade = (chordTimer / 1.8).coerceIn(0.0, 1.0)
                    val curWeight = (0.5 - 0.5 * cosApprox(xfade * PI)).toFloat()
                    val prevWeight = 1f - curWeight

                    lfoPhase += invSr * 0.11
                    val breathe = (0.82 + 0.18 * sin(lfoPhase * 2.0 * PI)).toFloat()

                    val curChord = chords[chordIndex]
                    val prevChord = chords[prevChordIndex]
                    var padSum = 0f
                    for (v in 0 until 4) {
                        padPhases[v] += 2.0 * PI * curChord[v] * invSr
                        prevPadPhases[v] += 2.0 * PI * prevChord[v] * invSr
                        if (padPhases[v] > 2.0 * PI) padPhases[v] -= 2.0 * PI
                        if (prevPadPhases[v] > 2.0 * PI) prevPadPhases[v] -= 2.0 * PI

                        val curTone = (sin(padPhases[v]) * 0.82 + sin(padPhases[v] * 2.0) * 0.14).toFloat()
                        val prevTone = (sin(prevPadPhases[v]) * 0.82 + sin(prevPadPhases[v] * 2.0) * 0.14).toFloat()
                        padSum += curTone * curWeight + prevTone * prevWeight
                    }
                    val padOut = padSum * 0.115f * breathe

                    // Soft felt-piano / harp note trigger every ~1.25s..1.75s
                    noteTimer -= invSr
                    if (noteTimer <= 0.0) {
                        noteFreq = melodyNotes[noteIdx % melodyNotes.size]
                        noteIdx++
                        noteAge = 0.0
                        noteTimer = if (noteIdx % 4 == 0) 1.85 else 1.25
                    }
                    noteAge += invSr
                    // Gentle 28ms attack + warm 2.1s exponential decay
                    val attack = (noteAge / 0.028).coerceIn(0.0, 1.0)
                    noteEnv = attack * exp(-noteAge * 1.65)
                    notePhase += 2.0 * PI * noteFreq * invSr
                    if (notePhase > 2.0 * PI) notePhase -= 2.0 * PI
                    val pianoRaw = (
                        sin(notePhase) * 0.78 +
                            sin(notePhase * 2.0) * 0.16 * exp(-noteAge * 3.0) +
                            sin(notePhase * 3.0) * 0.04 * exp(-noteAge * 5.0)
                        ).toFloat() * noteEnv.toFloat() * 0.22f

                    val delayed = echoBuf[echoPos]
                    val pianoWithEcho = pianoRaw + delayed * 0.36f
                    echoBuf[echoPos] = pianoRaw + delayed * 0.28f
                    echoPos = (echoPos + 1) % echoDelaySamples

                    // Ultra-soft warm low-passed breeze (never harsh hiss)
                    val white = rng.nextFloat() * 2f - 1f
                    breezeLp1 += 0.018f * (white - breezeLp1)
                    breezeLp2 += 0.014f * (breezeLp1 - breezeLp2)
                    val breezeOut = breezeLp2 * breezeVol

                    val mixed = tanh(((padOut + pianoWithEcho + breezeOut) * vol).toDouble()).toFloat()
                    pcm[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
                }

                val written = runCatching { track.write(pcm, 0, chunkSize) }.getOrDefault(-1)
                if (written < 0) break
            }

            runCatching {
                track.pause()
                track.flush()
                track.release()
            }
            if (ambientTrack === track) {
                ambientTrack = null
            }
        }
    }

    private fun cosApprox(x: Double): Double = kotlin.math.cos(x)

    private fun stopAmbient() {
        ambientJob?.cancel()
        ambientJob = null
        ambientTrack?.let { track ->
            runCatching {
                track.pause()
                track.flush()
                track.release()
            }
        }
        ambientTrack = null
    }

    private fun playStaticBuffer(samples: ShortArray, volume: Float) {
        val byteLen = samples.size * 2
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(byteLen)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        }.getOrNull() ?: return

        runCatching {
            track.write(samples, 0, samples.size)
            track.setVolume(volume)
            track.play()
            val durationMs = (samples.size * 1000L) / sampleRate + 60L
            Thread.sleep(durationMs)
        }
        runCatching {
            track.stop()
            track.release()
        }
    }

    private fun buildGentleLaunchBuffer(): ShortArray {
        val duration = 0.36f
        val count = (sampleRate * duration).toInt()
        val out = ShortArray(count)
        var p1 = 0.0
        var p2 = 0.0
        var lp = 0f
        val rng = Random(7)
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val env = sin(t * PI).toFloat() * exp(-t * 1.8f)
            val freq = 340.0 + 180.0 * sin(t * PI * 0.65)
            p1 += 2.0 * PI * freq / sampleRate
            p2 += 2.0 * PI * (freq * 1.5) / sampleRate
            val breeze = rng.nextFloat() * 2f - 1f
            lp += 0.03f * (breeze - lp)
            val mixed = ((sin(p1) * 0.65 + sin(p2) * 0.25).toFloat() * 0.45f + lp * 0.4f) * env * 0.28f
            out[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun buildSoftWingFlapBuffer(): ShortArray {
        val duration = 0.16f
        val count = (sampleRate * duration).toInt()
        val out = ShortArray(count)
        var lp1 = 0f
        var lp2 = 0f
        val rng = Random(11)
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val env = sin(t * PI).toFloat() * (1f - t * 0.35f)
            val noise = rng.nextFloat() * 2f - 1f
            lp1 += 0.045f * (noise - lp1)
            lp2 += 0.035f * (lp1 - lp2)
            val thump = sin(2.0 * PI * (78.0 - 22.0 * t) * (i.toDouble() / sampleRate)).toFloat() * 0.24f
            val mixed = (lp2 * 0.9f + thump) * env * 0.20f
            out[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun buildNearMissBuffer(): ShortArray {
        val duration = 0.24f
        val count = (sampleRate * duration).toInt()
        val out = ShortArray(count)
        var phase1 = 0.0
        var phase2 = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val attack = (t / 0.06f).coerceIn(0f, 1f)
            val env = attack * exp(-t * 4.5f)
            phase1 += 2.0 * PI * 440.0 / sampleRate
            phase2 += 2.0 * PI * 659.25 / sampleRate
            val mixed = (sin(phase1) * 0.65 + sin(phase2) * 0.35).toFloat() * env * 0.18f
            out[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun buildCoinPickupBuffer(): ShortArray {
        val duration = 0.22f
        val count = (sampleRate * duration).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val attack = (t / 0.035f).coerceIn(0f, 1f)
            val env = attack * exp(-t * 7.2f)
            val freq = if (t < 0.24f) 587.33 else 783.99 // warm D5 -> G5 kalimba chime
            phase += 2.0 * PI * freq / sampleRate
            val mixed = (sin(phase) * 0.84 + sin(phase * 2.0) * 0.14).toFloat() * env * 0.22f
            out[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun buildHitDamageBuffer(): ShortArray {
        val duration = 0.22f
        val count = (sampleRate * duration).toInt()
        val out = ShortArray(count)
        var lp = 0f
        var phase = 0.0
        val rng = Random(53)
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val attack = (t / 0.03f).coerceIn(0f, 1f)
            val env = attack * exp(-t * 8.5f)
            phase += 2.0 * PI * (120.0 - 52.0 * t) / sampleRate
            val noise = rng.nextFloat() * 2f - 1f
            lp += 0.05f * (noise - lp)
            val mixed = (sin(phase).toFloat() * 0.72f + lp * 0.28f) * env * 0.34f
            out[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun buildCollisionBuffer(): ShortArray {
        val duration = 0.42f
        val count = (sampleRate * duration).toInt()
        val out = ShortArray(count)
        var lp = 0f
        var phase = 0.0
        val rng = Random(99)
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val attack = (t / 0.03f).coerceIn(0f, 1f)
            val env = attack * exp(-t * 5.2f)
            val freq = 105.0 * exp(-t * 2.8) + 42.0
            phase += 2.0 * PI * freq / sampleRate
            val noise = rng.nextFloat() * 2f - 1f
            lp += 0.045f * (noise - lp)
            val mixed = (sin(phase).toFloat() * 0.76f + lp * 0.24f) * env * 0.42f
            out[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun buildUiTapBuffer(): ShortArray {
        val duration = 0.055f
        val count = (sampleRate * duration).toInt()
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val attack = (t / 0.08f).coerceIn(0f, 1f)
            val env = attack * exp(-t * 14f)
            phase += 2.0 * PI * (310.0 - 70.0 * t) / sampleRate
            val mixed = sin(phase).toFloat() * env * 0.14f
            out[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun buildNewRecordBuffer(): ShortArray {
        val duration = 0.65f
        val count = (sampleRate * duration).toInt()
        val out = ShortArray(count)
        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val attack = (t / 0.06f).coerceIn(0f, 1f)
            val env = attack * exp(-t * 2.6f)
            phase1 += 2.0 * PI * 293.66 / sampleRate
            phase2 += 2.0 * PI * 440.00 / sampleRate
            phase3 += 2.0 * PI * 587.33 / sampleRate
            val mixed = (sin(phase1) * 0.42 + sin(phase2) * 0.34 + sin(phase3) * 0.24).toFloat() * env * 0.26f
            out[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun buildPurchaseBuffer(): ShortArray {
        val duration = 0.38f
        val count = (sampleRate * duration).toInt()
        val out = ShortArray(count)
        var p1 = 0.0
        var p2 = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val attack = (t / 0.05f).coerceIn(0f, 1f)
            val env = attack * exp(-t * 4.5f)
            p1 += 2.0 * PI * 392.00 / sampleRate
            p2 += 2.0 * PI * (if (t < 0.32f) 523.25 else 783.99) / sampleRate
            val mixed = (sin(p1) * 0.45 + sin(p2) * 0.55).toFloat() * env * 0.25f
            out[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun buildBonusHpBuffer(): ShortArray {
        val duration = 0.52f
        val count = (sampleRate * duration).toInt()
        val out = ShortArray(count)
        var p = 0.0
        var pHarm = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val attack = (t / 0.04f).coerceIn(0f, 1f)
            val env = attack * exp(-t * 3.5f)
            val freq = when {
                t < 0.22f -> 440.00 // A4
                t < 0.45f -> 523.25 // C5
                else -> 659.25      // E5
            }
            p += 2.0 * PI * freq / sampleRate
            pHarm += 2.0 * PI * (freq * 0.5) / sampleRate
            val mixed = (sin(p) * 0.72 + sin(pHarm) * 0.28).toFloat() * env * 0.26f
            out[i] = (mixed * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }
}
