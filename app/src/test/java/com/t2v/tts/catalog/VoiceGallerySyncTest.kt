package com.t2v.tts.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGallerySyncTest {

    private val sync = VoiceGallerySync(
        catalogUrl = "http://localhost.invalid/catalog.json",
        cacheDir = java.io.File(System.getProperty("java.io.tmpdir"), "t2v-voice-gallery-test"),
    )

    @Test
    fun `parses a well-formed catalog array`() {
        val json = """
            [
              {
                "id": "af_sarah",
                "name": "Sarah",
                "engine": "kokoro",
                "language": "en-US",
                "gender": "female",
                "previewUrl": "https://example.com/sarah.mp3",
                "downloadModelId": "kokoro-82m",
                "tags": ["warm", "narration"],
                "isCloned": false
              },
              {
                "id": "ru_RU-irina-medium",
                "name": "Irina",
                "engine": "piper_ru",
                "language": "ru-RU",
                "gender": "female"
              }
            ]
        """.trimIndent()
        val entries = sync.parseCatalog(json)
        assertEquals(2, entries.size)

        val sarah = entries[0]
        assertEquals("af_sarah", sarah.voice.id)
        assertEquals("Sarah", sarah.voice.displayName)
        assertEquals("kokoro", sarah.voice.engineId)
        assertEquals("en-US", sarah.voice.language)
        assertEquals("female", sarah.voice.gender)
        assertEquals("https://example.com/sarah.mp3", sarah.voice.previewUrl)
        assertEquals("kokoro-82m", sarah.downloadModelId)
        assertEquals(listOf("warm", "narration"), sarah.voice.tags)
        assertEquals(false, sarah.voice.isCloned)

        val irina = entries[1]
        assertEquals("ru_RU-irina-medium", irina.voice.id)
        assertEquals("Irina", irina.voice.displayName)
        assertEquals("piper_ru", irina.voice.engineId)
        assertNull(irina.downloadModelId)
        assertTrue(irina.voice.tags.isEmpty())
    }

    @Test
    fun `empty array returns empty list`() {
        assertEquals(emptyList<VoiceGallerySync.GalleryEntry>(), sync.parseCatalog("[]"))
    }

    @Test
    fun `invalid JSON returns empty list`() {
        assertTrue(sync.parseCatalog("not json").isEmpty())
    }

    @Test
    fun `entry missing id is skipped`() {
        val json = """[{"name":"No ID","engine":"kokoro"}]"""
        assertTrue(sync.parseCatalog(json).isEmpty())
    }

    @Test
    fun `entry missing engine is skipped`() {
        val json = """[{"id":"voice1","name":"Voice 1"}]"""
        assertTrue(sync.parseCatalog(json).isEmpty())
    }

    @Test
    fun `id falls back to displayName when name is blank`() {
        val json = """[{"id":"voice1","engine":"kokoro","name":""}]"""
        val entries = sync.parseCatalog(json)
        assertEquals(1, entries.size)
        assertEquals("voice1", entries[0].voice.displayName)
    }

    @Test
    fun `cloned flag is parsed correctly`() {
        val json = """[{"id":"clone1","engine":"elevenlabs","isCloned":true}]"""
        val entries = sync.parseCatalog(json)
        assertEquals(1, entries.size)
        assertTrue(entries[0].voice.isCloned)
    }

    @Test
    fun `default catalog URL points to bobbibob repo`() {
        assertTrue(VoiceGallerySync.DEFAULT_CATALOG_URL.contains("bobbibob/T2V-VoiceGallery"))
    }
}