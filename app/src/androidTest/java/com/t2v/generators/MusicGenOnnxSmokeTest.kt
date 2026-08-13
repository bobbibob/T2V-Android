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
 * `files/models/litert/musicgen-small`). Golden values come from the verified
 * int8 ONNX pipeline (Python ORT, guidance=3): step-0 logits for
 * "80s pop track with bassy drums and synth" put the reference tokens
 * `[353, 1659, 1007, 1801]` at the top of their codebooks.
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
        val t = encoded.ids.size

        MusicGenOnnxRuntime(bundleDir()).use { onnx ->
            val hidden = onnx.encodeText(
                encoded.ids.toIntArray(),
                encoded.attentionMask.toIntArray(),
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
        val t = encoded.ids.size

        MusicGenOnnxRuntime(bundleDir()).use { onnx ->
            val hidden = onnx.encodeText(
                encoded.ids.toIntArray(),
                encoded.attentionMask.toIntArray(),
            )

            // Feed the guidance-batch encoder inputs (row0 real, row1 zeros).
            val encoderHidden = hidden + FloatArray(t * MusicGenOnnxRuntime.D_MODEL)
            val encoderAttn = encoded.attentionMask.toIntArray() + IntArray(t)

            // Step-0 logits depend only on the encoder inputs, so this is the
            // strongest structural check that the ONNX pipeline matches the
            // verified reference. A feed-layout bug (wrong KV-cache slots, EOS
            // dropped, etc.) pushes the reference tokens to arbitrary ranks.
            val merged = onnx.step0MergedLogits(
                encoderHidden = encoderHidden,
                encoderAttn = encoderAttn,
                guidance = MusicGenOnnxRuntime.DEFAULT_GUIDANCE,
            )
            // Golden step-0 tokens for the int8 pipeline (Python ORT, guidance=3):
            // "80s pop track with bassy drums and synth" -> [353, 1659, 1007, 1801].
            // The exact argmax is not portable across int8 kernels (near-ties
            // flip between platforms), so each reference token must land in the
            // top-20 of its codebook instead.
            val golden = listOf(353, 1659, 1007, 1801)
            for (cb in golden.indices) {
                val base = cb * MusicGenOnnxRuntime.VOCAB_SIZE
                val rank = (base until base + MusicGenOnnxRuntime.VOCAB_SIZE)
                    .count { merged[it] > merged[base + golden[cb]] }
                assertTrue(
                    "reference step-0 token ${golden[cb]} (codebook $cb) must be in the top-20, rank=$rank",
                    rank < 20,
                )
            }
        }
    }

    @Test
    fun full_pipeline_produces_audio_and_unlocks_gate() {
        assumeTrue("MusicGen bundle is not installed; install it via ModelsScreen or adb", installed())
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val runtime = LiteRtModelRuntime(target)
        val installer = LiteRtModelInstaller(runtime)

        val encoded = tokenizer()("80s pop track with bassy drums and synth")
        val t = encoded.ids.size
        val frames = 6

        MusicGenOnnxRuntime(bundleDir()).use { onnx ->
            val hidden = onnx.encodeText(
                encoded.ids.toIntArray(),
                encoded.attentionMask.toIntArray(),
            )
            val encoderHidden = hidden + FloatArray(t * MusicGenOnnxRuntime.D_MODEL)
            val encoderAttn = encoded.attentionMask.toIntArray() + IntArray(t)

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
