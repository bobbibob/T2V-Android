package com.t2v.generators.runtime

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * ONNX Runtime executor for the MusicGen-small pipeline.
 *
 * Runs the three verified ONNX exports from the `musicgen-small` LiteRT bundle
 * (see [LiteRtModelRuntime.MUSIC_GEN_SMALL]):
 *
 *   1. `onnx/text_encoder_int8.onnx` — prompt tokens -> (1, T, 768) embeddings.
 *   2. `onnx/decoder_model_merged_int8.onnx` — autoregressive EnCodec-token LM
 *      with cross-attention, guidance (batch doubling) and a built-in KV cache.
 *   3. `onnx/encodec_decode_int8.onnx` — EnCodec codes -> PCM waveform frames.
 *
 * The feed layout was verified against the reference Transformers.js pipeline
 * (see docs/ROADMAP): argmax of the CFG-merged logits matches the reference for
 * the first generation steps and EnCodec reproduces the reference waveform.
 *
 * **Tensor-feed contract (validated empirically):**
 *  - Step 0 feeds `use_cache_branch = [false]` with all pasts zero-filled.
 *  - Steps 1+ feed `use_cache_branch = [true]`, the decoder past from the
 *    previous step AND the encoder past captured at step 0. The merged export
 *    returns an empty (0-batch) encoder past on later steps, so the step-0
 *    encoder past must be pinned and reused.
 *  - `encoder_hidden_states` / `encoder_attention_mask` carry both branches:
 *    row 0 = conditional (real) embeddings, row 1 = unconditional (zeros),
 *    because guidance is baked into the export as a doubled batch.
 */
