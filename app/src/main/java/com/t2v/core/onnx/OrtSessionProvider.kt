package com.t2v.core.onnx

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtSession.SessionOptions
import java.io.File
import java.io.FileOutputStream

/**
 * Lazy-init wrapper around ONNX Runtime sessions for T2V.
 *
 * Why this exists:
 * - onnxruntime-android AAR (Microsoft, Apache-2.0) is a heavy dependency.
 *   We don't want to load it eagerly for users who never use MusicGen/TinyMusician.
 * - Each ONNX model (text_encoder, decoder, encodec) gets its own session.
 * - Sessions are heavy: ~1-3 seconds to create, plus memory (~500 MB for decoder).
 *   We cache them per (modelId, options) and re-use.
 * - All I/O is synchronous and CPU-only. Mobile NNAPI/XNNPACK would speed up
 *   3-5x but requires careful configuration per SoC.
 *
 * Usage:
 * ```
 * val session = OrtSessionProvider.getOrCreate(
 *     context = appContext,
 *     modelId = "musicgen-text-encoder",
 *     onnxFile = File(filesDir, "models/musicgen/text_encoder.onnx"),
 * )
 * val inputTensor = OnnxTensor.createTensor(env, inputIds, shape)
 * val outputs = session.run(mapOf("input_ids" to inputTensor, "attention_mask" to maskTensor))
 * val hidden = outputs[0].value as Array<Array<FloatArray>>
 * ```
 *
 * Currently used by:
 * - [com.t2v.generators.impl.MusicGenOnnxGenerator]
 * - (future) TinyMusicianOnnxGenerator, KokoroOnnxGenerator, etc.
 */
object OrtSessionProvider {

    @Volatile
    private var environment: OrtEnvironment? = null

    private val sessions = mutableMapOf<String, OrtSession>()

    /**
     * Returns a singleton OrtEnvironment, creating it on first access.
     * Thread-safe.
     */
    @Synchronized
    fun environment(): OrtEnvironment {
        return environment ?: OrtEnvironment.getEnvironment().also { environment = it }
    }

    /**
     * Returns a cached session for [modelId], or creates one from [onnxFile].
     * Subsequent calls with the same modelId return the same session.
     */
    @Synchronized
    fun getOrCreate(
        context: Context,
        modelId: String,
        onnxFile: File,
        options: SessionOptions = defaultOptions(),
    ): OrtSession {
        sessions[modelId]?.let { return it }
        if (!onnxFile.isFile) {
            throw IllegalStateException(
                "ONNX file not found: ${onnxFile.absolutePath}. " +
                    "The model has not been downloaded yet."
            )
        }
        val session = environment().createSession(onnxFile.absolutePath, options)
        sessions[modelId] = session
        return session
    }

    /**
     * Default session options: CPU only, all available CPU cores,
     * optimized for repeated inference of medium-sized models.
     */
    fun defaultOptions(): SessionOptions {
        val opts = SessionOptions()
        // CPU only — on ARM64 phones we'd add NNAPI, but it varies per SoC
        // opts.addCPU(true)
        // opts.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors())
        return opts
    }

    /**
     * Closes the session for [modelId], freeing native memory.
     * Use this when the user deletes the model or switches generators.
     */
    @Synchronized
    fun closeSession(modelId: String) {
        sessions.remove(modelId)?.close()
    }

    /**
     * Closes all sessions. Call from Application.onTerminate() or after test runs.
     */
    @Synchronized
    fun closeAll() {
        sessions.values.forEach { it.close() }
        sessions.clear()
    }

    /**
     * Total memory used by all loaded sessions (rough estimate).
     * Useful for diagnostic logging.
     */
    fun loadedSessionCount(): Int = sessions.size

    /**
     * Helper: copy a file from app assets to a target file.
     * Currently unused but kept for the case where models are bundled
     * (NOT in our case — AGENTS.md says no models in APK).
     */
    @Suppress("unused")
    fun copyAssetToFile(context: Context, assetPath: String, target: File) {
        target.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }
}

/**
 * Convenience function to convert a [Map<String, Any>] feed into
 * a properly-closed list of inputs, and dispose of intermediate tensors.
 */
inline fun <T> withOrtSession(
    session: OrtSession,
    feed: Map<String, Any>,
    block: (List<OnnxTensor?>) -> T,
): T {
    val inputs = feed.map { (name, value) ->
        when (value) {
            is OnnxTensor -> name to value
            else -> throw IllegalArgumentException(
                "Unsupported input type for '$name': ${value?.javaClass?.simpleName}. " +
                    "Use OnnxTensor for all inputs."
            )
        }
    }.toMap()
    val outputs = session.run(inputs)
    return try {
        block(outputs)
    } finally {
        outputs.forEach { it.close() }
    }
}
