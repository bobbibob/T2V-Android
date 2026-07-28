package com.t2v.core.midi.synth

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Минимальный General MIDI percussion kit (канал 9 в SMF convention,
 * 0-indexed = канал 9 = 10 в SMF, мы используем 0-indexed канал 9).
 *
 * Каждый "сэмпл" генерируется в коде — это **не** сэмплы из SoundFont, а
 * параметрические ADSR-носы с шумом, чтобы занимать 0 байт и не требовать
 * скачивания. Качество ниже чем у SoundFont, но всё равно различимо
 * (kick ≠ snare ≠ hihat).
 *
 * Когда пользователь скачает GeneralUser GS SoundFont, рендер пойдёт
 * через [com.t2v.core.midi.sf2.SoundFontRenderer] и DrumKit не вызывается.
 *
 * Drum-key mapping (GM):
 *  35 Acoustic Bass Drum
 *  36 Bass Drum 1
 *  38 Acoustic Snare
 *  40 Electric Snare
 *  42 Closed Hi-Hat
 *  44 Pedal Hi-Hat
 *  46 Open Hi-Hat
 *  49 Crash Cymbal
 *  51 Ride Cymbal
 *  56 Cowbell
 *  60 Hi Bongo
 *  62 Mute Hi Conga
 *  64 Low Conga
 *
 *  Остальные ноты возвращают короткий клик, чтобы T2V не падал в молчание.
 */
object DrumKit {

    private val DRUM_KIND: Map<Int, DrumKind> = mapOf(
        35 to DrumKind.KICK,
        36 to DrumKind.KICK,
        41 to DrumKind.SNARE,
        38 to DrumKind.SNARE,
        40 to DrumKind.SNARE,
        43 to DrumKind.SNARE,
        42 to DrumKind.HIHAT_CLOSED,
        44 to DrumKind.HIHAT_PEDAL,
        46 to DrumKind.HIHAT_OPEN,
        49 to DrumKind.CRASH,
        51 to DrumKind.RIDE,
        55 to DrumKind.CRASH,
        57 to DrumKind.CRASH,
        50 to DrumKind.RIDE,
        52 to DrumKind.RIDE,
        53 to DrumKind.RIDE,
        56 to DrumKind.COWBELL,
        60 to DrumKind.BONGO,
        61 to DrumKind.BONGO,
        62 to DrumKind.CONGA,
        63 to DrumKind.CONGA,
        64 to DrumKind.CONGA,
        65 to DrumKind.CONGA,
    )

    fun render(
        note: Int,
        sampleRate: Int,
        startSample: Int = 0,
        numSamples: Int,
    ): FloatArray {
        val kind = DRUM_KIND[note] ?: return click(sampleRate, numSamples)
        val out = FloatArray(numSamples)
        val rng = Random(note * 31L + 17L)  // deterministic per note
        when (kind) {
            DrumKind.KICK -> renderKick(out, sampleRate)
            DrumKind.SNARE -> renderSnare(out, sampleRate, rng)
            DrumKind.HIHAT_CLOSED -> renderHihat(out, sampleRate, decayMs = 60, rng)
            DrumKind.HIHAT_PEDAL -> renderHihat(out, sampleRate, decayMs = 100, rng)
            DrumKind.HIHAT_OPEN -> renderHihat(out, sampleRate, decayMs = 400, rng)
            DrumKind.CRASH -> renderCymbal(out, sampleRate, decayMs = 800, rng)
            DrumKind.RIDE -> renderCymbal(out, sampleRate, decayMs = 600, rng)
            DrumKind.COWBELL -> renderCowbell(out, sampleRate)
            DrumKind.BONGO -> renderToneDrum(out, sampleRate, freq = 240.0, decayMs = 100, rng)
            DrumKind.CONGA -> renderToneDrum(out, sampleRate, freq = 200.0, decayMs = 200, rng)
        }
        return out
    }

    private fun renderKick(out: FloatArray, sampleRate: Int) {
        val n = out.size
        val f0 = 110.0
        val f1 = 50.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val freqSweep = f0 * exp(-t * 25.0) + f1
            val env = exp(-t * 12.0).toFloat()
            out[i] = sin(2 * PI * freqSweep * t).toFloat() * env * 0.8f
        }
    }

    private fun renderSnare(out: FloatArray, sampleRate: Int, rng: Random) {
        val n = out.size
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val tone = sin(2 * PI * 180.0 * t) * exp(-t * 25.0)
            val noise = (rng.nextDouble() - 0.5) * exp(-t * 15.0)
            out[i] = ((tone * 0.5 + noise * 0.6).toFloat() * 0.7f)
        }
    }

    private fun renderHihat(out: FloatArray, sampleRate: Int, decayMs: Int, rng: Random) {
        val n = out.size
        val decayT = decayMs / 1000.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t / decayT * 5.0).toFloat()
            // High-frequency content (6-8 kHz) of white noise
            val highpass = highPassNoise(rng, 6000.0, sampleRate, t)
            out[i] = highpass * env * 0.5f
        }
    }

    private fun renderCymbal(out: FloatArray, sampleRate: Int, decayMs: Int, rng: Random) {
        val n = out.size
        val decayT = decayMs / 1000.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t / decayT * 3.0).toFloat()
            // Cymbal: many inharmonic high-frequency partials
            var s = 0.0
            for (k in 1..16) {
                val partialFreq = 3500.0 * (1.0 + 0.05 * k)
                s += sin(2 * PI * partialFreq * t) / k
            }
            val noise = (rng.nextDouble() - 0.5) * 0.2
            out[i] = (s * 0.15 + noise).toFloat() * env * 0.4f
        }
    }

    private fun renderCowbell(out: FloatArray, sampleRate: Int) {
        val n = out.size
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 15.0).toFloat()
            // Two-tone: 540 + 800 Hz
            val s = sin(2 * PI * 540.0 * t) * 0.6 + sin(2 * PI * 800.0 * t) * 0.4
            out[i] = s.toFloat() * env * 0.5f
        }
    }

    private fun renderToneDrum(
        out: FloatArray,
        sampleRate: Int,
        freq: Double,
        decayMs: Int,
        rng: Random,
    ) {
        val n = out.size
        val decayT = decayMs / 1000.0
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t / decayT * 5.0).toFloat()
            val s = sin(2 * PI * freq * t) * 0.7 +
                sin(2 * PI * freq * 2.7 * t) * 0.15
            val noise = (rng.nextDouble() - 0.5) * exp(-t * 30.0) * 0.3
            out[i] = (s + noise).toFloat() * env * 0.6f
        }
    }

    private fun click(sampleRate: Int, numSamples: Int): FloatArray {
        val n = minOf(numSamples, sampleRate / 50)  // 20 ms max
        val out = FloatArray(numSamples)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            out[i] = (sin(2 * PI * 1200.0 * t) * exp(-t * 80.0)).toFloat() * 0.3f
        }
        return out
    }

    private fun highPassNoise(rng: Random, cutoffHz: Double, sampleRate: Int, t: Double): Float {
        // Simple one-pole high-pass: y[n] = α * (y[n-1] + x[n] - x[n-1])
        // Here we use a stateless approximation: white * exp cutoff
        val noise = (rng.nextDouble() - 0.5)
        val hp = noise * (1.0 - exp(-2 * PI * cutoffHz * t / sampleRate))
        return hp.toFloat()
    }

    private enum class DrumKind {
        KICK, SNARE, HIHAT_CLOSED, HIHAT_PEDAL, HIHAT_OPEN,
        CRASH, RIDE, COWBELL, BONGO, CONGA,
    }
}
