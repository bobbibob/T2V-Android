package com.t2v.generators

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import com.t2v.generators.runtime.MusicGenOnnxRuntime
import com.t2v.tokenizer.MusicGenTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device ARM64 smoke-test for the MusicGen-small ONNX pipeline.
 *
 * Unlocks the [com.t2v.generators.impl.MusicGenOnnxGenerator] gate: the test
 * runs text_encoder -> decoder (autoregressive, guidance=3) -> EnCodec on a
 * real device and records the smoke-test marker via
 * [LiteRtModelInstaller.markSmokeTested] on success.
 *
 * The bundle must be installed first (ModelsScreen download or adb push into
 * `files/models/litert/musicgen-small`). Golden values come from the reference
 * Transformers.js pipeline (see docs/ROADMAP): step-0 CFG-merged argmax for
 * "80s pop track with bassy drums and synth" is `[353, 1659, 1007, 1801]`.
 */
@RunWith(AndroidJUnit4::class)
class MusicGenOnnxSmokeTest {

    private val manifest = LiteRtModelRuntime.MUSIC_GEN_SMALL

    private fun bundleDir(): File {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        return File(target.filesDir, "models/litert/${manifest.modelId}")
    }

    private fun installed(): Boolean {
        val root = bundleDir()
        return manifest.entries.all { entry ->
            File(root, entry.path).isFile &&
                File(root, entry.path).length() == entry.expectedBytes
        }
    }

    private fun tokenizer(): MusicGenTokenizer {
        val json = File(bundleDir(), "tokenizer.json").readText()
        return MusicGenTokenizer.fromJson(json)
    }

    @Test
    fun text_encoder_runs_on_arm64() {
        assumeTrue("MusicGen bundle is not installed; install it via ModelsScreen or adb", installed())
        val encoded = tokenizer()("80s pop track with bassy drums and synth")
        val t = encoded.ids.size - 1 // drop EOS sentinel from the ids fed to the model

        MusicGenOnnxRuntime(bundleDir()).use { onnx ->
            val hidden = onnx.encodeText(
                encoded.ids.take(t).toIntArray(),
                encoded.attentionMask.take(t).toIntArray(),
            )
            assertEquals("expected (1, T, 768) embeddings", t * 768, hidden.size)
            assertTrue("embeddings must be finite", hidden.all { it.isFinite() })
            val rms = Math.sqrt(hidden.sumOf { (it.toDouble() * it).toDouble() } / hidden.size)
            assertTrue("embeddings must not be silent, rms=$rms", rms > 0.01)
        }
    }

    @Test
    fun decoder_step0_argmax_matches_reference() {
        assumeTrue("MusicGen bundle is not installed; install it via ModelsScreen or adb", installed())
        val encoded = tokenizer()("80s pop track with bassy drums and synth")
        val t = encoded.ids.size - 1

        MusicGenOnnxRuntime(bundleDir()).use { onnx ->
            val hidden = onnx.encodeText(
                encoded.ids.take(t).toIntArray(),
                encoded.attentionMask.take(t).toIntArray(),
            )
            val attn = encoded.attentionMask.take(t).toIntArray()

            // Feed the guidance-batch encoder inputs (row0 real, row1 zeros).
            val encoderHidden = hidden + FloatArray(t * MusicGenOnnxRuntime.D_MODEL)
            val encoderAttn = attn + IntArray(t)

            // A single generation step (useCache=false, all-pad column).
            val codes = onnx.generateAudioCodes(
                encoderHidden = encoderHidden,
                encoderAttn = encoderAttn,
                guidance = MusicGenOnnxRuntime.DEFAULT_GUIDANCE,
                maxNewTokens = 4,
            )
            val step0 = IntArray(4) { cb -> codes[cb * 4] }
            assertEquals(
                "step-0 CFG-merged argmax must match the reference pipeline",
                listOf(353, 1659, 1007, 1801),
                step0.toList(),
            )
        }
    }

    @Test
    fun full_pipeline_produces_audio_and_unlocks_gate() {
        assumeTrue("MusicGen bundle is not installed; install it via ModelsScreen or adb", installed())
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = LiteRtModelRuntime(target)
        val installer = LiteRtModelInstaller(runtime)

        val encoded = tokenizer()("80s pop track with bassy drums and synth")
        val t = encoded.ids.size - 1
        val frames = 6

        MusicGenOnnxRuntime(bundleDir()).use { onnx ->
            val hidden = onnx.encodeText(
                encoded.ids.take(t).toIntArray(),
                encoded.attentionMask.take(t).toIntArray(),
            )
            val encoderHidden = hidden + FloatArray(t * MusicGenOnnxRuntime.D_MODEL)
            val encoderAttn = encoded.attentionMask.take(t).toIntArray() + IntArray(t)

            val codes = onnx.generateAudioCodes(
                encoderHidden = encoderHidden,
                encoderAttn = encoderAttn,
                guidance = MusicGenOnnxRuntime.DEFAULT_GUIDANCE,
                maxNewTokens = frames,
            )
            val emitted = codes.take(4 * frames)
            assertTrue("generated codes must stay in the 2048-vocab", emitted.all { it in 0 until 2048 })

            val audio = onnx.decodeAudio(codes, frames)
            assertEquals("6 EnCodec frames must decode to 3840 PCM samples", frames * 640, audio.size)
            assertTrue("audio must be finite", audio.all { it.isFinite() })
            val rms = Math.sqrt(audio.sumOf { (it.toDouble() * it).toDouble() } / audio.size)
            assertTrue("audio must not be silent, rms=$rms", rms > 0.01)
        }

        val plan = installer.plan(
            manifest = manifest,
            catalog = requireNotNull(LiteRtModelRuntime.catalogEntryFor(manifest.modelId)),
        )
        installer.markSmokeTested(plan)
        assertTrue(
            "smoke-test marker must be recorded",
            installer.isSmokeTestedInstalled(manifest),
        )
    }
}
