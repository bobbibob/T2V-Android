package com.t2v.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationModelCatalogTest {
    @Test
    fun `nsynth stays in development until the device smoke-test runs`() {
        val nsynth = GenerationModelCatalog.entries.single { it.id == "nsynth-wavenet" }
        assertEquals(GenerationModelCatalog.Support.RuntimeInDevelopment, nsynth.support)
        assertEquals(false, nsynth.canInstall)
    }

    @Test
    fun `catalog ids are unique and verified entries have a size`() {
        val entries = GenerationModelCatalog.entries

        assertEquals(entries.size, entries.map { it.id }.distinct().size)
        assertTrue(
            entries.filter { it.canInstall }.all {
                val size = it.approximateDownloadBytes
                size != null && size > 0
            },
        )
    }

    @Test
    fun `TagDocs are exposed for every documented model and engine`() {
        // Catalog models that ship a TagDocs block.
        val expected = listOf(
            "kokoro-82m",
            "piper-vits",
            "pocket-tts-int8",
            "zipvoice-distill-int8",
            "stable-audio-open-small",
            "stable-audio-clip",
        )
        for (id in expected) {
            val docs = GenerationModelCatalog.tagDocsFor(id)
            assertNotNull("Missing TagDocs for catalog model $id", docs)
            assertTrue(
                "TagDocs for $id must mention at least one supported tag",
                docs!!.supported.isNotEmpty(),
            )
        }

        // Cloud TTS engines.
        for (engine in listOf("openai", "elevenlabs", "gemini", "azure", "custom_http", "kokoro", "piper_ru")) {
            assertNotNull(
                "Missing TagDocs for engine $engine",
                GenerationModelCatalog.tagDocsForEngine(engine),
            )
        }

        // Generators without on-device model id.
        for (gen in listOf("elevenlabs.sound", "litert.stable-audio-open-small.music", "litert.stable-audio-clip.sound", "nsynth-wavenet")) {
            assertNotNull(
                "Missing TagDocs for generator $gen",
                GenerationModelCatalog.tagDocsForGenerator(gen),
            )
        }
    }

    @Test
    fun `piper catalog entry exposes the verified voices`() {
        val piper = GenerationModelCatalog.entries.single { it.id == "piper-vits" }
        assertEquals(GenerationModelCatalog.Support.Verified, piper.support)
        assertNotNull("Piper needs TagDocs", piper.tags)
    }

    @Test
    fun `cloud-only models stay marked as in-development`() {
        val openaiMusic = GenerationModelCatalog.entries.single { it.id == "openai-music" }
        val elevenSound = GenerationModelCatalog.entries.single { it.id == "elevenlabs-sound-clip" }
        assertEquals(GenerationModelCatalog.Support.RuntimeInDevelopment, openaiMusic.support)
        assertEquals(GenerationModelCatalog.Support.RuntimeInDevelopment, elevenSound.support)
        assertEquals(false, openaiMusic.canInstall)
        assertEquals(false, elevenSound.canInstall)
    }

    @Test
    fun `stable audio shares LiteRT runtime and is verified for install`() {
        val music = GenerationModelCatalog.forCategory(
            GenerationModelCatalog.Category.Music,
        ).single { it.id == "stable-audio-open-small" }
        val sound = GenerationModelCatalog.forCategory(
            GenerationModelCatalog.Category.Sound,
        ).single { it.id == "stable-audio-open-small" }

        assertEquals(music.id, sound.id)
        assertEquals(
            GenerationModelCatalog.Runtime.LiteRt,
            GenerationModelCatalog.requiredRuntime(music.id),
        )
        assertEquals(GenerationModelCatalog.Support.Verified, music.support)
        // The clip variant stays exclusive to Sound.
        val clips = GenerationModelCatalog.forCategory(
            GenerationModelCatalog.Category.Sound,
        ).filter { it.id == "stable-audio-clip" }
        assertEquals(1, clips.size)
        assertEquals(GenerationModelCatalog.Support.Verified, clips.single().support)
    }
}
