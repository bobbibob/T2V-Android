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
        val prompts = listOf(
            "door", "whoosh", "notification", "rain", "wind", "explosion", "click", "footstep",
            "heartbeat", "laser", "alarm", "water", "thunder", "applause", "whisper", "glass",
        )
        for (prompt in prompts) {
            val pcm = ProceduralAudioSynth.synthSound(prompt, 1, seed = 42L)
            assertEquals(ProceduralAudioSynth.SAMPLE_RATE, pcm.size)
            val peak = pcm.maxOf { kotlin.math.abs(it.toInt()) }
            assertTrue("$prompt should produce audible audio (peak=$peak)", peak > 500)
        }
    }

    @Test
    fun `sound synth aliases dispatch to the same families`() {
        val aliases = mapOf(
            "blaster fire" to "laser",
            "siren in the distance" to "alarm",
            "splash in a pool" to "water",
            "rapid clap crowd" to "applause",
            "break a jar" to "glass",
        )
        for ((alias, family) in aliases) {
            val byAlias = ProceduralAudioSynth.synthSound(alias, 1, seed = 7L)
            val byFamily = ProceduralAudioSynth.synthSound(family, 1, seed = 7L)
            assertTrue(
                "Alias '$alias' should match family '$family'",
                byAlias.contentEquals(byFamily),
            )
        }
    }

    @Test
    fun `music synth responds to distinct moods`() {
        val moods = listOf(
            "ambient", "dark", "uplifting", "sad", "tension", "dreamy",
            "mysterious", "peaceful", "romantic", "energetic", "jazzy", "warm",
        )
        for (mood in moods) {
            val pcm = ProceduralAudioSynth.synthMusic(mood, 2, seed = 11L)
            assertEquals(2 * ProceduralAudioSynth.SAMPLE_RATE, pcm.size)
            assertTrue("Mood $mood should be audible", pcm.maxOf { kotlin.math.abs(it.toInt()) } > 500)
        }
        // Different moods (same seed) must not collapse onto identical audio.
        for (i in moods.indices) {
            for (j in i + 1 until moods.size) {
                val a = ProceduralAudioSynth.synthMusic(moods[i], 2, seed = 11L)
                val b = ProceduralAudioSynth.synthMusic(moods[j], 2, seed = 11L)
                assertTrue(
                    "Moods ${moods[i]} and ${moods[j]} must not produce identical audio",
                    !a.contentEquals(b),
                )
            }
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
    fun `sound synth is deterministic with same seed`() {
        val a = ProceduralAudioSynth.synthSound("laser", 1, seed = 123L)
        val b = ProceduralAudioSynth.synthSound("laser", 1, seed = 123L)
        assertTrue("Same seed should produce identical output", a.contentEquals(b))
        val c = ProceduralAudioSynth.synthSound("laser", 1, seed = 124L)
        assertTrue("Different seed should differ", !a.contentEquals(c))
    }

    @Test
    fun `synth is deterministic with same seed`() {
        val a = ProceduralAudioSynth.synthMusic("ambient", 2, seed = 123L)
        val b = ProceduralAudioSynth.synthMusic("ambient", 2, seed = 123L)
        assertTrue("Same seed should produce identical output", a.contentEquals(b))
    }
}
