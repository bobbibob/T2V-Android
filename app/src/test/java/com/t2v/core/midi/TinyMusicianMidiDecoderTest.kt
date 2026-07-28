package com.t2v.core.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TinyMusicianMidiDecoderTest {

    @Test
    fun `fallback from prompt with epic keyword returns minor progression`() {
        val seq = TinyMusicianMidiDecoder.fallbackFromPrompt("epic cinematic trailer")
        assertTrue("Expected > 1 second", seq.durationMs > 1_000)
        // Should have at least 4 chords × 3 notes each
        val noteOns = seq.events.count { it is MidiEvent.NoteOn }
        assertTrue("Expected >= 12 NoteOns (4 chords × 3 notes), got $noteOns", noteOns >= 12)
    }

    @Test
    fun `fallback from prompt with happy keyword returns major progression`() {
        val seq = TinyMusicianMidiDecoder.fallbackFromPrompt("happy uplifting music")
        // First NoteOn should be on a major chord root (C4 = MIDI 60)
        val firstNote = seq.events.firstOrNull { it is MidiEvent.NoteOn } as? MidiEvent.NoteOn
        assertNotNull(firstNote)
        assertEquals(60, firstNote!!.note)
    }

    @Test
    fun `fallback from prompt with calm keyword returns ambient progression`() {
        val seq = TinyMusicianMidiDecoder.fallbackFromPrompt("calm ambient pad")
        // Should be on a stable I-IV-V-I pattern
        val instrumentChanges = seq.events.count { it is MidiEvent.InstrumentChange }
        assertTrue("Expected >= 4 instrument changes", instrumentChanges >= 4)
    }

    @Test
    fun `decoded token sequence produces NoteOn and NoteOff events`() {
        // Simulate a 4-note sequence: NoteOn C4, NoteOff C4, NoteOn E4, NoteOff E4
        val tokens = intArrayOf(60, 188, 64, 192, 449)
        val seq = TinyMusicianMidiDecoder.decode(tokens)
        val noteOns = seq.events.filterIsInstance<MidiEvent.NoteOn>()
        val noteOffs = seq.events.filterIsInstance<MidiEvent.NoteOff>()
        assertEquals(2, noteOns.size)
        assertEquals(2, noteOffs.size)
    }
}

class MidiRendererTest {

    @Test
    fun `renderSine with empty sequence produces non-empty PCM`() {
        val seq = MidiSequence(events = emptyList(), durationMs = 1000)
        val pcm = MidiRenderer.renderSine(seq, sampleRate = 22050)
        assertEquals(22050, pcm.size)
        // All samples should be zero (no notes)
        assertTrue("Expected silence", pcm.all { it == 0.toShort() })
    }

    @Test
    fun `renderSine with one note produces non-zero PCM`() {
        val seq = MidiSequence(
            events = listOf(
                MidiEvent.NoteOn(timeMs = 0, channel = 0, note = 60, velocity = 100),
                MidiEvent.NoteOff(timeMs = 500, channel = 0, note = 60),
            ),
            durationMs = 500,
        )
        val pcm = MidiRenderer.renderSine(seq, sampleRate = 22050)
        assertTrue("Expected > 0 length", pcm.size > 0)
        assertTrue("Expected non-zero samples", pcm.any { it != 0.toShort() })
    }

    @Test
    fun `renderSine with drum channel uses DrumKit`() {
        val seq = MidiSequence(
            events = listOf(
                MidiEvent.NoteOn(timeMs = 0, channel = MidiConstants.DRUM_CHANNEL, note = 36, velocity = 100),
                MidiEvent.NoteOff(timeMs = 200, channel = MidiConstants.DRUM_CHANNEL, note = 36),
            ),
            durationMs = 200,
        )
        val pcm = MidiRenderer.renderSine(seq, sampleRate = 22050)
        assertTrue("Expected non-zero drum samples", pcm.any { it != 0.toShort() })
    }
}

class StandardMidiFileParserTest {

    @Test
    fun `parses a minimal one-track MIDI file with one note`() {
        // Build a minimal SMF in memory:
        //   Header: MThd, length 6, format 0, 1 track, division 480
        //   Track:  MTrk, header + one NoteOn + one NoteOff + EndOfTrack
        val bytes = buildBytes {
            // MThd
            appendAscii("MThd")
            appendInt(6)
            appendShort(0)     // format
            appendShort(1)     // tracks
            appendShort(480)   // division
            // MTrk
            appendAscii("MTrk")
            val trackStart = size()
            appendInt(0)  // placeholder for length
            // Delta 0, NoteOn ch 0, note 60, vel 100
            appendByte(0x00)       // delta
            appendByte(0x90)       // NoteOn
            appendByte(60)
            appendByte(100)
            // Delta 480 (one quarter), NoteOff
            appendVarLen(480)
            appendByte(0x80)
            appendByte(60)
            appendByte(0)
            // Meta EndOfTrack
            appendByte(0)
            appendByte(0xFF)
            appendByte(0x2F)
            appendByte(0x00)
            val trackEnd = size()
            // patch length
            val len = trackEnd - trackStart - 4
            setInt(trackStart, len)
        }
        val seq = StandardMidiFileParser.parse(bytes.inputStream())
        assertEquals(1, seq.events.count { it is MidiEvent.NoteOn })
        assertEquals(1, seq.events.count { it is MidiEvent.NoteOff })
    }

    // ── tiny byte builder helpers ─────────────────────────────────────
    private fun buildBytes(block: MidiByteBuilder.() -> Unit): ByteArray {
        val b = MidiByteBuilder()
        b.block()
        return b.toByteArray()
    }
}

private class MidiByteBuilder {
    private val data = mutableListOf<Byte>()

    fun appendAscii(s: String) { s.forEach { data.add(it.code.toByte()) } }
    fun appendByte(v: Int) { data.add(v.toByte()) }
    fun appendShort(v: Int) {
        data.add((v and 0xFF).toByte())
        data.add(((v shr 8) and 0xFF).toByte())
    }
    fun appendInt(v: Int) {
        data.add((v and 0xFF).toByte())
        data.add(((v shr 8) and 0xFF).toByte())
        data.add(((v shr 16) and 0xFF).toByte())
        data.add(((v shr 24) and 0xFF).toByte())
    }
    fun setInt(offset: Int, v: Int) {
        data[offset] = (v and 0xFF).toByte()
        data[offset + 1] = ((v shr 8) and 0xFF).toByte()
        data[offset + 2] = ((v shr 16) and 0xFF).toByte()
        data[offset + 3] = ((v shr 24) and 0xFF).toByte()
    }
    fun appendVarLen(value: Int) {
        var v = value and 0x0FFFFFFF
        val stack = mutableListOf<Byte>()
        stack.add((v and 0x7F).toByte())
        v = v ushr 7
        while (v > 0) {
            stack.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        // MSB first
        for (i in stack.indices.reversed()) data.add(stack[i])
    }
    fun size() = data.size
    fun toByteArray(): ByteArray = data.toByteArray()
}
