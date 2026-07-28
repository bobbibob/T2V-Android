package com.t2v.core.midi

import com.t2v.core.midi.synth.Synthesiser
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Рендерит [MidiSequence] в моно 16-bit PCM.
 *
 * Поддерживает два режима:
 *  1. **`renderSine(...)` — всегда работает**, не требует SoundFont.
 *     Использует `Synthesiser` (instruments 0..127) или `DrumKit`
 *     (канал 9). Качество — 8-bit, но это лучше чем молчание.
 *  2. **`renderWithSoundFont(...)` — опционально**, требует
 *     распарсенный [com.t2v.core.midi.sf2.SoundFont].
 *     Качество сильно лучше (как General MIDI).
 *
 * Output: моно 16-bit PCM 22050 Hz.
 */
object MidiRenderer {

    /**
     * Рендерит MIDI в PCM через синусоидальный синтезатор. **Не требует SoundFont.**
     * Поддерживает все 128 GM-программ через [Synthesiser]; для канала 9 (drums)
     * использует [DrumKit] — перкуссионные сэмплы генерируются в коде.
     */
    fun renderSine(
        sequence: MidiSequence,
        sampleRate: Int = 22050,
        masterGain: Float = 0.6f,
    ): ShortArray {
        val out = FloatArray((sequence.durationMs * sampleRate / 1000).coerceAtLeast(sampleRate))
        val programs = IntArray(16)  // channel -> program
        val synth = Synthesiser(sampleRate)

        // Track active notes: channel -> (note -> startTimeMs)
        val active = mutableMapOf<Int, MutableMap<Int, Int>>()

        for (event in sequence.events) {
            when (event) {
                is MidiEvent.TimeShift -> {
                    // Already represented by absolute times in out[]; we render
                    // by walking all events in order and mixing. The TimeShift
                    // event is just a marker for SMF — we don't need to do
                    // anything here.
                }
                is MidiEvent.NoteOn -> {
                    active.getOrPut(event.channel) { mutableMapOf() }[event.note] = event.timeMs
                }
                is MidiEvent.NoteOff -> {
                    val startMs = active[event.channel]?.remove(event.note) ?: continue
                    val startSample = startMs * sampleRate / 1000
                    val endSample = (event.timeMs * sampleRate / 1000).coerceAtMost(out.size)
                    if (startSample < 0 || startSample >= endSample) continue
                    val note = event.note
                    val velocity = 96  // TODO: take from NoteOn
                    if (event.channel == MidiConstants.DRUM_CHANNEL) {
                        // Drum: render by drum-kit
                        val drumSample = DrumKit.render(
                            note = note,
                            sampleRate = sampleRate,
                            startSample = 0,
                            numSamples = endSample - startSample,
                        )
                        mixInto(out, drumSample, startSample, gain = 0.7f)
                    } else {
                        // Melodic: render by program
                        val samples = synth.renderNote(
                            program = programs[event.channel],
                            note = note,
                            velocity = velocity,
                            numSamples = endSample - startSample,
                        )
                        mixInto(out, samples, startSample, gain = 0.5f)
                    }
                }
                is MidiEvent.InstrumentChange -> {
                    if (event.channel in 0..15) {
                        programs[event.channel] = event.program.coerceIn(0, 127)
                    }
                }
                is MidiEvent.TempoChange -> {
                    // Tempo changes during rendering are not yet supported.
                    // Would need to re-emit samples with new rate; out of scope here.
                }
            }
        }

        // Master gain + soft clip
        for (i in out.indices) {
            out[i] = (out[i] * masterGain).coerceIn(-1f, 1f)
        }

        return floatToPcm16(out)
    }

    private fun mixInto(target: FloatArray, source: FloatArray, offset: Int, gain: Float) {
        val n = minOf(source.size, target.size - offset)
        if (n <= 0 || offset < 0) return
        for (i in 0 until n) {
            target[offset + i] += source[i] * gain
        }
    }

    private fun floatToPcm16(samples: FloatArray): ShortArray {
        val out = ShortArray(samples.size)
        for (i in samples.indices) {
            val v = samples[i].coerceIn(-1f, 1f)
            out[i] = (v * 32767f).toInt().toShort()
        }
        return out
    }
}

/**
 * Минимальный GM-совместимый синтезатор: генерирует синусоидальные ноты
 * с ADSR-конвертом и небольшим количеством обертонов для тёплого звука.
 *
 * Не претендует на реализм — это **fallback** для случая, когда SoundFont
 * ещё не скачан. Когда появится SoundFont, рендеринг делает
 * [com.t2v.core.midi.sf2.SoundFontRenderer].
 */
