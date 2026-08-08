package com.t2v.tts.engines

import com.t2v.core.audio.AudioEncoder
import com.t2v.tts.EngineInfo
import com.t2v.tts.EngineInfo.EngineKind
import com.t2v.tts.TtsEngineException
import com.t2v.tts.TtsRequest
import com.t2v.tts.TtsResult
import com.t2v.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Yandex SpeechKit Text-to-Speech (cloud) via the HTTP v1 API.
 *
 *   POST https://tts.api.cloud.yandex.net/speech/v1/tts:synthesize
 *
 * Authentication is an API key (service account) plus the folder id, both
 * stored in Settings (`engines.yandex.apiKey` / `engines.yandex.folderId`).
 * Response with `format=lpcm` is raw 16-bit mono PCM, which we wrap into a real
 * WAV file so the rest of the pipeline (concat, ffmpeg mix) can read it.
 */
class YandexTtsEngine(
    private val apiKey: String,
    private val folderId: String,
    private val baseUrl: String = "https://tts.api.cloud.yandex.net",
    private val defaultVoice: String = "alena",
) : TtsEngine {

    override val info: EngineInfo = ENGINE_INFO

    override fun isAvailable(): Boolean = apiKey.isNotBlank() && folderId.isNotBlank()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun synthesize(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val voice = request.voice.voice.ifBlank { defaultVoice }
        val lang = if (request.voice.lang.startsWith("en", ignoreCase = true)) "en-US" else "ru-RU"
        val speed = request.voice.speed.coerceIn(0.1, 3.0).toString()
        val emotion = mapEmotion(request.voice.emotion)
        val form = FormBody.Builder()
            .add("text", request.text)
            .add("lang", lang)
            .add("voice", voice)
            .add("emotion", emotion)
            .add("speed", speed)
            .add("format", "lpcm")
            .add("sampleRateHertz", "48000")
            .build()
        val req = Request.Builder()
            .url("$baseUrl/speech/v1/tts:synthesize")
            .header("Authorization", "Api-Key $apiKey")
            .header("x-folder-id", folderId)
            .post(form)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw TtsEngineException.Api(resp.code, resp.body?.string().orEmpty().take(300))
            }
            val pcm = resp.body?.bytes() ?: throw TtsEngineException.Generic("Empty Yandex response")
            request.outputFile.parentFile?.mkdirs()
            wrapPcmToWav(pcm, request.outputFile)
            TtsResult(
                outputFile = request.outputFile,
                sampleRate = 48000,
                channels = 1,
                durationMs = -1,
                bytesWritten = pcm.size.toLong(),
            )
        }
    }

    override suspend fun listVoices(): List<VoiceInfo> = listOf(
        "alena", "jane", "filipp", "ermil", "zahar", "omazh",
    ).map { name ->
        VoiceInfo(
            id = name,
            displayName = name,
            language = if (name == "alena" || name == "jane") "ru-RU" else "ru-RU",
            engineId = ENGINE_INFO.id,
        )
    }

    override suspend fun preload() {}
    override suspend fun close() {}
    override suspend fun cancel() {}

    private fun mapEmotion(emotion: String?): String = when (emotion) {
        "evil", "good", "neutral" -> emotion
        else -> "neutral"
    }

    private fun wrapPcmToWav(pcm: ByteArray, out: java.io.File) {
        require(pcm.size % 2 == 0) { "Yandex lpcm must be 16-bit mono" }
        val samples = ShortArray(pcm.size / 2)
        for (i in samples.indices) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt() and 0xFF
            samples[i] = ((hi shl 8) or lo).toShort()
        }
        AudioEncoder.encodePcm16MonoWav(out, samples, sampleRate = 48000)
    }

    companion object {
        val ENGINE_INFO = EngineInfo(
            id = "yandex",
            displayName = "Yandex Speech",
            kind = EngineKind.Cloud,
            requiresApiKey = true,
            configSchema = listOf(
                EngineInfo.ConfigField("apiKey", "API Key", EngineInfo.ConfigField.FieldType.Password, secret = true),
                EngineInfo.ConfigField("folderId", "Folder ID", EngineInfo.ConfigField.FieldType.String),
                EngineInfo.ConfigField("voice", "Voice", EngineInfo.ConfigField.FieldType.Select, default = "alena", options = listOf("alena", "jane", "filipp", "ermil", "zahar", "omazh")),
            ),
        )
    }
}