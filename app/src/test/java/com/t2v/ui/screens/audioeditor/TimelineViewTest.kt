package com.t2v.ui.screens.audioeditor

import com.t2v.core.audio.AudioEditClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineViewTest {

    @Test
    fun `clipDurationMs uses endMs-startMs when end is set`() {
        val clip = AudioEditClip(
            sourcePath = "/nonexistent.wav",
            startMs = 1000,
            endMs = 4000,
            speed = 1.0,
        )
        assertEquals(3000L, clipDurationMs(clip))
    }

    @Test
    fun `clipDurationMs applies speed multiplier`() {
        val clip = AudioEditClip(
            sourcePath = "/nonexistent.wav",
            startMs = 1000,
            endMs = 4000,
            speed = 2.0,
        )
        assertEquals(1500L, clipDurationMs(clip))
    }

    @Test
    fun `clipDurationMs halves duration at half speed`() {
        val clip = AudioEditClip(
            sourcePath = "/nonexistent.wav",
            startMs = 0,
            endMs = 2000,
            speed = 0.5,
        )
        assertEquals(4000L, clipDurationMs(clip))
    }

    @Test
    fun `clipDurationMs returns default 1000ms for missing file`() {
        val clip = AudioEditClip(
            sourcePath = "/nonexistent.wav",
            startMs = 0,
            endMs = 0,
            speed = 1.0,
        )
        assertEquals(1000L, clipDurationMs(clip))
    }

    @Test
    fun `clipDurationMs enforces minimum 100ms`() {
        val clip = AudioEditClip(
            sourcePath = "/nonexistent.wav",
            startMs = 0,
            endMs = 50,
            speed = 1.0,
        )
        assertEquals(100L, clipDurationMs(clip))
    }

    @Test
    fun `clipDurationMs estimates from file size when endMs is zero`() {
        val tmpFile = java.io.File.createTempFile("clip-test", ".wav")
        tmpFile.deleteOnExit()
        // 44100 bytes of data + 44 byte header = 1 second at 22050 Hz mono 16-bit
        tmpFile.writeBytes(ByteArray(44100 + 44))
        val clip = AudioEditClip(
            sourcePath = tmpFile.absolutePath,
            startMs = 0,
            endMs = 0,
            speed = 1.0,
        )
        val duration = clipDurationMs(clip)
        assertTrue("expected ~1000ms, got $duration", duration in 900..1100)
    }
}