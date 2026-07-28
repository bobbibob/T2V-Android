package com.t2v.tts.engines

import android.content.Context
import com.t2v.core.audio.AudioEncoder
import com.t2v.tts.EngineInfo
import com.t2v.tts.EngineInfo.EngineKind
import com.t2v.tts.TtsEngineException
import com.t2v.tts.TtsRequest
import com.t2v.tts.TtsResult
import com.t2v.tts.VoiceConfig
import com.t2v.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ZipVoice Distill (k2-fsa) — on-device zero-shot voice cloning TTS.
 *
 * ZipVoice takes a 5-30 second reference audio of a target speaker and
 * synthesises arbitrary text in that voice. It runs locally on Android
 * through the existing sherpa-onnx JNI runtime that already powers
 * Kokoro and Piper. No cloud call, no API key, no network.
 *
 * **Status: scaffold only.** No working Android export exists for ZipVoice
 * Distill as of mid-2026. The community has produced an ONNX export of
 * the decoder (`csukuangfj/sherpa-onnx-zipvoice-distill-int8-...`), but it
 * is not yet packaged for the sherpa-onnx Android runtime. Until the
 * Android runtime is updated and smoke-tested on a real device, this
 * engine reports `isAvailable() = false` and surfaces a clear error if
 * a caller tries to `synthesize` against it.
 *
 * What this scaffold already does correctly:
 *  - advertises the engine through `EngineInfo.EngineKind.Local` so the
 *    T2V registry treats it as an on-device Android engine (per
 *    AGENTS.md);
 *  - carries the `supportsCloning = true` flag so the Voice gallery
 *    UI knows it can host a "Clone voice" entry;
 *  - validates the reference audio path and provides a meaningful
 *    failure mode (throws TtsEngineException.NotInstalled) instead of
 *    silently producing empty audio.
 *
 * Once the runtime is updated and the smoke-test passes, the body of
 * `synthesize` switches from the explicit `throw` to a real sherpa-onnx
 * `OfflineTts` call — the public contract does not change, so the rest
 * of the app keeps working.
 */
class ZipVoiceTtsEngine(
    private val modelDir: File,
) : TtsEngine {
    override val info: EngineInfo = ENGINE_INFO

    override fun isAvailable(): Boolean =
        requiredFiles().all(File::exists) && markSmokeTestedFile().exists()

    override suspend fun listVoices(): List<VoiceInfo> {
        // Zero-shot: no built-in voice list. Every reference audio is
        // its own voice. We return an empty list until a clone is
        // materialised through the Voice gallery.
        return emptyList()
    }

    override suspend fun preload() {
        // No-op until the runtime is wired. Real preload will call
        // `OfflineTts` construction similar to KokoroTtsEngine.
    }

    override suspend fun close() = Unit

    override suspend fun synthesize(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val ref = request.voice.referenceAudioPath
        if (ref.isNullOrBlank() || !File(ref).isFile) {
            throw TtsEngineException.Generic(
                "ZipVoice requires a reference audio file. " +
                "Upload a 5-30 second WAV via the Voice gallery before selecting ZipVoice.",
            )
        }
        if (!isAvailable()) {
            throw TtsEngineException.NotInstalled("zipvoice_distill")
        }
        // Real implementation: build OfflineTtsConfig with
        // ZipVoice model assets + ref audio prompt, call
        // `runtime.generate(text, promptText=transcript, promptSamples=refPcm)`.
        // Deferred until the runtime update lands.
        throw TtsEngineException.NotInstalled("zipvoice_distill")
    }

    override suspend fun cancel() = Unit

    private fun requiredFiles(): List<File> {
        val root = modelDir
        return listOf(
            File(root, "model.onnx"),
            File(root, "tokens.txt"),
            File(root, "voices.bin"), // not actually used by ZipVoice; placeholder
        )
    }

    private fun markSmokeTestedFile(): File =
        File(modelDir, ".smoke-tested")

    companion object {
        const val DEFAULT_MODEL_DIR = "zipvoice-distill-int8"

        val ENGINE_INFO = EngineInfo(
            id = "zipvoice_distill",
            displayName = "ZipVoice Distill (zero-shot clone, on-device)",
            kind = EngineKind.Local,
            supportsCloning = true,
            supportsLocal = true,
            requiresApiKey = false,
        )
    }
}