class MusicGenOnnxRuntime(
    /** Directory containing the onnx sub-folder (e.g. `files/models/litert/musicgen-small`). */
    bundleRoot: java.io.File,
) : Closeable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val textEncoder: OrtSession
    private val decoder: OrtSession
    private val encodec: OrtSession

    init {
        val onnxDir = java.io.File(bundleRoot, "onnx")
        val options = OrtSession.SessionOptions().apply {
            addCPU(true)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        textEncoder = env.createSession(java.io.File(onnxDir, TEXT_ENCODER_FILE).absolutePath, options)
        decoder = env.createSession(java.io.File(onnxDir, DECODER_FILE).absolutePath, options)
        encodec = env.createSession(java.io.File(onnxDir, ENCODEC_FILE).absolutePath, options)
    }

    /** Encodes [inputIds] (+ its [attentionMask]) into the (1, T, 768) embedding matrix. */
    fun encodeText(inputIds: IntArray, attentionMask: IntArray): FloatArray {
        val t = inputIds.size
        val output = FloatArray(t * D_MODEL)
        textEncoder.run(
            mapOf(
                "input_ids" to tensor(env, inputIds.map { it.toLong() }.toLongArray(), longArrayOf(1, t.toLong())),
                "attention_mask" to tensor(env, attentionMask.map { it.toLong() }.toLongArray(), longArrayOf(1, t.toLong())),
            ),
        ).use { result ->
            val values = (result.get("last_hidden_state").get() as OnnxTensor).floatBuffer
            values.get(output)
        }
        return output
    }

    /**
     * Autoregressively generates [maxNewTokens] EnCodec tokens per codebook.
     *
     * Returns a flat `4 * maxNewTokens` array where row [codebook] occupies
     * `[codebook * maxNewTokens, (codebook + 1) * maxNewTokens)`.
     */
    fun generateAudioCodes(
        encoderHidden: FloatArray,
        encoderAttn: IntArray,
        guidance: Float = DEFAULT_GUIDANCE,
        maxNewTokens: Int,
    ): IntArray {
        val t = encoderAttn.size / 2
        val codes = IntArray(4 * maxNewTokens) { PAD_TOKEN }
        var decoderPast: Past? = null
        var encoderPast: Past? = null

        for (step in 0 until maxNewTokens + 3) {
            val col = LongArray(8) { PAD_TOKEN.toLong() }
            for (cb in 0 until 4) {
                val j = step - 1 - cb
                if (j in 0 until maxNewTokens) {
                    col[cb] = codes[cb * maxNewTokens + j].toLong()
                }
            }
            col[4] = col[0]; col[5] = col[1]; col[6] = col[2]; col[7] = col[3]

            val (logits, newPast) = decoderStep(
                col = col,
                encoderHidden = encoderHidden,
                encoderAttn = encoderAttn,
                pastDecoder = decoderPast,
                pastEncoder = encoderPast,
                useCache = step > 0,
            )
            if (decoderPast == null) encoderPast = newPast
            decoderPast = newPast

            // CFG merge: lg[4+c] + (lg[c] - lg[4+c]) * guidance, then argmax per codebook.
            for (cb in 0 until 4) {
                val base = cb * VOCAB_SIZE
                val uncond = cb * VOCAB_SIZE + 4 * VOCAB_SIZE
                var best = 0
                var bestScore = Float.NEGATIVE_INFINITY
                for (v in 0 until VOCAB_SIZE) {
                    val score = logits[uncond + v] + (logits[base + v] - logits[uncond + v]) * guidance
                    if (score > bestScore) {
                        bestScore = score
                        best = v
                    }
                }
                val j = step - cb
                if (j in 0 until maxNewTokens) {
                    codes[cb * maxNewTokens + j] = best
                }
            }
        }
        return codes
    }

    /** Decodes [numFrames] code frames (rows 0..3 of [codes]) into `numFrames * 640` PCM samples. */
    fun decodeAudio(codes: IntArray, numFrames: Int): FloatArray {
        val audio = FloatArray(numFrames * SAMPLES_PER_CODE_FRAME)
        encodec.run(
            mapOf(
                "audio_codes" to tensor(env, codes.take(4 * numFrames).map { it.toLong() }.toLongArray(), longArrayOf(1, 1, 4, numFrames.toLong())),
            ),
        ).use { result ->
            val values = (result.get("audio_values").get() as OnnxTensor).floatBuffer
            values.get(audio)
        }
        return audio
    }

    private fun decoderStep(
        col: LongArray,
        encoderHidden: FloatArray,
        encoderAttn: IntArray,
        pastDecoder: Past?,
        pastEncoder: Past?,
        useCache: Boolean,
    ): Pair<FloatArray, Past> {
        val t = encoderAttn.size / 2
        val feeds = HashMap<String, OnnxTensor>()
        feeds["input_ids"] = tensor(env, col, longArrayOf(8, 1))
        feeds["encoder_hidden_states"] = tensor(env, encoderHidden, longArrayOf(2, t.toLong(), D_MODEL.toLong()))
        feeds["encoder_attention_mask"] = tensor(env, encoderAttn.map { it.toLong() }.toLongArray(), longArrayOf(2, t.toLong()))
        feeds["use_cache_branch"] = boolTensor(env, useCache)

        for (l in 0 until NUM_LAYERS) {
            val (dk, dv) = pastDecoder?.let { it.keys[l] to it.values[l] } ?: (FloatArray(0) to FloatArray(0))
            feeds["past_key_values.$l.decoder.key"] = tensor(env, dk, pastShape(dk))
            feeds["past_key_values.$l.decoder.value"] = tensor(env, dv, pastShape(dv))
            val (ek, ev) = pastEncoder?.let { it.keys[l] to it.values[l] } ?: (FloatArray(0) to FloatArray(0))
            feeds["past_key_values.$l.encoder.key"] = tensor(env, ek, pastShape(ek))
            feeds["past_key_values.$l.encoder.value"] = tensor(env, ev, pastShape(ev))
        }

        var logits: FloatArray? = null
        var newKeys: Array<FloatArray>? = null
        var newValues: Array<FloatArray>? = null
        var newEncKeys: Array<FloatArray>? = null
        var newEncValues: Array<FloatArray>? = null
        var seqLen = -1

        try {
            decoder.run(feeds).use { result ->
                logits = FloatArray(8 * VOCAB_SIZE)
                (result.get("logits").get() as OnnxTensor).floatBuffer.get(logits!!)
                newKeys = Array(NUM_LAYERS) { l -> result.outputFloat("present.$l.decoder.key") }
                newValues = Array(NUM_LAYERS) { l -> result.outputFloat("present.$l.decoder.value") }
                newEncKeys = Array(NUM_LAYERS) { l -> result.outputFloat("present.$l.encoder.key") }
                newEncValues = Array(NUM_LAYERS) { l -> result.outputFloat("present.$l.encoder.value") }
                seqLen = decoderSeqLen(pastDecoder)
            }
        } finally {
            feeds.values.forEach { runCatching { it.close() } }
        }

        val newPast = Past(
            keys = newKeys!!,
            values = newValues!!,
            encoderKeys = newEncKeys!!,
            encoderValues = newEncValues!!,
            decoderSeqLen = seqLen,
        )
        return logits!! to newPast
    }

    private fun decoderSeqLen(pastDecoder: Past?): Int = (pastDecoder?.decoderSeqLen ?: 0) + 1

    private fun OrtSession.Result.outputFloat(name: String): FloatArray {
        val buf = (this.get(name).get() as OnnxTensor).floatBuffer
        val out = FloatArray(buf.remaining())
        buf.get(out)
        return out
    }

    private fun pastShape(data: FloatArray): LongArray {
        if (data.isEmpty()) return longArrayOf(2, NUM_HEADS.toLong(), 0, HEAD_DIM.toLong())
        // (batch=2, heads=16, seq, head_dim=64)
        val seq = data.size / (2 * NUM_HEADS * HEAD_DIM)
        return longArrayOf(2, NUM_HEADS.toLong(), seq.toLong(), HEAD_DIM.toLong())
    }

    private fun tensor(env: OrtEnvironment, data: LongArray, shape: LongArray): OnnxTensor =
        OnnxTensor.createTensor(env, LongBuffer.wrap(data), shape)

    private fun tensor(env: OrtEnvironment, data: FloatArray, shape: LongArray): OnnxTensor =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), shape)

    private fun boolTensor(env: OrtEnvironment, value: Boolean): OnnxTensor {
        val buffer = ByteBuffer.allocateDirect(1)
        buffer.put(if (value) 1 else 0).rewind()
        return OnnxTensor.createTensor(env, buffer, longArrayOf(1), OnnxJavaType.BOOL)
    }

    override fun close() {
        runCatching { textEncoder.close() }
        runCatching { decoder.close() }
        runCatching { encodec.close() }
    }

    /** Decoder past state carried between autoregressive steps. */
    private data class Past(
        val keys: Array<FloatArray>,
        val values: Array<FloatArray>,
        val encoderKeys: Array<FloatArray>,
        val encoderValues: Array<FloatArray>,
        val decoderSeqLen: Int,
    )

    companion object {
        const val TEXT_ENCODER_FILE = "text_encoder_int8.onnx"
        const val DECODER_FILE = "decoder_model_merged_int8.onnx"
        const val ENCODEC_FILE = "encodec_decode_int8.onnx"
        const val PAD_TOKEN = 2048
        const val NUM_CODEBOOKS = 4
        const val NUM_LAYERS = 24
        const val NUM_HEADS = 16
        const val HEAD_DIM = 64
        const val VOCAB_SIZE = 2048
        const val D_MODEL = 768
        const val SAMPLES_PER_CODE_FRAME = 640
        const val SAMPLE_RATE = 32000
        const val DEFAULT_GUIDANCE = 3f
    }
}
