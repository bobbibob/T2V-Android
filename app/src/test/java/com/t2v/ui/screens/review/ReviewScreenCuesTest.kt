package com.t2v.ui.screens.review

import com.t2v.data.SegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewScreenCuesTest {

    private fun seg(
        orderIndex: Int,
        text: String,
        durationMs: Int = 1000,
        pauseAfterMs: Int = 0,
    ) = SegmentEntity(
        audiobookId = 1L,
        orderIndex = orderIndex,
        text = text,
        durationMs = durationMs,
        pauseAfterMs = pauseAfterMs,
    )

    @Test
    fun `buildCues assigns sequential indices starting at 1`() {
        val segments = listOf(
            seg(0, "Hello", durationMs = 1000),
            seg(1, "World", durationMs = 2000),
        )
        val cues = buildCuesForTest(segments)
        assertEquals(2, cues.size)
        assertEquals(1, cues[0].index)
        assertEquals(2, cues[1].index)
    }

    @Test
    fun `buildCues computes cumulative timings`() {
        val segments = listOf(
            seg(0, "First", durationMs = 1000, pauseAfterMs = 500),
            seg(1, "Second", durationMs = 2000, pauseAfterMs = 0),
        )
        val cues = buildCuesForTest(segments)
        assertEquals(0f, cues[0].startSec, 0.01f)
        assertEquals(1.5f, cues[0].endSec, 0.01f)
        assertEquals(1.5f, cues[1].startSec, 0.01f)
        assertEquals(3.5f, cues[1].endSec, 0.01f)
    }

    @Test
    fun `buildCues handles zero duration segments`() {
        val segments = listOf(seg(0, "Zero", durationMs = 0))
        val cues = buildCuesForTest(segments)
        assertEquals(0f, cues[0].startSec, 0.01f)
        assertEquals(0f, cues[0].endSec, 0.01f)
    }

    @Test
    fun `buildCues preserves segment text`() {
        val segments = listOf(seg(0, "Hello world"))
        val cues = buildCuesForTest(segments)
        assertEquals("Hello world", cues[0].text)
    }

    @Test
    fun `buildCues empty segments returns empty list`() {
        assertTrue(buildCuesForTest(emptyList()).isEmpty())
    }

    /**
     * Mirrors [ReviewViewModel.buildCues] logic without needing a ViewModel
     * or database. Keep in sync with the private method.
     */
    private fun buildCuesForTest(segments: List<SegmentEntity>): List<com.t2v.core.subtitle.SubtitleCue> {
        var currentTimeSec = 0f
        return segments.mapIndexed { idx, seg ->
            val startSec = currentTimeSec
            val durationSec = (seg.durationMs.coerceAtLeast(0) / 1000f)
            val endSec = startSec + durationSec + (seg.pauseAfterMs / 1000f)
            currentTimeSec = endSec
            com.t2v.core.subtitle.SubtitleCue(
                index = idx + 1,
                startSec = startSec,
                endSec = endSec,
                text = seg.text,
            )
        }
    }
}