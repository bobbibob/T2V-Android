package com.t2v.generators.synth

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.cos
import kotlin.random.Random

/**
 * Procedural audio synthesiser that turns a free-text prompt into a short
 * WAV-ready PCM signal — no ML model, no download, runs in milliseconds.
 *
 * Music: parses mood keywords → picks a key + chord progression → synthesises
 *   pad / arpeggio / drone with simple oscillators and a delay-line reverb.
 * Sound: parses the prompt for an effect family (door, whoosh, notification,
 *   rain, wind, impact, beep, explosion, laser, alarm, water, thunder,
 *   applause, whisper, glass) → generates the matching waveform from
 *   filtered noise, oscillators and amplitude envelopes.
 *
 * Output: mono 16-bit PCM at 22050 Hz.
 */
object ProceduralAudioSynth {

    const val SAMPLE_RATE = 22050

    // ── Music ──────────────────────────────────────────────────────────────

    private val MOOD_KEYS = mapOf(
        "ambient"  to Key(0,  Scale.MAJOR,    55.0,  0.3, 4.0),
        "calm"     to Key(0,  Scale.MAJOR,    65.0,  0.4, 3.0),
        "cinema"   to Key(2,  Scale.MINOR,    49.0,  0.5, 5.0),
        "cinematic" to Key(2, Scale.MINOR,    49.0,  0.5, 5.0),
        "dark"     to Key(5,  Scale.MINOR,    41.0,  0.6, 6.0),
        "uplift"   to Key(0,  Scale.MAJOR,    65.0,  0.5, 2.0),
        "uplifting" to Key(0, Scale.MAJOR,    65.0,  0.5, 2.0),
        "happy"    to Key(0,  Scale.MAJOR,    73.0,  0.55, 2.0),
        "sad"      to Key(5,  Scale.MINOR,    49.0,  0.4, 5.0),
        "tension"  to Key(7,  Scale.HARMONIC_MINOR, 55.0, 0.6, 4.0),
        "suspense" to Key(9,  Scale.MINOR,    49.0,  0.55, 4.0),
        "dream"    to Key(4,  Scale.MAJOR,    55.0,  0.3, 5.0),
        "dreamy"   to Key(4,  Scale.MAJOR,    55.0,  0.3, 5.0),
        "mysterious" to Key(3, Scale.HARMONIC_MINOR, 49.0, 0.5, 5.0),
        "mystic"   to Key(3,  Scale.HARMONIC_MINOR, 49.0, 0.5, 5.0),
        "peaceful" to Key(0,  Scale.MAJOR,    55.0,  0.3, 5.0),
        "serene"   to Key(0,  Scale.MAJOR,    55.0,  0.3, 5.0),
        "meditation" to Key(0, Scale.MAJOR,   55.0,  0.25, 6.0),
        "romantic" to Key(0,  Scale.MAJOR,    61.0,  0.45, 4.0),
        "love"     to Key(0,  Scale.MAJOR,    61.0,  0.45, 4.0),
        "energetic" to Key(0, Scale.MAJOR,    82.0,  0.6, 2.0),
        "powerful" to Key(0,  Scale.MAJOR,    82.0,  0.6, 2.0),
        "epic"     to Key(0,  Scale.MAJOR,    82.0,  0.6, 2.0),
        "jazzy"    to Key(2,  Scale.MAJOR,    69.0,  0.55, 2.0),
        "jazz"     to Key(2,  Scale.MAJOR,    69.0,  0.55, 2.0),
        "swing"    to Key(2,  Scale.MAJOR,    69.0,  0.55, 2.0),
        "warm"     to Key(0,  Scale.MAJOR,    61.0,  0.35, 3.0),
        "cozy"     to Key(0,  Scale.MAJOR,    61.0,  0.35, 3.0),
    )

    private data class Key(
        val rootSemitones: Int,        // 0=C, 2=D, 5=F …
        val scale: Scale,
        val rootFreq: Double,          // Hz of the root note
        val brightness: Double,        // 0..1 low-pass amount
        val reverbSeconds: Double,
    )

    private enum class Scale(val intervals: IntArray) {
        MAJOR(intArrayOf(0, 2, 4, 5, 7, 9, 11)),
        MINOR(intArrayOf(0, 2, 3, 5, 7, 8, 10)),
        HARMONIC_MINOR(intArrayOf(0, 2, 3, 5, 7, 8, 11)),
    }

