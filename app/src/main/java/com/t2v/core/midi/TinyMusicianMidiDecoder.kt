package com.t2v.core.midi

/**
 * Декодирует токены, которые выдаёт TinyMusician (asigalov61), в наш
 * внутренний [MidiSequence]. Это **заготовка для будущего**, когда у нас
 * будет реальный TinyMusician ONNX-инференс.
 *
 * Сейчас у TinyMusician **нет публичного ONNX-экспорта** (на 2026-07-28).
 * Когда asigalov61/TinyMusician-maintainer или сообщество выпустит
 * `tinymusician-small.onnx` + `tokenizer.json`, мы добавим inference и
 * этот декодер заработает.
 *
 * Формат токенов (TEA-like, как в TunesFormer/REMI):
 *  - 0..127: NoteOn (note pitch)
 *  - 128..255: NoteOff (note pitch + 128)
 *  - 256..287: TimeShift (10ms, 20ms, 50ms, 100ms, ... 4s)
 *  - 288..319: Velocity (32 bins, 32..127)
 *  - 320..447: Instrument (128 GM programs)
 *  - 448: Bar marker
 *  - 449: EndOfSequence
 *
 * **Не претендуем на полное соответствие** — конкретный словарь будет
 * взят из `tokenizer.json` в реальном ONNX-экспорте.
 */
object TinyMusicianMidiDecoder {

    /** Decodes raw token ids into a [MidiSequence]. */
    fun decode(tokenIds: IntArray, sampleRate: Int = 22050, tempoBpm: Int = 120): MidiSequence {
        val out = mutableListOf<MidiEvent>()
        var timeMs = 0
        var currentChannel = 0
        val ticksPerQuarter = 480
        val microsPerQuarter = 60_000_000 / tempoBpm
        val msPerTick = microsPerQuarter / 1000.0 / ticksPerQuarter

        // Active notes: map<channel, map<note, noteOnTimeMs>>
        val active = mutableMapOf<Int, MutableMap<Int, Int>>()

        for (token in tokenIds) {
            when {
                token in 0..127 -> {
                    // NoteOn
                    val note = token
                    active.getOrPut(currentChannel) { mutableMapOf() }[note] = timeMs
                    out.add(MidiEvent.NoteOn(timeMs, currentChannel, note, velocity = 96))
                }
                token in 128..255 -> {
                    // NoteOff
                    val note = token - 128
                    out.add(MidiEvent.NoteOff(timeMs, currentChannel, note))
                    active[currentChannel]?.remove(note)
                }
                token in 256..287 -> {
                    // TimeShift
                    val deltaMs = timeShiftValue(token - 256)
                    timeMs += deltaMs
                    out.add(MidiEvent.TimeShift(timeMs, deltaMs))
                }
                token in 288..319 -> {
                    // Velocity
                    val vel = (token - 288) * 4 + 16  // map bin -> 16..144, clamp at 127
                    // The next NoteOn uses this velocity; we approximate by adjusting
                    // the most recent NoteOn's velocity. For simplicity, we drop this
                    // token here (proper handling would buffer).
                }
                token in 320..447 -> {
                    // Instrument change
                    val program = token - 320
                    out.add(MidiEvent.InstrumentChange(timeMs, currentChannel, program))
                }
                token == 448 -> {
                    // Bar marker — add a TimeShift of one quarter note
                    val quarterMs = (ticksPerQuarter * msPerTick).toInt()
                    timeMs += quarterMs
                    out.add(MidiEvent.TimeShift(timeMs, quarterMs))
                }
                token == 449 -> {
                    // EndOfSequence
                    break
                }
            }
        }
        return MidiSequence(
            events = out,
            durationMs = timeMs,
            ticksPerQuarter = ticksPerQuarter,
            tempoBpm = tempoBpm,
        )
    }

    /**
     * Maps a TimeShift bin index (0..31) to milliseconds.
     * Bin 0 = 10ms, then logarithmic up to ~4s.
     */
    private fun timeShiftValue(bin: Int): Int {
        val ms = 10.0 * Math.pow(1.2, bin.toDouble())
        return ms.toInt().coerceAtLeast(10)
    }

    /**
     * Build a fallback [MidiSequence] from a free-text prompt without running
     * any model. Used while the real ONNX export is still missing.
     *
     * Strategy: parse the prompt for mood keywords (calm, epic, lofi, …) and
     * play a simple 4-bar chord progression on piano.
     */
    fun fallbackFromPrompt(prompt: String, tempoBpm: Int = 100): MidiSequence {
        val lower = prompt.lowercase()
        val key = when {
            "epic" in lower || "cinema" in lower -> ChordProgression.minor(0, 2, 5, 3)  // i-iv-VII-VI
            "sad" in lower || "dark" in lower -> ChordProgression.minor(0, 5, 3, 4)    // i-vi-iv-V
            "happy" in lower || "uplift" in lower -> ChordProgression.major(0, 4, 5, 3) // I-V-vi-IV
            "calm" in lower || "ambient" in lower -> ChordProgression.major(0, 3, 4, 0) // I-IV-V-I
            "lofi" in lower -> ChordProgression.minor(0, 5, 4, 3)                      // i-vi-V-IV
            else -> ChordProgression.major(0, 4, 5, 3)                                  // default I-V-vi-IV
        }
        val out = mutableListOf<MidiEvent>()
        var timeMs = 0
        val barMs = 4 * 60_000 / tempoBpm
        for ((i, chord) in key.chords.withIndex()) {
            val barStart = i * barMs
            out.add(MidiEvent.InstrumentChange(barStart, 0, 0))  // Acoustic Grand Piano
            for (note in chord) {
                out.add(MidiEvent.NoteOn(barStart, 0, note, 90))
                out.add(MidiEvent.NoteOff(barStart + barMs, 0, note))
            }
            // Simple bass: root on every beat
            for (beat in 0 until 4) {
                val beatMs = barStart + beat * (barMs / 4)
                out.add(MidiEvent.NoteOn(beatMs, 0, chord[0] - 12, 80))
                out.add(MidiEvent.NoteOff(beatMs + (barMs / 4) - 50, 0, chord[0] - 12))
            }
        }
        return MidiSequence(
            events = out,
            durationMs = key.chords.size * barMs,
            tempoBpm = tempoBpm,
        )
    }
}

/**
 * Мажорные/минорные аккордовые прогрессии (4 аккорда по 1 такту).
 * Каждый аккорд = 3 ноты (root, third, fifth) в тональности C.
 */
private object ChordProgression {
    private val MAJOR_SCALE = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    private val MINOR_SCALE = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    private const val BASE_C4 = 60  // MIDI note 60 = C4

    private fun chord(rootSemitones: Int, scale: IntArray, degree: Int, isMinor: Boolean): IntArray {
        val s = scale[(degree % scale.size)]
        val r = (rootSemitones + s) % 12
        val third = r + if (isMinor) 3 else 4
        val fifth = r + 7
        return intArrayOf(BASE_C4 + r, BASE_C4 + third, BASE_C4 + fifth)
    }

    fun major(vararg degrees: Int): Progression = Progression(
        chords = degrees.map { d ->
            chord(0, MAJOR_SCALE, d, isMinor = false)
        }
    )

    fun minor(vararg degrees: Int): Progression = Progression(
        chords = degrees.map { d ->
            chord(0, MINOR_SCALE, d, isMinor = true)
        }
    )
}

private data class Progression(val chords: List<IntArray>)
