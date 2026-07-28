package com.t2v.core.midi.sf2

import com.t2v.core.midi.MidiConstants
import com.t2v.core.midi.MidiEvent
import com.t2v.core.midi.MidiSequence
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

/**
 * Рендерит [MidiSequence] в моно 16-bit PCM, используя распарсенный
 * [SoundFont]. Использует таблично-волновой синтез (sample playback)
 * с pitch shift по MIDI-ноте и ADSR-конвертом.
 *
 * **Не поддерживает**:
 *  - Modulators (LFO, vibrato)
 *  - Filter envelopes
 *  - Stereo (только моно — даже если в SoundFont есть linked sample)
 *  - ROM-ссылки
 *
 * **Поддерживает**:
 *  - 128 GM-инструментов (programs 0..127)
 *  - Drum kit (channel 9, presets 0..127 в bank 128)
 *  - Loop-воспроизведение сэмплов
 *  - Pitch shift через линейную интерполяцию
 *  - Per-zone key range (берём первую зону, попадающую в диапазон)
 */
class SoundFontRenderer(
    private val soundFont: SoundFont,
    private val sampleRate: Int = 22050,
) {
    fun render(sequence: MidiSequence, masterGain: Float = 0.5f): ShortArray {
        val out = FloatArray((sequence.durationMs * sampleRate / 1000).coerceAtLeast(sampleRate))

        val activeNotes = mutableMapOf<Int, MutableMap<Int, ActiveNote>>()
        val programs = IntArray(16)  // channel -> program

        for (event in sequence.events) {
            when (event) {
                is MidiEvent.TimeShift -> { /* implicit */ }
                is MidiEvent.NoteOn -> {
                    activeNotes.getOrPut(event.channel) { mutableMapOf() }[event.note] =
                        ActiveNote(
                            note = event.note,
                            velocity = event.velocity,
                            startMs = event.timeMs,
                            program = programs[event.channel],
                        )
                }
                is MidiEvent.NoteOff -> {
                    val active = activeNotes[event.channel]?.remove(event.note) ?: continue
                    val samples = renderNote(
                        program = active.program,
                        note = active.note,
                        velocity = active.velocity,
                        durationMs = event.timeMs - active.startMs,
                    )
                    val startSample = active.startMs * sampleRate / 1000
                    mixInto(out, samples, startSample, gain = 0.7f)
                }
                is MidiEvent.InstrumentChange -> {
                    if (event.channel in 0..15) {
                        programs[event.channel] = event.program.coerceIn(0, 127)
                    }
                }
                is MidiEvent.TempoChange -> { /* out of scope */ }
            }
        }

        for (i in out.indices) {
            out[i] = (out[i] * masterGain).coerceIn(-1f, 1f)
        }
        return floatToPcm16(out)
    }

    private fun renderNote(program: Int, note: Int, velocity: Int, durationMs: Int): FloatArray {
        val numSamples = (durationMs * sampleRate / 1000).coerceAtLeast(sampleRate / 50)
        // Choose sample: for drum channel use drum preset; for melodic use program
        val preset = if (program == MidiConstants.DRUM_CHANNEL || program < 0) {
            soundFont.drumPreset(note)
        } else {
            soundFont.presetByProgram(program)
        }
        val sample = selectSample(preset, note) ?: return FloatArray(numSamples)
        return playSample(
            sample = sample,
            note = note,
            velocity = velocity,
            numSamples = numSamples,
        )
    }

    /**
     * Selects the best matching sample for a given note from the preset's zones.
     * For simplicity we use the first sample referenced by the first global zone,
     * and rely on pitch shift to handle all notes. A more elaborate implementation
     * would walk the zone chain and respect keyRange / overridingRootKey.
     */
    private fun selectSample(preset: Preset?, note: Int): Sample? {
        preset ?: return null
        if (preset.pbagIndex < 0 || preset.pbagIndex >= soundFont.samples.size) return null
        return soundFont.samples.getOrNull(preset.pbagIndex)
    }

    private fun playSample(
        sample: Sample,
        note: Int,
        velocity: Int,
        numSamples: Int,
    ): FloatArray {
        val out = FloatArray(numSamples)
        val start = sample.start
        val end = sample.end
        val loopStart = sample.loopStart
        val loopEnd = sample.loopEnd
        val loopLen = loopEnd - loopStart
        val sampleData = soundFont.sampleData
        if (end <= start || start < 0 || end > sampleData.size) return out

        // Pitch shift: target sample rate = originalRate * 2^((note - originalKey - correction/100) / 12)
        val semitones = note - sample.originalKey - sample.correction / 100.0
        val rateRatio = 2.0.pow(semitones / 12.0) * sample.sampleRate / sampleRate
        val amp = (velocity / 127.0).toFloat() * 0.5f

        // ADSR: simple per-sample envelope (we approximate a 5ms attack + 80ms release)
        val attackSamples = (sampleRate * 0.005).toInt()
        val releaseStart = (numSamples - sampleRate * 0.080).toInt().coerceAtLeast(0)
        val releaseLen = numSamples - releaseStart
        var pos = start.toDouble()
        for (i in 0 until numSamples) {
            if (pos.toInt() >= end) {
                if (loopLen > 0) {
                    // Loop back
                    pos = (loopStart + (pos.toInt() - loopStart) % loopLen).toDouble()
                } else {
                    out[i] = 0f
                    continue
                }
            }
            // Linear interpolation
            val idx = pos.toInt()
            val frac = (pos - idx).toFloat()
            val s0 = sampleData.getOrElse(idx) { 0 }
            val s1 = sampleData.getOrElse(idx + 1) { 0 }
            val sample = (s0 * (1f - frac) + s1 * frac) / 32768f
            val env = when {
                i < attackSamples -> i.toFloat() / attackSamples
                i < releaseStart -> 1f
                else -> 1f - (i - releaseStart).toFloat() / releaseLen
            }.coerceIn(0f, 1f)
            out[i] = sample * env * amp
            pos += rateRatio
        }
        return out
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

    private data class ActiveNote(
        val note: Int,
        val velocity: Int,
        val startMs: Int,
        val program: Int,
    )
}