class Synthesiser(private val sampleRate: Int) {
    fun renderNote(
        program: Int,
        note: Int,
        velocity: Int,
        numSamples: Int,
    ): FloatArray {
        val freq = 440.0 * 2.0.pow((note - 69).toDouble() / 12.0)
        val amp = (velocity / 127.0).toFloat() * 0.4f
        val (attack, decay, sustain, release) = envelope(program)
        val attackSamples = (attack * sampleRate).toInt()
        val decaySamples = (decay * sampleRate).toInt()
        val releaseSamples = (release * sampleRate).toInt()
        val sustainLevel = sustain.toFloat()
        val out = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = when {
                i < attackSamples -> i.toFloat() / attackSamples
                i < attackSamples + decaySamples -> {
                    val k = (i - attackSamples).toFloat() / decaySamples
                    1f - (1f - sustainLevel) * k
                }
                else -> sustainLevel
            }
            val releaseStart = numSamples - releaseSamples
            val releaseEnv = if (i > releaseStart) {
                val k = ((i - releaseStart).toFloat() / releaseSamples).coerceIn(0f, 1f)
                (1f - k).coerceAtLeast(0f)
            } else 1f
            val tone = synthTone(program, t, freq)
            out[i] = tone * env * releaseEnv * amp
        }
        return out
    }

    private fun envelope(program: Int): ADSR = when {
        program in 0..7 -> ADSR(0.005, 0.10, 0.65, 0.30)      // Piano
        program in 8..15 -> ADSR(0.002, 0.05, 0.40, 0.15)     // Chromatic Perc
        program in 16..23 -> ADSR(0.020, 0.05, 0.80, 0.10)    // Organ (slow attack)
        program in 24..31 -> ADSR(0.005, 0.08, 0.60, 0.25)    // Guitar
        program in 32..39 -> ADSR(0.010, 0.05, 0.70, 0.20)    // Bass
        program in 40..47 -> ADSR(0.050, 0.10, 0.85, 0.40)    // Strings
        program in 48..55 -> ADSR(0.080, 0.10, 0.85, 0.40)    // Ensemble
        program in 56..63 -> ADSR(0.020, 0.08, 0.75, 0.20)    // Brass
        program in 64..71 -> ADSR(0.030, 0.08, 0.70, 0.20)    // Reed
        program in 72..79 -> ADSR(0.020, 0.05, 0.75, 0.20)    // Pipe
        program in 80..87 -> ADSR(0.010, 0.10, 0.65, 0.30)    // Synth Lead
        program in 88..95 -> ADSR(0.100, 0.20, 0.85, 0.50)    // Synth Pad
        program in 96..103 -> ADSR(0.050, 0.10, 0.60, 0.30)   // Synth Effects
        else -> ADSR(0.010, 0.10, 0.65, 0.30)
    }

    private fun synthTone(program: Int, t: Double, freq: Double): Float {
        val f = 2 * PI * freq
        return when (MidiConstants.PROGRAM_FAMILY[program]) {
            0 -> {  // Piano: sine + soft 2nd harmonic
                (sin(f * t) * 0.7 + sin(2 * f * t) * 0.15).toFloat()
            }
            in 8..15 -> {  // Mallet: triangle (odd harmonics)
                ((2.0 / PI) * sin(f * t) - (2.0 / (3 * PI)) * sin(3 * f * t)).toFloat()
            }
            16 -> {  // Organ: full square
                val saw = 0.0
                var s = 0.0
                for (h in 1..8) s += sin(h * f * t) / h
                (s * 0.3).toFloat()
            }
            24 -> {  // Guitar: karplus-strong-like (we approximate with decay-modulated sine)
                val env = exp(-t * 2.0).toFloat()
                (sin(f * t) * 0.6 * env).toFloat()
            }
            40 -> {  // Strings: 4 detuned sawtooths
                val detune = 0.005
                var s = 0.0
                for (k in 0..3) {
                    val f2 = f * (1.0 + detune * (k - 1.5))
                    for (h in 1..6) s += sin(h * f2 * t) / h
                }
                (s * 0.1).toFloat()
            }
            56 -> {  // Brass: sawtooth with low-pass envelope
                var s = 0.0
                for (h in 1..10) s += sin(h * f * t) / h
                (s * 0.2).toFloat()
            }
            64 -> {  // Reed: clarinet-like (odd harmonics)
                var s = 0.0
                for (h in 1..9 step 2) s += sin(h * f * t) / h
                (s * 0.3).toFloat()
            }
            72 -> {  // Pipe: flute-like (pure sine + tiny breath noise)
                (sin(f * t) * 0.8 + (Math.random() - 0.5) * 0.05).toFloat()
            }
            80 -> {  // Synth Lead: sawtooth
                var s = 0.0
                for (h in 1..12) s += sin(h * f * t) / h
                (s * 0.15).toFloat()
            }
            88 -> {  // Synth Pad: slow LFO + 3 detuned sines
                val lfo = 0.8 + 0.2 * sin(2 * PI * 0.3 * t)
                val detune = 0.01
                (sin(f * t) * 0.4 +
                    sin(f * (1 + detune) * t) * 0.3 +
                    sin(f * (1 - detune) * t) * 0.3) * lfo.toFloat()
            }
            96 -> {  // SFX: FM-like
                val mod = sin(2 * PI * freq * 2 * t)
                sin(f * t + mod * 2).toFloat()
            }
            104, 112, 120 -> {  // Ethnic/Perc/SFX: short blip
                val env = exp(-t * 5.0).toFloat()
                sin(f * t).toFloat() * env * 0.4f
            }
            32 -> {  // Bass: pure sine, no harmonics
                (sin(f * t) * 0.7).toFloat()
            }
            else -> sin(f * t).toFloat()
        }
    }

    private data class ADSR(val attack: Double, val decay: Double, val sustain: Double, val release: Double)
}
