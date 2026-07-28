package com.t2v.ui.screens.models

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadProgressTextTest {
    @Test
    fun `shows percentage and downloaded bytes`() {
        assertEquals(
            "50% • 184.7 MB / 369.3 MB",
            downloadProgressText(184_657_741L, 369_315_482L),
        )
    }

    @Test
    fun `clamps percentage when server sends extra bytes`() {
        assertEquals(
            "100% • 2.0 MB / 1.0 MB",
            downloadProgressText(2_000_000L, 1_000_000L),
        )
    }

    @Test
    fun `shows downloaded bytes when total is unavailable`() {
        assertEquals("850.0 KB", downloadProgressText(850_000L, -1L))
    }
}
