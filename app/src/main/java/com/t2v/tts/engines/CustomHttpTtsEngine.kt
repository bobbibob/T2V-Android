package com.t2v.tts.engines

import com.t2v.tts.EngineInfo
import com.t2v.tts.EngineInfo.EngineKind
import com.t2v.tts.TtsRequest
import com.t2v.tts.VoiceInfo
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Custom HTTP TTS — прозрачный прокси к локальному/удалённому HTTP-серверу
 * TTS. Конфиг полностью настраивается пользователем:
 *
 *   - url: эндпоинт (POST)
 *   - method: POST/GET/PUT
 *   - headers: заголовки (многострочный key: value)
 *   - bodyTemplate: JSON-шаблон, {{text}}, {{voice}}, {{lang}}, {{speed}}
 *   - responseFormat: json | audio
 *   - responseAudioField: имя поля JSON с base64-аудио (опц.)
 *   - responseUrlField: имя поля JSON со ссылкой на аудио (опц.)
 *
 * Прямой порт `app/tts/api_engines.py:CustomHttpTTSEngine`.
 */
class CustomHttpTtsEngine(
    private val config: Config,
) : AbstractHttpEngine(ENGINE_INFO) {

    data class Config(
        val url: String,
        val method: String = "POST",
        val headers: Map<String, String> = emptyMap(),
        val bodyTemplate: String = """{"text": "{{text}}", "voice": "{{voice}}", "lang": "{{lang}}"}""",
        val responseFormat: ResponseFormat = ResponseFormat.JSON,
        val responseAudioField: String? = "audio",
        val responseUrlField: String? = null,
        val contentType: String = "application/json",
    )

    enum class ResponseFormat { JSON, AUDIO }

    override fun endpoint(): String = config.url

    override fun headers(): Map<String, String> = config.headers

    override fun buildBody(request: TtsRequest): JsonObject {
        val rendered = config.bodyTemplate
            .replace("{{text}}", escape(request.text))
            .replace("{{voice}}", escape(request.voice.voice))
            .replace("{{lang}}", escape(request.voice.lang))
            .replace("{{speed}}", request.voice.speed.toString())
            .replace("{{volume}}", request.voice.volume.toString())
            .replace("{{pitch}}", request.voice.pitch.toString())
        return runCatching { json.parseToJsonElement(rendered).let { it as JsonObject } }
            .getOrElse { buildJsonObject { put("text", rendered) } }
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun downloadBytes(url: String): ByteArray {
        val rq = okhttp3.Request.Builder().url(url).get().build()
        httpClient.newCall(rq).execute().use { r ->
            if (!r.isSuccessful) throw com.t2v.tts.TtsEngineException.Api(r.code, "audio download failed")
            return r.body?.bytes() ?: throw com.t2v.tts.TtsEngineException.Generic("Empty audio body")
        }
    }

    override suspend fun synthesize(request: TtsRequest): com.t2v.tts.TtsResult {
        val body = buildBody(request).toString().toRequestBody(config.contentType.toMediaType())
        val req = Request.Builder()
            .url(config.url)
            .headers(headers().toHeaders())
            .method(config.method.uppercase(), if (config.method.equals("GET", true)) null else body)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw com.t2v.tts.TtsEngineException.Api(resp.code, resp.body?.string().orEmpty().take(500))
            }
            val ct = resp.body?.contentType()?.toString().orEmpty()
            val bytes: ByteArray = if (config.responseFormat == ResponseFormat.AUDIO || ct.contains("audio/")) {
                resp.body?.bytes() ?: throw com.t2v.tts.TtsEngineException.Generic("Empty audio body")
            } else {
                val text = resp.body?.string().orEmpty()
                val obj = runCatching { json.parseToJsonElement(text).let { it as JsonObject } }
                    .getOrElse { throw com.t2v.tts.TtsEngineException.Generic("Invalid JSON: ${text.take(200)}") }
                val inline = config.responseAudioField?.let { obj[it]?.let { (it as? JsonPrimitive)?.content } }
                val url = config.responseUrlField?.let { obj[it]?.let { (it as? JsonPrimitive)?.content } }
                when {
                    !url.isNullOrEmpty() -> downloadBytes(url)
                    !inline.isNullOrEmpty() -> android.util.Base64.decode(inline, android.util.Base64.DEFAULT)
                    else -> throw com.t2v.tts.TtsEngineException.Generic("No audio data in response: ${text.take(200)}")
                }
            }
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

    companion object {
        val ENGINE_INFO = EngineInfo(
            id = "custom_http",
            displayName = "Custom HTTP TTS",
            kind = EngineKind.Cloud,
            configSchema = listOf(
                EngineInfo.ConfigField("url", "Endpoint URL", EngineInfo.ConfigField.FieldType.String),
                EngineInfo.ConfigField("bodyTemplate", "Body template", EngineInfo.ConfigField.FieldType.String, default = """{"text": "{{text}}", "voice": "{{voice}}", "lang": "{{lang}}"}"""),
                EngineInfo.ConfigField("responseAudioField", "Audio field", EngineInfo.ConfigField.FieldType.String, default = "audio"),
                EngineInfo.ConfigField("responseUrlField", "URL field", EngineInfo.ConfigField.FieldType.String, default = ""),
            ),
        )
    }
}
