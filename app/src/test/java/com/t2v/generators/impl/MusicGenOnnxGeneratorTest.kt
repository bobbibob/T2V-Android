package com.t2v.generators.impl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Verifies the tokenizer wiring inside [MusicGenOnnxGenerator] using the same
 * golden vectors as [com.t2v.tokenizer.MusicGenTokenizerTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MusicGenOnnxGeneratorTest {

    private fun withGenerator(block: (MusicGenOnnxGenerator) -> Unit) {
        val root = File.createTempFile("musicgen", "").apply { delete(); mkdirs() }
        val modelDir = File(root, LiteRtModelRuntime.MUSIC_GEN_SMALL.modelId)
        modelDir.mkdirs()
        val json = checkNotNull(this::class.java.classLoader)
            .getResourceAsStream("tokenizer.json")!!
            .bufferedReader()
            .use { it.readText() }
        File(modelDir, "tokenizer.json").writeText(json)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = LiteRtModelRuntime(context, root)
        val installer = LiteRtModelInstaller(runtime)
        block(MusicGenOnnxGenerator(context, runtime, installer))
    }

    @Test
    fun `encodePrompt returns golden ids and tokens for pop prompt`() {
        withGenerator { generator ->
            val result = generator.encodePrompt("80s pop track with bassy drums and synth")

            assertEquals(
                listOf(2775, 7, 2783, 1463, 28, 7981, 63, 5253, 7, 11, 13353, 1),
                result.ids,
            )
            assertEquals(
                listOf(
                    "▁80", "s", "▁pop", "▁track", "▁with", "▁bass", "y", "▁drum",
                    "s", "▁and", "▁synth", "</s>",
                ),
                result.tokens,
            )
            assertEquals(result.ids.size, result.attentionMask.size)
        }
    }

    @Test
    fun `generate tokenizes the prompt before refusing`() {
        withGenerator { generator ->
            val request = GeneratorRequest(
                prompt = "rock guitar riff, energetic, 120 bpm",
                outputFile = File(File.createTempFile("musicgen", "").apply { delete() }.parentFile, "out.wav"),
                category = GeneratorCategory.Music,
            )

            var thrown: IllegalStateException? = null
            try {
                runBlocking { generator.generate(request) }
            } catch (e: IllegalStateException) {
                thrown = e
            }

            assertNotNull("generate must refuse while the pipeline is not smoke-tested", thrown)
            assertTrue(thrown!!.message.orEmpty().contains("tokens"))
            assertTrue(thrown.message.orEmpty().contains("smoke-tested"))
        }
    }
}
