package com.t2v.tts.engines

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.t2v.core.audio.AudioChunk
import com.t2v.core.audio.AudioEncoder
import com.t2v.tts.EngineInfo
import com.t2v.tts.TtsEngineException
import com.t2v.tts.TtsRequest
import com.t2v.tts.TtsResult
import com.t2v.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Generic on-device TTS engine backed by any installed sherpa-onnx model
 * directory (a single `model.onnx` + `tokens.txt` + `espeak-ng-data` layout,
 * i.e. a VITS/Piper-style model). Used to expose catalog voice models that are
 * not special-cased like [KokoroTtsEngine].
 *
 * This keeps the local model story data-driven: a catalog entry that points to
 * a real Hugging Face repository whose files are downloaded into
 * [modelDir] becomes a selectable engine without new adapter code.
 */
class SherpaOnnxLocalEngine(
    private val modelDir: File,
    private val engineId: String,
    private val displayName: String,
    private val voiceLabel: String = displayName,
    private val sampleRate: Int = 22050,
    private val language: String = "",
) : TtsEngine {
    override val info: EngineInfo = EngineInfo(
        id = engineId,
        displayName = displayName,
        kind = EngineInfo.EngineKind.Local,
        supportsLocal = true,
    )

    private var tts: OfflineTts? = null

    override fun isAvailable(): Boolean = findModelRoot() != null

    override suspend fun listVoices(): List<VoiceInfo> = listOf(
        VoiceInfo(
            id = engineId,
            displayName = voiceLabel,
            language = language,
            gender = "",
            engineId = info.id,
            isLocal = true,
            sampleRate = sampleRate,
        ),
    )

    override suspend fun preload(): Unit = Unit

    override suspend fun synthesize(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val runtime = ensureTts()
        val audio = runtime.generate(
            text = request.text,
            sid = 0,
            speed = request.voice.speed.toFloat().coerceIn(0.5f, 2.0f),
        )
        if (audio.samples.isEmpty()) {
            throw TtsEngineException.Generic("$displayName returned empty audio")
        }
        val volume = request.voice.volume.coerceIn(0.0, 4.0).toFloat()
        val pcm = ShortArray(audio.samples.size) { index ->
            (audio.samples[index] * volume * Short.MAX_VALUE)
                .toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        request.outputFile.parentFile?.mkdirs()
        AudioEncoder.writeWav(
            request.outputFile,
            AudioChunk(samples = pcm, sampleRate = audio.sampleRate, channels = 1),
        )
        TtsResult(
            outputFile = request.outputFile,
            sampleRate = audio.sampleRate,
            channels = 1,
            durationMs = ((pcm.size * 1000L) / audio.sampleRate).toInt(),
            bytesWritten = request.outputFile.length(),
        )
    }

    override suspend fun cancel(): Unit = Unit

    override suspend fun close(): Unit {
        tts?.release()
        tts = null
    }

    private fun ensureTts(): OfflineTts {
        tts?.let { return it }
        val root = findModelRoot() ?: throw TtsEngineException.NotInstalled(info.id)
        val model = root.walkTopDown().firstOrNull { it.isFile && it.extension == "onnx" }
            ?: throw TtsEngineException.NotInstalled(info.id)
        val tokens = root.walkTopDown().firstOrNull { it.isFile && it.name == "tokens.txt" }
            ?: throw TtsEngineException.NotInstalled(info.id)
        val dataDir = root.walkTopDown().firstOrNull { it.isDirectory && it.name == "espeak-ng-data" }
            ?: throw TtsEngineException.NotInstalled(info.id)
        val vits = OfflineTtsVitsModelConfig(
            model = model.absolutePath,
            tokens = tokens.absolutePath,
            dataDir = dataDir.absolutePath,
        )
        return OfflineTts(
            OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = vits,
                    numThreads = 4,
                    debug = false,
                    provider = "cpu",
                ),
                maxNumSentences = 1,
            ),
        ).also {
            tts = it
        }
    }

    private fun findModelRoot(): File? {
        if (!modelDir.isDirectory) return null
        val hasModel = modelDir.walkTopDown().any { it.isFile && it.extension == "onnx" }
        val hasTokens = modelDir.walkTopDown().any { it.isFile && it.name == "tokens.txt" }
        val hasEspeak = modelDir.walkTopDown().any { it.isDirectory && it.name == "espeak-ng-data" }
        return modelDir.takeIf { hasModel && hasTokens && hasEspeak }
    }
}