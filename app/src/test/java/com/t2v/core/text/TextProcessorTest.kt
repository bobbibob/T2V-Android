package com.t2v.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextProcessorTest {

    private val tp = TextProcessor(chunkSize = 200, minChunkSize = 50, random = kotlin.random.Random(42))

    @Test
    fun `clean removes control chars and collapses whitespace`() {
        val input = "Hello\u0000World\r\nFoo\r\n\r\n\r\nBar"
        val out = tp.clean(input)
        assertEquals("HelloWorld\nFoo\n\nBar", out)
    }

    @Test
    fun `splitSections detects chapters and lessons`() {
        val text = """
            Chapter 1: The beginning
            First paragraph of chapter one.

            Chapter 2: The middle
            Second chapter content.

            # Markdown heading
            Markdown content here.

            Lección 3 - inicio
            Spanish content.
        """.trimIndent()

        val sections = tp.splitSections(tp.clean(text))
        assertEquals(4, sections.size)
        assertEquals("Chapter 1: The beginning", sections[0].title)
        assertEquals("Chapter 2: The middle", sections[1].title)
        assertEquals("# Markdown heading", sections[2].title)
        assertEquals("Lecci\u00f3n 3 - inicio", sections[3].title)
        assertTrue(sections[0].text.contains("First paragraph"))
    }

    @Test
    fun `splitChunks keeps short paragraphs in one chunk`() {
        val sec = TextSection("S", "Short paragraph.\n\nAnother short one.")
        val chunks = tp.splitChunks(sec)
        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.endsParagraph })
    }

    @Test
    fun `splitChunks splits long paragraphs by sentences`() {
        val long = "Sentence one. Sentence two. Sentence three. Sentence four."
        val tpSmall = TextProcessor(chunkSize = 40, minChunkSize = 10, random = kotlin.random.Random(1))
        val chunks = tpSmall.splitChunks(TextSection("S", long))
        assertTrue("Expected >1 chunks, got ${chunks.size}", chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 40 })
    }

    @Test
    fun `process pipeline returns sections and chunks`() {
        val result = tp.process("Hello world. This is a test.")
        assertEquals(1, result.sections.size)
        assertTrue(result.chunks.isNotEmpty())
    }

    @Test
    fun `no sections for empty text`() {
        val result = tp.process("")
        assertEquals(0, result.sections.size)
        assertEquals(0, result.chunks.size)
    }
}
