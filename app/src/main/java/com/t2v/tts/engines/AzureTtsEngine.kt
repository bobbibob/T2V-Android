package com.t2v.tts.engines

import com.t2v.tts.EngineInfo
import com.t2v.tts.EngineInfo.EngineKind
import com.t2v.tts.ExpressiveSpeech
import com.t2v.tts.TtsRequest
import com.t2v.tts.VoiceInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Microsoft Azure Speech (REST API, не SDK).
 *
 * Прямой порт `app/tts/api_engines.py:AzureTTSEngine`.
 *
 * SSML-шаблон соответствует документации Azure Speech:
 *   https://learn.microsoft.com/azure/ai-services/speech-service/rest-text-to-speech
 */
class AzureTtsEngine(
    private val subscriptionKey: String,
    private val region: String,
    private val defaultVoice: String = "en-US-JennyNeural",
    private val outputFormat: String = "audio-24khz-96kbitrate-mono-mp3",
) : AbstractHttpEngine(ENGINE_INFO) {

    override fun endpoint(): String =
        "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"

    override fun headers(): Map<String, String> = mapOf(
        "Ocp-Apim-Subscription-Key" to subscriptionKey,
        "Content-Type" to "application/ssml+xml",
        "X-Microsoft-OutputFormat" to outputFormat,
        "User-Agent" to "T2V",
    )

    override fun buildBody(request: TtsRequest): JsonObject {
        // Azure ожидает SSML в теле, не JSON. Сгенерируем SSML здесь и
        // переопределим поведение родителя, сериализуя как строку.
        val ssml = buildSsml(request)
        return buildJsonObject {
            put("text", request.text)
            put("ssml", ssml) // используется кастомной реализацией synthesize
        }
    }

    override suspend fun synthesize(request: TtsRequest): com.t2v.tts.TtsResult {
        val ssml = buildSsml(request)
        val req = okhttp3.Request.Builder()
            .url(endpoint())
            .headers(headers().toHeaders())
            .post(ssml.toRequestBody("application/ssml+xml; charset=utf-8".toMediaType()))
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw com.t2v.tts.TtsEngineException.Api(resp.code, resp.body?.string().orEmpty().take(500))
            }
            val bytes = resp.body?.bytes() ?: throw com.t2v.tts.TtsEngineException.Generic("Empty Azure response")
            request.outputFile.parentFile?.mkdirs()
            request.outputFile.writeBytes(bytes)
            return com.t2v.tts.TtsResult(
                outputFile = request.outputFile,
                sampleRate = 24000,
                channels = 1,
                durationMs = -1,
                bytesWritten = bytes.size.toLong(),
            )
        }
    }

    override suspend fun listVoices(): List<VoiceInfo> = emptyList()

    private fun buildSsml(request: TtsRequest): String {
        val voice = request.voice.voice.ifEmpty { defaultVoice }
        val lang = request.voice.lang.ifEmpty { voice.substringBefore('-', "en-US") }
        val speedPercent = ((request.voice.speed - 1.0) * 100).toInt()
        val volPercent = ((request.voice.volume - 1.0) * 100).toInt()
        val rate = if (speedPercent == 0) "" else "<prosody rate=\"${speedPercent}%\">"
        val rateClose = if (speedPercent == 0) "" else "</prosody>"
        val vol = if (volPercent == 0) "" else "<prosody volume=\"${volPercent}%\">"
        val volClose = if (volPercent == 0) "" else "</prosody>"
        val pitchPercent = ((request.voice.pitch - 1.0) * 100).toInt()
        val pitch = if (pitchPercent == 0) "" else "<prosody pitch=\"${pitchPercent}%\">"
        val pitchClose = if (pitchPercent == 0) "" else "</prosody>"
        val style = azureStyle(
            request.voice.extras[ExpressiveSpeech.DELIVERY]
                ?: request.voice.emotion,
        )
        val expressiveOpen = style?.let { """<mstts:express-as style="$it">""" }.orEmpty()
        val expressiveClose = if (style == null) "" else "</mstts:express-as>"

        return """<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xmlns:mstts="https://www.w3.org/2001/mstts" xml:lang="$lang">
  <voice name="$voice">$expressiveOpen$vol$pitch$rate${escapeXml(request.text)}$rateClose$pitchClose$volClose$expressiveClose</voice>
</speak>"""
    }

    private fun azureStyle(value: String?): String? {
        val normalized = value?.lowercase()
        return when (normalized) {
            "happy" -> "cheerful"
            "sad", "angry", "afraid", "calm", "excited", "friendly", "hopeful",
            "shouting", "whispering", "terrified", "serious", "empathetic" -> normalized
            "whisper" -> "whispering"
            "shout" -> "shouting"
            else -> null
        }
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        val ENGINE_INFO = EngineInfo(
            id = "azure",
            displayName = "Azure Speech",
            kind = EngineKind.Cloud,
            requiresApiKey = true,
            configSchema = listOf(
                EngineInfo.ConfigField("subscriptionKey", "Subscription Key", EngineInfo.ConfigField.FieldType.Password, secret = true),
                EngineInfo.ConfigField("region", "Region", EngineInfo.ConfigField.FieldType.String, default = "eastus"),
                EngineInfo.ConfigField("voice", "Voice", EngineInfo.ConfigField.FieldType.String, default = "en-US-JennyNeural"),
            ),
        )
    }
}