    /** Generate `durationSec` seconds of music from a prompt. */
    fun synthMusic(prompt: String, durationSec: Int, seed: Long = System.currentTimeMillis()): ShortArray {
        val rng = Random(seed)
        val lower = prompt.lowercase()
        val key = MOOD_KEYS.entries.firstOrNull { (k, _) -> lower.contains(k) }?.value
            ?: Key(0, Scale.MAJOR, 55.0, 0.4, 3.0)

        // Chord progression: I – V – vi – IV  (or i – v – VI – iv for minor)
        val scale = key.scale.intervals
        val progression = if (key.scale == Scale.MAJOR) intArrayOf(0, 4, 5, 3) else intArrayOf(0, 4, 5, 3)
        val chordRoots = progression.map { degree ->
            val octaveShift = if (degree >= scale.size) 12 else 0
            key.rootSemitones + scale[degree % scale.size] + octaveShift
        }

        val totalSamples = durationSec * SAMPLE_RATE
        val out = FloatArray(totalSamples)
        val chordDurSec = durationSec.toDouble() / chordRoots.size

        for ((ci, chordRoot) in chordRoots.withIndex()) {
            val startSample = (ci * chordDurSec * SAMPLE_RATE).toInt()
            val endSample = ((ci + 1) * chordDurSec * SAMPLE_RATE).toInt().coerceAtMost(totalSamples)
            // Triad: root, third, fifth
            val notes = intArrayOf(0, 2, 4).map { interval ->
                midiToFreq(chordRoot + scale[interval % scale.size] + 48)
            }
            for (s in startSample until endSample) {
                val t = (s - startSample) / SAMPLE_RATE.toDouble()
                val env = envelope(t, chordDurSec, 0.15, 0.6)
                var sample = 0.0
                for ((ni, freq) in notes.withIndex()) {
                    val detune = 1.0 + (rng.nextDouble() - 0.5) * 0.003
                    // Mix sine (fundamental) + triangle (odd harmonics) for warmth
                    val sine = sin(2 * PI * freq * detune * t)
                    val tri = (2 / PI) * sin(2 * PI * freq * detune * t) -
                              (2 / (3 * PI)) * sin(6 * PI * freq * detune * t)
                    sample += sine * 0.5 + tri * 0.25 * key.brightness
                }
                // Slow LFO on amplitude for movement
                val lfo = 0.85 + 0.15 * sin(2 * PI * 0.3 * t)
                out[s] += (sample * env * lfo * 0.18).toFloat()
            }
        }

        // Simple delay-line reverb
        applyReverb(out, key.reverbSeconds, 0.35)
        // Gentle low-pass for warmth
        applyLowPass(out, key.brightness)
        normalize(out, 0.7)
        return floatToPcm16(out)
    }

    // ── Sound effects ──────────────────────────────────────────────────────

    /** Generate `durationSec` seconds of a sound effect from a prompt. */
    fun synthSound(prompt: String, durationSec: Int, seed: Long = System.currentTimeMillis()): ShortArray {
        val lower = prompt.lowercase()
        val totalSamples = durationSec * SAMPLE_RATE
        val rng = Random(seed)

        return when {
            lower.contains("laser") || lower.contains("blaster") || lower.contains("pew") || lower.contains("zap") ->
                synthLaser(totalSamples, rng)
            lower.contains("alarm") || lower.contains("siren") || lower.contains("klaxon") ->
                synthAlarm(totalSamples, rng)
            lower.contains("door") || lower.contains("slam") || lower.contains("close") ->
                synthDoor(totalSamples, rng)
            lower.contains("whoosh") || lower.contains("swoosh") || lower.contains("swish") ->
                synthWhoosh(totalSamples, rng)
            lower.contains("notif") || lower.contains("bell") || lower.contains("chime") || lower.contains("ding") ->
                synthNotification(totalSamples, rng)
            lower.contains("water") || lower.contains("splash") || lower.contains("bubble") || lower.contains("drip") ->
                synthWater(totalSamples, rng)
            lower.contains("thunder") || lower.contains("storm") ->
                synthThunder(totalSamples, rng)
            lower.contains("rain") ->
                synthRain(totalSamples, rng)
            lower.contains("wind") ->
                synthWind(totalSamples, rng)
            lower.contains("applause") || lower.contains("crowd") || lower.contains("cheer") || lower.contains("clap") ->
                synthApplause(totalSamples, rng)
            lower.contains("whisper") || lower.contains("breath") || lower.contains("shush") || lower.contains("hush") ->
                synthWhisper(totalSamples, rng)
            lower.contains("glass") || lower.contains("shards") || lower.contains("smash") ->
                synthGlass(totalSamples, rng)
            lower.contains("explosion") || lower.contains("boom") || lower.contains("blast") ->
                synthExplosion(totalSamples, rng)
            lower.contains("click") || lower.contains("tap") || lower.contains("tick") ->
                synthClick(totalSamples, rng)
            lower.contains("step") || lower.contains("footstep") ->
                synthFootstep(totalSamples, rng)
            lower.contains("heartbeat") || lower.contains("pulse") ->
                synthHeartbeat(totalSamples, rng)
            else ->
                synthGenericSfx(totalSamples, lower, rng)
        }
    }

