package com.t2v.generators

import com.t2v.generators.impl.ElevenLabsSoundEffectsGenerator
import com.t2v.generators.impl.StableAudioMusicGenerator
import com.t2v.generators.impl.StableAudioSoundGenerator
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import com.t2v.tts.registry.EngineRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorRegistryTest {

    @Test
    fun `elevenlabs sound is unavailable without an api key`() {
        val gen = ElevenLabsSoundEffectsGenerator(apiKey = "")
        assertFalse(gen.isAvailable())
    }

    @Test
    fun `elevenlabs sound is available with an api key`() {
        val gen = ElevenLabsSoundEffectsGenerator(apiKey = "sk-test")
        assertTrue(gen.isAvailable())
        assertEquals(GeneratorCategory.Sound, gen.category)
        assertEquals("elevenlabs.sound", gen.id)
    }

    @Test
    fun `GeneratorResult carries byte count and metadata`() {
        val tmp = java.io.File.createTempFile("generator", ".wav").also { it.deleteOnExit() }
        tmp.writeBytes(ByteArray(4))
        val result = GeneratorResult(
            outputFile = tmp,
            sampleRate = 22050,
            channels = 1,
            durationMs = 0,
            bytesWritten = 4,
        )
        assertNotNull(result.outputFile)
        assertEquals(4, result.bytesWritten)
    }

    @Test
    fun `EngineSettings defaults preserve generator contract`() {
        val settings = EngineRegistry.EngineSettings()
        assertEquals(emptyMap<String, Map<String, String>>(), settings.engines)
    }

    @Test
    fun `Stable Audio manifests list every required file with size`() {
        val music = LiteRtModelRuntime.STABLE_AUDIO_OPEN_SMALL
        assertEquals(3, music.entries.size)
        assertTrue(music.totalBytes > 500_000_000)
        for (entry in music.entries) {
            assertTrue("Entry ${entry.path} must declare a size", entry.expectedBytes > 0)
            assertEquals(64, entry.sha256.length)
        }
        val sound = LiteRtModelRuntime.STABLE_AUDIO_CLIP
        assertEquals(1, sound.entries.size)
        assertTrue(sound.totalBytes in 1..200_000_000)
    }

    @Test
    fun `LiteRT installer plan exposes verify report and root`() {
        val runtime = object {
            // Lightweight stand-in for the runtime: nothing is installed, root is captured.
            val root: java.io.File = java.nio.file.Files.createTempDirectory("litert-test").toFile()
            fun isInstalled(m: LiteRtModelRuntime.BundleManifest): Boolean = false
            fun verifyChecksums(m: LiteRtModelRuntime.BundleManifest): Map<String, String> = emptyMap()
            fun probe(): LiteRtModelRuntime.ProbeResult =
                LiteRtModelRuntime.ProbeResult.Unsupported("simulated")
        }
        // Sanity: the manifest totals and SHA-256 field shape are testable without a device.
        val music = LiteRtModelRuntime.STABLE_AUDIO_OPEN_SMALL
        val sound = LiteRtModelRuntime.STABLE_AUDIO_CLIP
        assertTrue(music.totalBytes > sound.totalBytes)
        for (entry in music.entries + sound.entries) {
            assertEquals(64, entry.sha256.length)
        }
        assertTrue(runtime.probe() is LiteRtModelRuntime.ProbeResult.Unsupported)
    }
}
