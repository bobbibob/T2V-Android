package com.t2v.generators.synth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralAudioSynthTest {

    @Test
    fun `music synth produces non-silent audio of correct length`() {
        val pcm = ProceduralAudioSynth.synthMusic("ambient calm pad", 3, seed = 42L)
        assertEquals(3 * ProceduralAudioSynth.SAMPLE_RATE, pcm.size)
        val nonZero = pcm.count { it != 0.toShort() }
        assertTrue("Music should have non-zero samples, got $nonZero/${pcm.size}", nonZero > pcm.size / 2)
        val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("Peak should be > 1000, got $peak", peak > 1000)
        assertTrue("Peak should be < Short.MAX, got $peak", peak < Short.MAX_VALUE.toInt())
    }

    @Test
    fun `music synth responds to different moods`() {
        val ambient = ProceduralAudioSynth.synthMusic("ambient", 2, seed = 1L)
        val dark = ProceduralAudioSynth.synthMusic("dark tension", 2, seed = 1L)
        val ambientSum = ambient.sumOf { it.toLong() }
        val darkSum = dark.sumOf { it.toLong() }
        assertTrue("Different moods should produce different audio", ambientSum != darkSum)
    }

    @Test
    fun `sound synth produces non-silent audio`() {
        val pcm = ProceduralAudioSynth.synthSound("door close", 1, seed = 42L)
        assertEquals(ProceduralAudioSynth.SAMPLE_RATE, pcm.size)
        val nonZero = pcm.count { it != 0.toShort() }
        assertTrue("Sound should have non-zero samples", nonZero > 100)
    }

    @Test
    fun `sound synth handles all keyword families`() {
        val prompts = listOf("door", "whoosh", "notification", "rain", "wind", "explosion", "click", "footstep", "heartbeat")
        for (prompt in prompts) {
            val pcm = ProceduralAudioSynth.synthSound(prompt, 1, seed = 42L)
            assertEquals(ProceduralAudioSynth.SAMPLE_RATE, pcm.size)
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("$prompt should produce audible audio (peak=$peak)", peak > 500)
        }
    }

    @Test
    fun `sound synth falls back for unknown prompts`() {
        val pcm = ProceduralAudioSynth.synthSound("something completely unknown", 1, seed = 42L)
        assertEquals(ProceduralAudioSynth.SAMPLE_RATE, pcm.size)
        val nonZero = pcm.count { it != 0.toShort() }
        assertTrue("Unknown prompt should still produce audio", nonZero > 100)
    }

    @Test
    fun `synth is deterministic with same seed`() {
        val a = ProceduralAudioSynth.synthMusic("ambient", 2, seed = 123L)
        val b = ProceduralAudioSynth.synthMusic("ambient", 2, seed = 123L)
        assertTrue("Same seed should produce identical output", a.contentEquals(b))
    }
}
