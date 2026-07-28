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
