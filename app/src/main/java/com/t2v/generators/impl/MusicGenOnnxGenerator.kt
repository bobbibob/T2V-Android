package com.t2v.generators.impl

import android.content.Context
import com.t2v.core.audio.AudioEncoder
import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import com.t2v.generators.runtime.LiteRtModelInstaller
import com.t2v.generators.runtime.LiteRtModelRuntime
import com.t2v.generators.runtime.MusicGenOnnxRuntime
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
        require(isAvailable()) {
            "MusicGen bundle is not installed and smoke-tested on this device"
        }
        val encoded = encodePrompt(request.prompt)
        val output = File(
            request.outputFile.parentFile ?: appContext.filesDir,
            "musicgen-${UUID.randomUUID()}.wav",
        )
        output.parentFile?.mkdirs()

        // requested duration in seconds ({{duration N}}) -> EnCodec frames (50 Hz).
        val seconds = request.durationSeconds.coerceIn(1, 30)
        val maxNewTokens = seconds * 50

        MusicGenOnnxRuntime(runtime.rootDirectory.resolve(LiteRtModelRuntime.MUSIC_GEN_SMALL.modelId)).use { onnx ->
            val encoderHidden = onnx.encodeText(
                encoded.ids.toIntArray(),
                encoded.attentionMask.toIntArray(),
            )
            val codes = onnx.generateAudioCodes(
                encoderHidden = encoderHidden,
                encoderAttn = encoded.attentionMask.toIntArray(),
                guidance = MusicGenOnnxRuntime.DEFAULT_GUIDANCE,
                maxNewTokens = maxNewTokens,
            )
            val audio = onnx.decodeAudio(codes, maxNewTokens)

            val pcm = ShortArray(audio.size)
            for (i in audio.indices) {
                val v = (audio[i] * 32767f).coerceIn(-32768f, 32767f)
                pcm[i] = v.toInt().toShort()
            }
            AudioEncoder.encodePcm16MonoWav(
                out = output,
                pcm = pcm,
                sampleRate = MusicGenOnnxRuntime.SAMPLE_RATE,
            )
        }

        GeneratorResult(
            outputFile = output,
            sampleRate = MusicGenOnnxRuntime.SAMPLE_RATE,
            channels = 1,
            durationMs = ((maxNewTokens * MusicGenOnnxRuntime.SAMPLES_PER_CODE_FRAME) * 1000L / MusicGenOnnxRuntime.SAMPLE_RATE).toInt(),
            bytesWritten = output.length(),
        )
    }
}