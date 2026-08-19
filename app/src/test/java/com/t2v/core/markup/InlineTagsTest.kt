package com.t2v.core.markup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineTagsTest {

    private val parser = LTVMarkupParser()

    @Test
    fun `whisper tag applies delivery to enclosed text only`() {
        val input = "Привет! <whisper>Сергей, я устала</whisper> Пойдём."
        val markup = parser.parse(input)
        val commands = markup.commands

        // Should produce: delivery whisper, reset delivery
        val deliveries = commands.filterIsInstance<MarkupCommand.Delivery>()
        assertEquals(1, deliveries.size)
        assertEquals("whisper", deliveries[0].value)

        val resets = commands.filterIsInstance<MarkupCommand.Reset>()
        assertTrue("Should have a reset for delivery", resets.any { it.target == "delivery" })
    }

    @Test
    fun `emotion tag with argument`() {
        val input = "<emotion sad>Я так устала</emotion>"
        val markup = parser.parse(input)
        val emotions = markup.commands.filterIsInstance<MarkupCommand.Emotion>()
        assertEquals(1, emotions.size)
        assertEquals("sad", emotions[0].value)
    }

    @Test
    fun `short emotion alias works`() {
        val input = "<happy>Ура!</happy>"
        val markup = parser.parse(input)
        val emotions = markup.commands.filterIsInstance<MarkupCommand.Emotion>()
        assertEquals(1, emotions.size)
        assertEquals("happy", emotions[0].value)
    }

    @Test
    fun `fast and slow speed aliases`() {
        val input = "<fast>быстро</fast> <slow>медленно</slow>"
        val markup = parser.parse(input)
        val speeds = markup.commands.filterIsInstance<MarkupCommand.Speed>()
        assertEquals(2, speeds.size)
        assertTrue(speeds[0].value > 1.0) // fast
        assertTrue(speeds[1].value < 1.0) // slow
    }

    @Test
    fun `nested tags work`() {
        val input = "<whisper><emotion sad>я устала</emotion></whisper>"
        val markup = parser.parse(input)
        val deliveries = markup.commands.filterIsInstance<MarkupCommand.Delivery>()
        val emotions = markup.commands.filterIsInstance<MarkupCommand.Emotion>()
        assertEquals(1, deliveries.size)
        assertEquals("whisper", deliveries[0].value)
        assertEquals(1, emotions.size)
        assertEquals("sad", emotions[0].value)

        // Resets should close in reverse order: emotion first, then delivery
        val resets = markup.commands.filterIsInstance<MarkupCommand.Reset>()
        assertTrue("emotion reset before delivery reset", resets.indexOfFirst { it.target == "emotion" } < resets.indexOfFirst { it.target == "delivery" })
    }

    @Test
    fun `self-closing breath tag`() {
        val input = "Hello <breath/> world"
        val markup = parser.parse(input)
        val cues = markup.commands.filterIsInstance<MarkupCommand.VocalCue>()
        assertEquals(1, cues.size)
        assertEquals("breath", cues[0].cue)
    }

    @Test
    fun `unclosed tag auto-closes at end`() {
        val input = "Привет <whisper>я устала"
        val markup = parser.parse(input)
        val deliveries = markup.commands.filterIsInstance<MarkupCommand.Delivery>()
        assertEquals(1, deliveries.size)
        // Should auto-close
        val resets = markup.commands.filterIsInstance<MarkupCommand.Reset>()
        assertTrue("unclosed tag should auto-close", resets.any { it.target == "delivery" })
    }

    @Test
    fun `music and sfx tags still work alongside inline tags`() {
        val input = "<whisper>тихо</whisper> <music>ambient pad</music> <sfx>door</sfx>"
        val audioTags = parser.extractAudioTags(input)
        assertEquals(2, audioTags.size)
        assertEquals(AudioTag.Category.Music, audioTags[0].category)
        assertEquals(AudioTag.Category.Sound, audioTags[1].category)
    }

    @Test
    fun `plain text without tags is unchanged`() {
        val input = "Просто обычный текст без тегов."
        val markup = parser.parse(input)
        assertTrue(markup.commands.isEmpty())
        assertEquals(input, markup.plainText)
    }

    @Test
    fun `emphasis tag works`() {
        val input = "<emphasis strong>Важно!</emphasis>"
        val markup = parser.parse(input)
        val emphasis = markup.commands.filterIsInstance<MarkupCommand.Emphasis>()
        assertEquals(1, emphasis.size)
        assertEquals("strong", emphasis[0].value)
    }

    @Test
    fun `loud and soft volume aliases`() {
        val input = "<quiet>тихо</quiet>"
        val markup = parser.parse(input)
        val volumes = markup.commands.filterIsInstance<MarkupCommand.Volume>()
        assertEquals(1, volumes.size)
        assertTrue(volumes[0].value < 1.0) // quiet
    }

    @Test
    fun `multiple inline tags in sequence`() {
        val input = "<whisper>шёпот</whisper> нормальный <shout>крик</shout>"
        val markup = parser.parse(input)
        val deliveries = markup.commands.filterIsInstance<MarkupCommand.Delivery>()
        assertEquals(2, deliveries.size)
        assertEquals("whisper", deliveries[0].value)
        assertEquals("shout", deliveries[1].value)
    }
}