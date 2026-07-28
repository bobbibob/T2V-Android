package com.t2v.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressiveSpeechTest {
    @Test
    fun `Eleven v3 maps semantic cues without changing transcript`() {
        val voice = VoiceConfig(
            emotion = "sad",
            extras = mapOf(
                ExpressiveSpeech.DELIVERY to "whisper",
                ExpressiveSpeech.VOCAL_CUES to "breath|laugh",
            ),
        )

        assertEquals("[sad] [whispers] [takes a breath] [laughs]", ExpressiveSpeech.elevenV3Prefix(voice))
    }

    @Test
    fun `local fallback changes prosody but never emits reaction text`() {
        val source = VoiceConfig(
            emotion = "sad",
            extras = mapOf(
                ExpressiveSpeech.DELIVERY to "whisper",
                ExpressiveSpeech.VOCAL_CUES to "sigh",
            ),
        )
        val result = ExpressiveSpeech.localFallback(source)

        assertTrue(result.speed < 1.0)
        assertTrue(result.pitch < 1.0)
        assertTrue(result.volume < 1.0)
        assertFalse(result.extras.values.any { it.contains("[") })
    }

    @Test
    fun `local fallback strips cloud-only extras`() {
        val source = VoiceConfig(
            emotion = "sad",
            extras = mapOf(
                ExpressiveSpeech.DELIVERY to "whisper",
                ExpressiveSpeech.EMPHASIS to "strong",
                ExpressiveSpeech.VOCAL_CUES to "breath|laugh",
                "user.tag" to "keep-me",
            ),
        )
        val result = ExpressiveSpeech.localFallback(source)

        assertFalse(result.extras.containsKey(ExpressiveSpeech.VOCAL_CUES))
        assertFalse(result.extras.containsKey(ExpressiveSpeech.EMPHASIS))
        // DELIVERY is harmless on local engines but the other cloud-only keys
        // must not survive — they could otherwise leak into UI as raw tags.
        assertEquals("keep-me", result.extras["user.tag"])
    }

    @Test
    fun `instruction builds a single semantic string for OpenAI and Gemini`() {
        val voice = VoiceConfig(
            emotion = "happy",
            extras = mapOf(
                ExpressiveSpeech.DELIVERY to "soft",
                ExpressiveSpeech.EMPHASIS to "moderate",
                ExpressiveSpeech.VOCAL_CUES to "laugh",
            ),
        )
        val prompt = ExpressiveSpeech.instruction(voice)
        assertNotNull(prompt)
        assertTrue(prompt!!.contains("Emotion: happy"))
        assertTrue(prompt.contains("Delivery: soft"))
        assertTrue(prompt.contains("Emphasis: moderate"))
        assertTrue(prompt.contains("Before speaking"))
        assertTrue(prompt.contains("laugh"))
    }

    @Test
    fun `Eleven v3 prefix is empty for neutral voice`() {
        val voice = VoiceConfig()
        assertEquals("", ExpressiveSpeech.elevenV3Prefix(voice))
    }
}
