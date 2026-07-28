package com.t2v.tts.engines

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.t2v.core.audio.AudioChunk
import com.t2v.core.audio.AudioEncoder
import com.t2v.tts.EngineInfo
import com.t2v.tts.EngineInfo.EngineKind
import com.t2v.tts.TtsEngineException
import com.t2v.tts.TtsRequest
import com.t2v.tts.TtsResult
import com.t2v.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Kokoro runs entirely on Android through the official sherpa-onnx JNI runtime.
 *
 * The model directory is downloaded separately and must contain model.onnx,
 * voices.bin, tokens.txt and espeak-ng-data/.
 */
class KokoroTtsEngine(
    private val modelDir: File,
) : TtsEngine {
    override val info: EngineInfo = ENGINE_INFO
    private var tts: OfflineTts? = null

    override fun isAvailable(): Boolean = requiredFiles().all(File::exists)

    override suspend fun listVoices(): List<VoiceInfo> = KOKORO_VOICES.map { (id, _) ->
        VoiceInfo(
            id = id,
            displayName = id.replace('_', ' ').replaceFirstChar { it.uppercase() },
            language = if (id.startsWith("b")) "en-gb" else "en-us",
            gender = if (id[1] == 'f') "female" else "male",
            engineId = info.id,
            isLocal = true,
            sampleRate = 24000,
        )
    }

    override suspend fun preload(): Unit = withContext(Dispatchers.IO) {
        ensureTts()
    }

    override suspend fun synthesize(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val runtime = ensureTts()
        val voiceId = request.voice.voice.ifBlank { DEFAULT_VOICE }
        val speakerId = KOKORO_VOICES[voiceId] ?: KOKORO_VOICES.getValue(DEFAULT_VOICE)
        val audio = runtime.generate(
            text = request.text,
            sid = speakerId,
            speed = request.voice.speed.toFloat().coerceIn(0.5f, 2.0f),
        )
        if (audio.samples.isEmpty()) {
            throw TtsEngineException.Generic("Kokoro returned empty audio")
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
        if (!isAvailable()) {
            throw TtsEngineException.NotInstalled(info.id)
        }
        val kokoro = OfflineTtsKokoroModelConfig(
            model = File(modelDir, "model.onnx").absolutePath,
            voices = File(modelDir, "voices.bin").absolutePath,
            tokens = File(modelDir, "tokens.txt").absolutePath,
            dataDir = File(modelDir, "espeak-ng-data").absolutePath,
            lengthScale = 1.0f,
        )
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                kokoro = kokoro,
                numThreads = 4,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        return OfflineTts(config = config).also { tts = it }
    }

    private fun requiredFiles(): List<File> = listOf(
        File(modelDir, "model.onnx"),
        File(modelDir, "voices.bin"),
        File(modelDir, "tokens.txt"),
        File(modelDir, "espeak-ng-data"),
    )

    companion object {
        const val DEFAULT_VOICE = "af"
        val KOKORO_VOICES = linkedMapOf(
            "af" to 0,
            "af_bella" to 1,
            "af_nicole" to 2,
            "af_sarah" to 3,
            "af_sky" to 4,
            "am_adam" to 5,
            "am_michael" to 6,
            "bf_emma" to 7,
            "bf_isabella" to 8,
            "bm_george" to 9,
            "bm_lewis" to 10,
        )
        val ENGINE_INFO = EngineInfo(
            id = "kokoro",
            displayName = "Kokoro 82M (on-device)",
            kind = EngineKind.Local,
            supportsLocal = true,
        )
    }
}
