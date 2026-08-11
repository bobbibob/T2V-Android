package com.t2v.generators.impl

import android.content.Context
import com.t2v.core.audio.AudioEncoder
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import com.t2v.tokenizer.MusicGenTokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * On-device AI music generator backed by a MusicGen-small ONNX export
 * (executed through ONNX Runtime; see [LiteRtModelRuntime.MUSIC_GEN_SMALL]).
 *
 * MusicGen is an autoregressive audio-language model: a text encoder maps the
 * prompt to embeddings, a transformer LM autoregressively predicts EnCodec
 * code tokens, and the audio decoder turns those tokens into a waveform.
 *
 * **Not selectable yet.** It follows the exact gate used by
 * [NSynthSoundGenerator]: it reports [isAvailable] = false until all three of
 * these are satisfied:
 *   - the LiteRT runtime probe reports the device as Ready, AND
 *   - the MusicGen bundle is installed with matching sizes/hashes, AND
 *   - a device ARM64 smoke-test has run once (via
 *     [LiteRtModelInstaller.markSmokeTested]).
 *
 * Until a real verified export with concrete tensor names/shapes is available
 * on the phone, that smoke-test has not happened and [generate] refuses rather
 * than emit silent audio.
 */
class MusicGenOnnxGenerator(
    private val appContext: Context,
    private val runtime: LiteRtModelRuntime = LiteRtModelRuntime(appContext),
    private val installer: LiteRtModelInstaller = LiteRtModelInstaller(runtime),
) : Generator {

    override val id: String = "musicgen-small"
    override val displayName: String = "MusicGen (on-device music)"
    override val category: GeneratorCategory = GeneratorCategory.Music

    override fun isAvailable(): Boolean =
        installer.isSmokeTestedInstalled(LiteRtModelRuntime.MUSIC_GEN_SMALL) &&
            runtime.isInstalled(LiteRtModelRuntime.MUSIC_GEN_SMALL)

    fun plan(): LiteRtModelInstaller.Plan =
        installer.plan(
            manifest = LiteRtModelRuntime.MUSIC_GEN_SMALL,
            catalog = requireNotNull(LiteRtModelRuntime.catalogEntryFor("musicgen-small")),
        )

    /** Path of the installed MusicGen T5 `tokenizer.json` inside the bundle. */
    private fun tokenizerFile(): File =
        File(
            runtime.rootDirectory,
            "${LiteRtModelRuntime.MUSIC_GEN_SMALL.modelId}/tokenizer.json",
        )

    /**
     * Loads the MusicGen T5 Unigram tokenizer from the installed bundle.
     *
     * The tokenizer.json is part of [LiteRtModelRuntime.MUSIC_GEN_SMALL] and its
     * SHA-256 is verified at install time, so reading it here is safe once the
     * bundle is installed.
     */
    private fun tokenizer(): MusicGenTokenizer =
        MusicGenTokenizer.fromJson(tokenizerFile().readText())

    /**
     * Encodes [prompt] into ids, tokens and attention mask.
     *
     * Kept separate from [generate] so the tokenizer can be exercised on-device
     * before the ONNX export is smoke-tested: it needs only the tokenizer.json,
     * not the three ONNX weights.
     */
    fun encodePrompt(prompt: String): MusicGenTokenizer.EncodeResult =
        tokenizer()(prompt)

    override suspend fun generate(request: GeneratorRequest): GeneratorResult = withContext(Dispatchers.IO) {
        // Tokenize the prompt up front. This exercises the tokenizer path even
        // while the ONNX pipeline is still unverified, and lets a device-side
        // check confirm the ids before the first real inference run.
        val encoded = encodePrompt(request.prompt)
        // The pipeline drives each generator. Until the ARM64 smoke-test lands
        // we must not claim to have produced audio; refuse the call so
        // AudioTagInserter skips this tag (matching NSynth behaviour).
        throw IllegalStateException(
            "MusicGen is still in development: LiteRT export not smoke-tested on a device yet" +
                " (prompt encoded to ${encoded.ids.size} tokens, first=${encoded.ids.firstOrNull()})",
        )
        @Suppress("UNREACHABLE_CODE")
        val output = File(
            request.outputFile.parentFile ?: appContext.filesDir,
            "musicgen-${UUID.randomUUID()}.wav",
        )
        output.parentFile?.mkdirs()
        // Real inference once tensors are verified:
        //  1. onnx/text_encoder_int8.onnx: prompt -> (1, seq, 768) text embeddings.
        //  2. onnx/decoder_model_merged_int8.onnx: autoregressive Sampling of
        //     EnCodec tokens (32 steps) with cross-attention over the embeddings.
        //  3. onnx/encodec_decode_int8.onnx: tokens -> (1, 1, 32000) PCM per 1sec.
        AudioEncoder.encodePcm16MonoWav(
            out = output,
            pcm = ShortArray(0),
            sampleRate = 32000,
        )
        GeneratorResult(
            outputFile = output,
            sampleRate = 32000,
            channels = 1,
            durationMs = 0,
            bytesWritten = output.length(),
        )
    }
}