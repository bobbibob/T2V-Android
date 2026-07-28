package com.t2v

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.t2v.core.markup.LTVMarkupParser
import com.t2v.core.text.TextProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EndToEndSmokeTest {

    @Test
    fun textProcessor_and_markup_works_on_android() {
        val tp = TextProcessor(chunkSize = 1000)
        val parser = LTVMarkupParser()

        val raw = """
            Chapter 1: Beginning
            First paragraph.

            {{voice "Alice"}}
            Hello, this is a test with {{speed 1.2}} emphasis.

            Chapter 2: Middle
            {{pause 500ms}}After the pause, more text.
        """.trimIndent()

        val (sections, chunks) = tp.process(tp.clean(raw))
        assertTrue("Sections should be > 0", sections.isNotEmpty())
        assertTrue("Chunks should be > 0", chunks.isNotEmpty())

        val parsed = parser.parse(raw)
        assertTrue("Voice commands > 0", parsed.commands.any { it is com.t2v.core.markup.MarkupCommand.Voice })
        assertTrue("Pause commands > 0", parsed.commands.any { it is com.t2v.core.markup.MarkupCommand.Pause })
        assertEquals("Alice", parsed.finalState.voice)
    }
}
