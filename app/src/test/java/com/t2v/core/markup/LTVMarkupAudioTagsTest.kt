package com.t2v.core.markup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LTVMarkupAudioTagsTest {

    private val parser = LTVMarkupParser()

    @Test
    fun `extractAudioTags returns music and sfx with prompt and offsets`() {
        val src = "Hello.<music>ambient pad</music>World.<sfx>door close</sfx>Done."
        val tags = parser.extractAudioTags(src)
        assertEquals(2, tags.size)
        assertEquals(AudioTag.Category.Music, tags[0].category)
        assertEquals("ambient pad", tags[0].prompt)
        assertEquals(AudioTag.Category.Sound, tags[1].category)
        assertEquals("door close", tags[1].prompt)
        assertTrue(tags[0].endOffset > tags[0].startOffset)
        assertTrue(tags[1].endOffset > tags[1].startOffset)
    }

    @Test
    fun `parseSpans breaks at every audio tag boundary and strips prompt from voice`() {
        val spans = parser.parseSpans("Alpha.<music>cinema</music>Beta.<sfx>whoosh</sfx>Gamma.")
        // Expect 4 spans:
        //   1. Alpha.
        //   2. "" (after </music>, before Beta)
        //   3. Beta.
        //   4. "" (after </sfx>, before Gamma)
        //   5. Gamma.  -- this might collapse with 4 because both are empty; trim.
        val nonEmpty = spans.filter { it.text.isNotBlank() || it.trailingAudioTag != null }
        // We expect at least 3 non-empty spans: Alpha., Beta., Gamma.
        assertEquals(3, nonEmpty.count { it.text.isNotBlank() })
        assertEquals(2, nonEmpty.count { it.trailingAudioTag != null })
    }

    @Test
    fun `parseSpans handles mixed ltv commands and audio tags`() {
        val src = "{{emotion happy}}<music>ambient</music>Hi.<sfx>door</sfx>Bye."
        val spans = parser.parseSpans(src)
        assertTrue(spans.any { it.trailingAudioTag?.category == AudioTag.Category.Music })
        assertTrue(spans.any { it.trailingAudioTag?.category == AudioTag.Category.Sound })
    }

    @Test
    fun `audio tag with no surrounding text still produces a boundary`() {
        val spans = parser.parseSpans("<music>cinema</music>")
        // First span may be empty with trailingAudioTag; pipeline uses position 0.
        assertTrue(spans.any { it.trailingAudioTag?.category == AudioTag.Category.Music })
    }
}
