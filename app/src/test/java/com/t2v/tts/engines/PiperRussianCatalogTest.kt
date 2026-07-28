package com.t2v.tts.engines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiperRussianCatalogTest {
    @Test
    fun `catalog keeps four unique Russian voices on-device`() {
        val voices = PiperRussianTtsEngine.RUSSIAN_VOICES
        val russianVoices = voices.filter { it.language == "ru-RU" }

        assertEquals(4, russianVoices.size)
        assertEquals(
            listOf("irina", "denis", "dmitri", "ruslan"),
            russianVoices.map { it.id },
        )
        assertTrue(voices.size >= 6)
        assertTrue(voices.size == voices.map { it.id }.distinct().size)
    }

    @Test
    fun `catalog exposes additional verified Piper languages`() {
        val voices = PiperRussianTtsEngine.RUSSIAN_VOICES
        val expectedLanguages = setOf(
            "ru-RU", "en-US", "en-GB", "de-DE", "de-AT", "fr-FR",
            "es-ES", "es-MX", "it-IT", "zh-CN", "ja-JP", "hi-IN",
            "bn-IN", "ar", "ko-KR",
        )
        val actualLanguages = voices.map { it.language }.toSet()
        assertTrue(
            "Missing Piper languages in catalog: ${expectedLanguages - actualLanguages}",
            expectedLanguages.all { it in actualLanguages },
        )
    }

    @Test
    fun `every en-us voice id is unique`() {
        val voices = PiperRussianTtsEngine.RUSSIAN_VOICES.filter { it.language == "en-US" }
        assertTrue(voices.size >= 4)
        assertEquals(voices.size, voices.map { it.id }.distinct().size)
    }

    @Test
    fun `every voice archive points to the official sherpa-onnx release`() {
        val voices = PiperRussianTtsEngine.RUSSIAN_VOICES
        assertTrue(voices.isNotEmpty())
        assertTrue(
            "All voices must be hosted on sherpa-onnx releases",
            voices.all { it.archiveUrl.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/") },
        )
        assertTrue(
            "All voices must ship as tar.bz2 archives",
            voices.all { it.archiveUrl.endsWith(".tar.bz2") },
        )
        assertTrue(
            "Approximate size must be a positive number for every voice",
            voices.all { it.approximateSizeBytes > 0 },
        )
    }
}