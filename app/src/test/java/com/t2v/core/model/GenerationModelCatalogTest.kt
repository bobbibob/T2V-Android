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
    fun `musicgen catalog entry is registered, verified and documented`() {
        val musicGen = GenerationModelCatalog.entries.single { it.id == "musicgen-small" }
        assertEquals(GenerationModelCatalog.Support.Verified, musicGen.support)
        assertEquals(true, musicGen.canInstall)
        assertEquals(
            setOf(GenerationModelCatalog.Category.Music),
            musicGen.categories,
        )
        assertEquals(
            GenerationModelCatalog.Runtime.LiteRt,
            musicGen.requirements.runtime,
        )
        assertNotNull("MusicGen needs TagDocs", musicGen.tags)
        assertNotNull(
            "MusicGen must expose its known repository",
            GenerationModelCatalog.repositoryFor("musicgen-small"),
        )
        assertNotNull(
            "Generator id must map to MusicGen TagDocs",
            GenerationModelCatalog.tagDocsForGenerator("musicgen-small"),
        )
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
    fun `localVoiceModelEntries only returns local HF-backed voice models`() {
        val voice = GenerationModelCatalog.localVoiceModelEntries()
        // Kokoro is a real HF repo; piper/openai-music/eleven-sfx and the LiteRT
        // generators are intentionally excluded.
        assertTrue(
            voice.map { it.id }.contains("kokoro-82m"),
        )
        assertTrue(
            "No voice entry may be a bundle with no download",
            voice.all { GenerationModelCatalog.isHuggingFaceRepository(it.id) },
        )
        assertTrue(
            "Procedural music is not a local voice entry",
            voice.none { it.id == "stable-audio-open-small" },
        )
    }

    @Test
    fun `HuggingFace Piper voices are installable and carry a language`() {
        val ids = listOf(
            "vits-piper-uk-ua",
            "vits-piper-ca-es",
            "vits-piper-cs-cz",
            "vits-piper-da-dk",
            "vits-piper-el-gr",
            "vits-piper-fa-ir",
            "vits-piper-fi-fi",
            "vits-piper-hu-hu",
            "vits-piper-nl-nl",
            "vits-piper-pt-br",
            "vits-piper-ro-ro",
            "vits-piper-tr-tr",
        )
        for (id in ids) {
            val entry = GenerationModelCatalog.entries.single { it.id == id }
            assertTrue("$id must be installable", entry.canInstall)
            assertTrue("$id must be a real HF repository", GenerationModelCatalog.isHuggingFaceRepository(id))
            assertTrue("$id must download all files", entry.downloadAllFiles)
            assertTrue("$id must declare a language", entry.language.isNotBlank())
            assertEquals(GenerationModelCatalog.Category.Voice, entry.categories.single())
            assertEquals(
                GenerationModelCatalog.Runtime.SherpaOnnx,
                entry.requirements.runtime,
            )
        }
    }

    @Test
    fun `voice filter languages include catalog Piper voices`() {
        val languages = GenerationModelCatalog.localVoiceModelEntries()
            .map { it.language }
            .filter { it.isNotBlank() }
        assertTrue("New HF Piper languages must be present", languages.contains("uk-UA"))
        assertTrue(languages.contains("tr-TR"))
        assertTrue(languages.contains("pt-BR"))
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