    private fun synthDoor(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Low-frequency thump + filtered noise burst
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val thump = sin(2 * PI * 80.0 * t) * exp(-t * 8.0)
            val creak = (rng.nextDouble() - 0.5) * exp(-t * 15.0) * 0.3
            val body = sin(2 * PI * 120.0 * t) * exp(-t * 12.0) * 0.5
            out[i] = (thump * 0.5 + creak + body).toFloat()
        }
        applyLowPass(out, 0.4)
        normalize(out, 0.8)
        return floatToPcm16(out)
    }

    private fun synthWhoosh(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Filtered noise with rising then falling amplitude
        var prev = 0.0
        for (i in 0 until n) {
            val t = i / n.toDouble()
            // Band-pass sweep: centre freq rises then falls
            val centerFreq = 400 + 2000 * sin(PI * t)
            val alpha = exp(-2 * PI * centerFreq / SAMPLE_RATE)
            val white = rng.nextDouble() - 0.5
            prev = (1 - alpha) * white + alpha * prev
            // Amplitude envelope: rise → peak → fall
            val env = sin(PI * t).coerceAtLeast(0.0)
            out[i] = (prev * env * 0.6).toFloat()
        }
        normalize(out, 0.75)
        return floatToPcm16(out)
    }

    private fun synthNotification(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Two-tone bell: 880 Hz then 1320 Hz
        val tones = listOf(Pair(880.0, 0.0), Pair(1320.0, 0.15))
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            var sample = 0.0
            for ((freq, startT) in tones) {
                if (t >= startT) {
                    val dt = t - startT
                    val env = exp(-dt * 3.0)
                    // Fundamental + slight inharmonic overtone for bell character
                    sample += sin(2 * PI * freq * dt) * env * 0.4
                    sample += sin(2 * PI * freq * 2.76 * dt) * env * 0.08
                }
            }
            out[i] = sample.toFloat()
        }
        normalize(out, 0.7)
        return floatToPcm16(out)
    }

    private fun synthLaser(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Downward gliss sweep with a fast-decay "pew" body
        var phase = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val ratio = i / n.toDouble()
            val freq = 1400.0 - 1150.0 * (ratio * ratio * ratio) // 1400 -> 250 Hz
            phase += 2 * PI * freq / SAMPLE_RATE
            val env = Math.pow(1.0 - ratio, 1.5)
            val body = sin(phase) * env * 0.5
            val click = (rng.nextDouble() - 0.5) * exp(-t * 60.0) * 0.35
            out[i] = (body + click).toFloat()
        }
        normalize(out, 0.7)
        return floatToPcm16(out)
    }

    private fun synthAlarm(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Alternating two-tone klaxon: ~0.3s each tone, gentle attack per pulse
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val segment = (t / 0.3).toInt() % 2
            val freq = if (segment == 0) 620.0 else 830.0
            val localT = t % 0.3
            val attack = (localT / 0.02).coerceIn(0.0, 1.0)
            val sustain = exp(-(localT - 0.02).coerceAtLeast(0.0) * 0.4)
            out[i] = (sin(2 * PI * freq * t) * attack * (0.35 + 0.25 * sustain)).toFloat()
        }
        normalize(out, 0.7)
        return floatToPcm16(out)
    }

    private fun synthWater(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Loose filtered noise bed + random droplet pings and splashes
        var lp1 = 0.0; var lp2 = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val white = rng.nextDouble() - 0.5
            lp1 = 0.94 * lp1 + 0.06 * white
            lp2 = 0.8 * lp2 + 0.2 * lp1
            var sample = lp2 * 0.4
            if (rng.nextInt(900) == 0) {
                val freq = 700 + rng.nextDouble() * 800
                val ping = sin(2 * PI * freq * t) * exp(-0.05 * SAMPLE_RATE * rng.nextDouble()) * 0.5
                sample += ping
            }
            out[i] = sample.toFloat()
        }
        normalize(out, 0.65)
        return floatToPcm16(out)
    }

    private fun synthThunder(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Sharp crackle first ~0.4s, then a long low brown-noise rumble
        var brown = 0.0; var lp = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val crackle = (rng.nextDouble() - 0.5) * exp(-t * 6.0) * 0.5
            brown = (brown + (rng.nextDouble() - 0.5) * 0.06).coerceIn(-1.0, 1.0)
            lp = 0.985 * lp + 0.015 * brown
            val rumble = lp * exp(-t * 1.2) * 0.7
            val strike = sin(2 * PI * 40.0 * t) * exp(-t * 2.0) * 0.3
            out[i] = (crackle + rumble + strike).toFloat()
        }
        normalize(out, 0.85)
        return floatToPcm16(out)
    }

    private fun synthApplause(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Clustered short noise bursts: hand-clap texture
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val burst = exp(-((i % (SAMPLE_RATE / 14)) / SAMPLE_RATE.toDouble()) * 220.0)
            val clusterEnv = 0.5 + 0.5 * sin(2 * PI * 2.1 * t)
            out[i] = ((rng.nextDouble() - 0.5) * burst * clusterEnv * 0.7).toFloat()
        }
        applyLowPass(out, 0.5)
        normalize(out, 0.6)
        return floatToPcm16(out)
    }

    private fun synthWhisper(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Soft band-passed noise with gentle amplitude movement
        var lp = 0.0; var prev = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val white = rng.nextDouble() - 0.5
            lp = 0.9 * lp + 0.1 * white
            val sibilant = (white - prev) * 0.6
            prev = white
            val env = 0.6 + 0.4 * sin(2 * PI * 1.4 * t)
            out[i] = (sibilant * env * 0.5 + lp * env * 0.2).toFloat()
        }
        normalize(out, 0.5)
        return floatToPcm16(out)
    }

    private fun synthGlass(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Metallic high-freq shards with fast decay and bright overtones
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val shard = if (rng.nextInt(220) == 0) (rng.nextDouble() - 0.5) * 2.0 else 0.0
            val freq = 1800.0 + rng.nextDouble() * 3600.0
            val ring = sin(2 * PI * freq * t) * exp(-t * 9.0) * 0.2
            val bright = sin(2 * PI * freq * 2.76 * t) * exp(-t * 11.0) * 0.05
            out[i] = (shard * 0.6 + ring + bright).toFloat()
        }
        normalize(out, 0.7)
        return floatToPcm16(out)
    }

    private fun synthRain(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // White noise → cascaded low-pass for rain texture
        var lp1 = 0.0; var lp2 = 0.0
        for (i in 0 until n) {
            val white = rng.nextDouble() - 0.5
            lp1 = 0.98 * lp1 + 0.02 * white
            lp2 = 0.95 * lp2 + 0.05 * lp1
            // Occasional "drop" impulses
            val drop = if (rng.nextInt(200) == 0) (rng.nextDouble() - 0.5) * 2.0 else 0.0
            out[i] = (lp2 * 0.4 + drop * 0.3).toFloat()
        }
        normalize(out, 0.6)
        return floatToPcm16(out)
    }

    private fun synthWind(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Brown noise with slowly varying low-pass
        var brown = 0.0; var lp = 0.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            brown = (brown + (rng.nextDouble() - 0.5) * 0.02).coerceIn(-1.0, 1.0)
            val cutoff = 0.92 + 0.06 * sin(2 * PI * 0.15 * t)
            lp = cutoff * lp + (1 - cutoff) * brown
            out[i] = (lp * 0.7).toFloat()
        }
        normalize(out, 0.65)
        return floatToPcm16(out)
    }

    private fun synthExplosion(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Low rumble + noise burst with long decay
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val rumble = sin(2 * PI * 45.0 * t) * exp(-t * 2.0) * 0.6
            val noise = (rng.nextDouble() - 0.5) * exp(-t * 4.0) * 0.5
            out[i] = (rumble + noise).toFloat()
        }
        applyLowPass(out, 0.5)
        normalize(out, 0.9)
        return floatToPcm16(out)
    }

    private fun synthClick(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Very short transient: high-freq noise burst with fast decay
        val clickDur = (SAMPLE_RATE * 0.008).toInt() // 8 ms
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val env = if (i < clickDur) exp(-t * 300.0) else 0.0
            out[i] = ((rng.nextDouble() - 0.5) * env * 0.8).toFloat()
        }
        normalize(out, 0.85)
        return floatToPcm16(out)
    }

    private fun synthFootstep(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Two short thumps ~0.5s apart
        val stepInterval = SAMPLE_RATE / 2
        var step = 0
        for (i in 0 until n) {
            val t = (i - step) / SAMPLE_RATE.toDouble()
            if (i >= step + SAMPLE_RATE * 0.15) {
                step += stepInterval
            }
            if (i >= step) {
                val dt = (i - step) / SAMPLE_RATE.toDouble()
                val env = exp(-dt * 30.0)
                val thump = sin(2 * PI * 100.0 * dt) * env
                val noise = (rng.nextDouble() - 0.5) * env * 0.3
                out[i] = (thump * 0.5 + noise).toFloat()
            }
        }
        applyLowPass(out, 0.5)
        normalize(out, 0.7)
        return floatToPcm16(out)
    }

    private fun synthHeartbeat(n: Int, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Two low thumps (lub-dub) per ~1s
        val bpm = 65.0
        val beatInterval = (60.0 / bpm * SAMPLE_RATE).toInt()
        var beat = 0
        for (i in 0 until n) {
            if (i >= beat + beatInterval) beat += beatInterval
            val dt = (i - beat) / SAMPLE_RATE.toDouble()
            // lub
            val lub = sin(2 * PI * 60.0 * dt) * exp(-dt * 25.0) * 0.6
            // dub (slightly after)
            val dubDt = dt - 0.15
            val dub = if (dubDt > 0) sin(2 * PI * 55.0 * dubDt) * exp(-dubDt * 30.0) * 0.4 else 0.0
            out[i] = (lub + dub).toFloat()
        }
        normalize(out, 0.8)
        return floatToPcm16(out)
    }

    private fun synthGenericSfx(n: Int, prompt: String, rng: Random): ShortArray {
        val out = FloatArray(n)
        // Hash prompt to seed a deterministic character
        val hash = prompt.hashCode()
        val baseFreq = 200 + (abs(hash) % 600)
        val noiseAmount = (abs(hash / 1000) % 100) / 100.0
        for (i in 0 until n) {
            val t = i / SAMPLE_RATE.toDouble()
            val env = envelope(t, n / SAMPLE_RATE.toDouble(), 0.05, 0.7)
            val tone = sin(2 * PI * baseFreq * t) * (1 - noiseAmount)
            val noise = (rng.nextDouble() - 0.5) * noiseAmount
            out[i] = ((tone + noise) * env * 0.5).toFloat()
        }
        normalize(out, 0.7)
        return floatToPcm16(out)
    }

    // ── DSP helpers ────────────────────────────────────────────────────────

    private fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun envelope(t: Double, dur: Double, attack: Double, release: Double): Double {
        val attackEnd = attack
        val releaseStart = dur - release
        return when {
            t < attackEnd -> t / attack
            t > releaseStart -> ((dur - t) / release).coerceIn(0.0, 1.0)
            else -> 1.0
        }
    }

    private fun applyReverb(out: FloatArray, seconds: Double, mix: Double) {
        val delayMs = (seconds * 250).toInt().coerceIn(50, 800)
        val delaySamples = delayMs * SAMPLE_RATE / 1000
        if (delaySamples >= out.size) return
        val wet = FloatArray(out.size)
        for (i in out.indices) {
            val delayedIdx = i - delaySamples
            wet[i] = if (delayedIdx >= 0) out[delayedIdx] * 0.5f + wet[delayedIdx] * 0.3f else 0f
        }
        for (i in out.indices) {
            out[i] = (out[i] * (1 - mix) + wet[i] * mix).toFloat()
        }
    }

    private fun applyLowPass(out: FloatArray, brightness: Double) {
        val alpha = 0.5 + brightness * 0.49 // 0.5..0.99
        var prev = 0f
        for (i in out.indices) {
            prev = (alpha * prev + (1 - alpha) * out[i]).toFloat()
            out[i] = prev
        }
    }

    private fun normalize(out: FloatArray, target: Double) {
        var peak = 0.0
        for (s in out) peak = maxOf(peak, abs(s.toDouble()))
        if (peak < 1e-9) return
        val gain = target / peak
        for (i in out.indices) out[i] = (out[i] * gain).toFloat()
    }

    private fun floatToPcm16(floats: FloatArray): ShortArray {
        val out = ShortArray(floats.size)
        for (i in floats.indices) {
            out[i] = (floats[i] * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        return out
    }

    // Extension: Double.pow
    private fun Double.pow(exp: Double): Double = Math.pow(this, exp)
}
