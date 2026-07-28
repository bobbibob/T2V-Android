package com.t2v.core.midi

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Читает Standard MIDI File (SMF) Format 0 / Format 1 в наш
 * [MidiSequence]. Минимальная реализация: только NoteOn/Off,
 * ProgramChange, Tempo.
 *
 * Используется, когда у нас есть готовый .mid файл (например,
 * от TinyMusician inference в будущем). Сейчас TinyMusician
 * генерирует собственные MIDI-токены, и мы конвертируем их
 * через [com.t2v.core.midi.TinyMusicianMidiDecoder].
 *
 * SMF-спека: http://www.music.mcgill.ca/~ich/classes/mumt306/StandardMIDIfileformat.html
 */
object StandardMidiFileParser {

    fun parse(input: InputStream): MidiSequence {
        val bb = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val headerId = bb.int
        require(headerId == 0x6468744D) { "Not a MIDI file: bad header chunk id 0x${"%08X".format(headerId)}" }
        val headerLen = bb.int
        require(headerLen == 6) { "Bad header chunk length: $headerLen" }
        val trackCount = bb.short.toInt() and 0xFFFF
        val division = bb.short.toInt() and 0xFFFF
        require(division and 0x8000 == 0) { "SMPTE timecode not supported" }
        val ticksPerQuarter = division and 0x7FFF

        val tracks = mutableListOf<List<RawMidiEvent>>()
        repeat(trackCount) {
            tracks.add(readTrack(bb))
        }
        return mergeTracks(tracks, ticksPerQuarter)
    }

    private fun readTrack(bb: ByteBuffer): List<RawMidiEvent> {
        val trackId = bb.int
        require(trackId == 0x6B72544D) { "Not a track chunk: 0x${"%08X".format(trackId)}" }
        val trackLen = bb.int.toLong() and 0xFFFFFFFFL
        // We read the track body into a bounded byte array so we can
        // always know exactly how many bytes are left.
        val trackData = ByteArray(trackLen.toInt())
        bb.get(trackData)
        return parseTrackBytes(trackData)
    }

    /**
     * Parse a single track's bytes. We work on a byte array so we can
     * always know exactly how many bytes are left.
     */
    private fun parseTrackBytes(bytes: ByteArray): List<RawMidiEvent> {
        val events = mutableListOf<RawMidiEvent>()
        var pos = 0
        var absoluteTick = 0
        var runningStatus = 0
        while (pos < bytes.size) {
            val (delta, deltaBytes) = readVariableLength(bytes, pos)
            pos += deltaBytes
            absoluteTick += delta
            if (pos >= bytes.size) break
            var firstByte = bytes[pos].toInt() and 0xFF
            pos++
            val status = if (firstByte and 0x80 != 0) {
                runningStatus = firstByte
                firstByte
            } else {
                runningStatus
            }
            val high = status and 0xF0
            val channel = status and 0x0F
            when (high) {
                0x80 -> {
                    val note = bytes[pos].toInt() and 0xFF; pos++
                    val vel = bytes[pos].toInt() and 0xFF; pos++
                    events.add(RawMidiEvent(absoluteTick, NoteOff(channel, note, vel)))
                }
                0x90 -> {
                    val note = bytes[pos].toInt() and 0xFF; pos++
                    val vel = bytes[pos].toInt() and 0xFF; pos++
                    if (vel == 0) {
                        events.add(RawMidiEvent(absoluteTick, NoteOff(channel, note, 0)))
                    } else {
                        events.add(RawMidiEvent(absoluteTick, NoteOn(channel, note, vel)))
                    }
                }
                0xC0 -> {
                    val program = bytes[pos].toInt() and 0xFF; pos++
                    events.add(RawMidiEvent(absoluteTick, ProgramChange(channel, program)))
                }
                0xFF -> {
                    val metaType = bytes[pos].toInt() and 0xFF; pos++
                    val (len, lenBytes) = readVariableLength(bytes, pos)
                    pos += lenBytes
                    when (metaType) {
                        0x51 -> {
                            if (pos + 2 < bytes.size) {
                                val b0 = bytes[pos].toInt() and 0xFF
                                val b1 = bytes[pos + 1].toInt() and 0xFF
                                val b2 = bytes[pos + 2].toInt() and 0xFF
                                val micros = (b0 shl 16) or (b1 shl 8) or b2
                                val bpm = (60_000_000.0 / micros).toInt()
                                events.add(RawMidiEvent(absoluteTick, Tempo(bpm)))
                            }
                            pos += len
                        }
                        0x2F -> {
                            // End of track
                            return events
                        }
                        else -> {
                            pos += len
                        }
                    }
                    if (len % 2 == 1) pos++  // pad byte
                }
                0xF0, 0xF7 -> {
                    // Sysex: skip the rest of the message
                    val (len, lenBytes) = readVariableLength(bytes, pos)
                    pos += lenBytes + len
                    if (len % 2 == 1) pos++
                }
                else -> {
                    // Channel messages with 2 data bytes
                    if (high in setOf(0xA0, 0xB0, 0xE0)) {
                        pos += 2
                    } else if (high == 0xD0) {
                        pos += 1
                    } else {
                        // Unknown — bail
                        return events
                    }
                }
            }
        }
        return events
    }

