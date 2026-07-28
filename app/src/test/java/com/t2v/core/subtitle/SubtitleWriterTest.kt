package com.t2v.core.subtitle

import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleWriterTest {

    @Test
    fun `srt format has correct time format`() {
        val cue = SubtitleCue(1, 1.5f, 3.0f, "Hello")
        val srt = SubtitleWriter.formatSrt(listOf(cue))
        assertTrue(srt.contains("00:00:01,500 --> 00:00:03,000"))
        assertTrue(srt.contains("Hello"))
    }

    @Test
    fun `ass format includes karaoke tags`() {
        val cue = SubtitleCue(
            index = 1,
            startSec = 0f,
            endSec = 2f,
            text = "ignored",
            words = listOf(
                WordTiming("Hello", 0f, 1f),
                WordTiming("world", 1f, 2f),
            ),
        )
        val ass = SubtitleWriter.formatAss(listOf(cue))
        assertTrue(ass.contains("[Script Info]"))
        assertTrue(ass.contains("{\\kf"))
        assertTrue(ass.contains("Hello"))
        assertTrue(ass.contains("world"))
    }

    @Test
    fun `srt has blank line between cues`() {
        val cues = listOf(
            SubtitleCue(1, 0f, 1f, "A"),
            SubtitleCue(2, 1f, 2f, "B"),
        )
        val srt = SubtitleWriter.formatSrt(cues)
        assertTrue(srt.contains("\n\n"))
    }
}
