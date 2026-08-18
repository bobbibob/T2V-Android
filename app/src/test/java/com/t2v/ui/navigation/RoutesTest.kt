package com.t2v.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    @Test
    fun `route builders embed ids correctly`() {
        assertEquals("generation/42", Routes.generation(42L))
        assertEquals("generation/0", Routes.generation(0L))
        assertEquals("review/99", Routes.review(99L))
        assertEquals("music/7", Routes.musicMix(7L))
        assertEquals("audio-editor/3", Routes.audioEditor(3L))
    }

    @Test
    fun `base routes are stable strings`() {
        assertEquals("editor", Routes.Editor)
        assertEquals("projects", Routes.Projects)
        assertEquals("voices", Routes.Voices)
        assertEquals("settings", Routes.Settings)
        assertEquals("models", Routes.Models)
        assertEquals("onboarding", Routes.Onboarding)
    }

    @Test
    fun `parameterised routes contain placeholder`() {
        assertEquals(true, Routes.Generation.contains("{projectId}"))
        assertEquals(true, Routes.Review.contains("{audiobookId}"))
        assertEquals(true, Routes.MusicMix.contains("{audiobookId}"))
        assertEquals(true, Routes.AudioEditor.contains("{audiobookId}"))
    }
}