    /** Reads a variable-length quantity and returns the (value, bytesConsumed) tuple. */
    private fun readVariableLength(bytes: ByteArray, pos: Int): Pair<Int, Int> {
        var value = 0
        var i = 0
        while (true) {
            val b = bytes[pos + i].toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F)
            i++
            if (b and 0x80 == 0) break
        }
        return value to i
    }

    private fun mergeTracks(
        tracks: List<List<RawMidiEvent>>,
        ticksPerQuarter: Int,
    ): MidiSequence {
        val all = tracks.flatten().sortedBy { it.tick }
        val tempoBpm = all.firstOrNull { it.event is Tempo }?.let { (it.event as Tempo).bpm } ?: 120
        val microsPerQuarter = 60_000_000 / tempoBpm
        val microsPerTick = microsPerQuarter.toDouble() / ticksPerQuarter

        val out = mutableListOf<MidiEvent>()
        var lastTick = 0
        for (raw in all) {
            val deltaTicks = raw.tick - lastTick
            val deltaMs = (deltaTicks * microsPerTick).toInt().coerceAtLeast(0)
            val absoluteMs = (raw.tick * microsPerTick).toInt()
            if (deltaMs > 0) {
                out.add(MidiEvent.TimeShift(absoluteMs, deltaMs))
            }
            when (val e = raw.event) {
                is NoteOn -> out.add(MidiEvent.NoteOn(absoluteMs, e.channel, e.note, e.velocity))
                is NoteOff -> out.add(MidiEvent.NoteOff(absoluteMs, e.channel, e.note))
                is ProgramChange -> out.add(MidiEvent.InstrumentChange(absoluteMs, e.channel, e.program))
                is Tempo -> out.add(MidiEvent.TempoChange(absoluteMs, e.bpm))
            }
            lastTick = raw.tick
        }
        val totalMs = (all.maxOfOrNull { it.tick } ?: 0).let {
            (it * microsPerTick).toInt()
        }
        return MidiSequence(
            events = out,
            durationMs = totalMs,
            ticksPerQuarter = ticksPerQuarter,
            tempoBpm = tempoBpm,
        )
    }

    private data class RawMidiEvent(val tick: Int, val event: Event)

    private sealed interface Event
    private data class NoteOn(val channel: Int, val note: Int, val velocity: Int) : Event
    private data class NoteOff(val channel: Int, val note: Int, val velocity: Int) : Event
    private data class ProgramChange(val channel: Int, val program: Int) : Event
    private data class Tempo(val bpm: Int) : Event
}
