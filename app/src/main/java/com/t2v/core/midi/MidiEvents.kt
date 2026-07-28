package com.t2v.core.midi

/**
 * Упрощённое представление MIDI-событий, которое мы получаем после
 * detokenisation из TinyMusician (или читаем из MIDI-файла).
 *
 * Это **НЕ полный Standard MIDI File (SMF)** — это минимальное подмножество,
 * достаточное для рендеринга фоновой музыки:
 *  - NoteOn (нота + velocity)
 *  - NoteOff (нота)
 *  - TimeShift (дельта в миллисекундах)
 *  - InstrumentChange (channel 0-15, program 0-127)
 *  - TempoChange (BPM, для будущего)
 *
 * Drum-канал (channel 9 по SMF convention) рендерится отдельно
 * через набор GM-drum-сэмплов.
 *
 * **Не зависит от `javax.sound.midi`** — мы хотим работать на Android
 * без десктопных зависимостей.
 */
data class MidiSequence(
    val events: List<MidiEvent>,
    val durationMs: Int,
    val ticksPerQuarter: Int = 480,
    val tempoBpm: Int = 120,
)

sealed interface MidiEvent {
    val timeMs: Int

    data class NoteOn(
        override val timeMs: Int,
        val channel: Int,
        val note: Int,
        val velocity: Int,
    ) : MidiEvent

    data class NoteOff(
        override val timeMs: Int,
        val channel: Int,
        val note: Int,
    ) : MidiEvent

    data class InstrumentChange(
        override val timeMs: Int,
        val channel: Int,
        val program: Int,
    ) : MidiEvent

    data class TempoChange(
        override val timeMs: Int,
        val bpm: Int,
    ) : MidiEvent

    data class TimeShift(
        override val timeMs: Int,
        val deltaMs: Int,
    ) : MidiEvent
}

object MidiConstants {
    /** Standard MIDI File: channel 10 (index 9) — drums. */
    const val DRUM_CHANNEL = 9

    /** 128 GM-программ по порядку (Families). */
    val PROGRAM_FAMILY: IntArray = intArrayOf(
        // 0-7 Piano
        0, 0, 0, 0, 0, 0, 0, 0,
        // 8-15 Chromatic Percussion
        8, 8, 8, 8, 8, 8, 8, 8,
        // 16-23 Organ
        16, 16, 16, 16, 16, 16, 16, 16,
        // 24-31 Guitar
        24, 24, 24, 24, 24, 24, 24, 24,
        // 32-39 Bass
        32, 32, 32, 32, 32, 32, 32, 32,
        // 40-47 Strings
        40, 40, 40, 40, 40, 40, 40, 40,
        // 48-55 Ensemble
        48, 48, 48, 48, 48, 48, 48, 48,
        // 56-63 Brass
        56, 56, 56, 56, 56, 56, 56, 56,
        // 64-71 Reed (Woodwind)
        64, 64, 64, 64, 64, 64, 64, 64,
        // 72-79 Pipe
        72, 72, 72, 72, 72, 72, 72, 72,
        // 80-87 Synth Lead
        80, 80, 80, 80, 80, 80, 80, 80,
        // 88-95 Synth Pad
        88, 88, 88, 88, 88, 88, 88, 88,
        // 96-103 Synth Effects
        96, 96, 96, 96, 96, 96, 96, 96,
        // 104-111 Ethnic
        104, 104, 104, 104, 104, 104, 104, 104,
        // 112-119 Percussive
        112, 112, 112, 112, 112, 112, 112, 112,
        // 120-127 Sound Effects
        120, 120, 120, 120, 120, 120, 120, 120,
    )
}
