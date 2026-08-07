package com.t2v.tts.engines

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KokoroTtsEngineTest {

    @Test
    fun `short text is passed through unsplit`() {
        val text = "Hello, world. This is short."
        val parts = KokoroTtsEngine.splitLongText(text)
        assertEquals(listOf(text), parts)
    }

    @Test
    fun `long text is split on sentence boundaries`() {
        val sentence = "This is a fairly normal sentence that stays well under the native limit. "
        val text = sentence.repeat(120)
        assertTrue(text.length > 1200)

        val parts = KokoroTtsEngine.splitLongText(text)
        assertTrue("Expected multiple parts, got ${parts.size}", parts.size > 1)
        parts.forEach { part ->
            assertTrue(
                "Part length ${part.length} must not exceed the native cap",
                part.length <= 1200,
            )
        }
    }

    @Test
    fun `split preserves the original text after re-joining`() {
        val sentence = "Word one. Word two. Word three. Let us make each quite long to force splitting. "
        val text = sentence.repeat(40)
        val parts = KokoroTtsEngine.splitLongText(text)
        val joinedHalfway = parts.joinToString(" ")
        // Splitting is lossy about double spaces but content words are preserved.
        assertEquals(text.split(" ").filter { it.isNotBlank() }, joinedHalfway.split(" ").filter { it.isNotBlank() })
    }

    @Test
    fun `pathological single long word is hard cut into capped pieces`() {
        val word = "x".repeat(5000)
        val parts = KokoroTtsEngine.splitLongText(word)
        assertTrue(parts.size >= 4)
        assertTrue(parts.all { it.length <= 1200 })
        assertEquals(5000, parts.sumOf { it.length })
    }
}