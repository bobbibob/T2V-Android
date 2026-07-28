package com.t2v.tts.engines

import com.t2v.tts.EngineInfo
import com.t2v.tts.TtsEngineException
import com.t2v.tts.TtsRequest
import com.t2v.tts.TtsResult
import com.t2v.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Базовый класс для облачных HTTP-движков.
 *
 * Шаблон делает одно и то же для OpenAI/ElevenLabs/Gemini/Azure/Custom:
 *   1. Подставляет голос, формат, скорость, язык.
 *   2. Отправляет POST.
 *   3. Получает либо JSON с URL/base64, либо сырой PCM/MP3.
 *   4. Сохраняет в файл.
 *
 * Каждый подкласс задаёт:
 *   - endpoint URL
 *   - заголовки (auth и пр.)
 *   - шаблон тела запроса
 *   - правило разбора ответа (URL / inline-audio)
 */
abstract class AbstractHttpEngine(
    override val info: EngineInfo,
    protected val httpClient: OkHttpClient = defaultClient(),
) : TtsEngine {

    protected val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    /** URL эндпоинта. */
    protected abstract fun endpoint(): String

    /** Engines with a voice in the URL can override this request-aware form. */
    protected open fun endpoint(request: TtsRequest): String = endpoint()

    /** HTTP-заголовки. */
    protected abstract fun headers(): Map<String, String>

    /** JSON-тело запроса. */
    protected abstract fun buildBody(request: TtsRequest): JsonObject

    /** Куда сохранить результат (если ответ содержит URL/base64/inline). */
    protected open fun inlineAudioField(): String? = "audio"

    /** Если ответ — ссылка на файл. */
    protected open fun audioUrlField(): String? = "url"

    /** Если ответ — JSON c audio в base64. */
    protected open fun audioBase64Field(): String? = null

    /** Куда сохранить: получив URL, дёргаем GET, или сразу пишем inline. */
    override suspend fun synthesize(request: TtsRequest): TtsResult = withContext(Dispatchers.IO) {
        val body = buildBody(request).toString().toRequestBody(JSON_MEDIA)
        val req = Request.Builder()
            .url(endpoint(request))
            .headers(headers().toHeaders())
            .post(body)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw TtsEngineException.Api(resp.code, err.take(500))
            }
            val contentType = resp.body?.contentType()?.toString().orEmpty()
            if (contentType.contains("audio/") || contentType.contains("octet-stream")) {
                // Сырой аудио-поток — пишем как есть
                val bytes = resp.body?.bytes() ?: throw TtsEngineException.Generic("Empty response body")
                request.outputFile.parentFile?.mkdirs()
                request.outputFile.writeBytes(bytes)
                return@withContext TtsResult(
                    outputFile = request.outputFile,
                    sampleRate = 24000,
                    channels = 1,
                    durationMs = -1,
                    bytesWritten = bytes.size.toLong(),
                )
            }
            // JSON-ответ
            val text = resp.body?.string().orEmpty()
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrElse {
                throw TtsEngineException.Generic("Invalid JSON: ${text.take(200)}")
            }
            val url = audioUrlField()?.let { obj[it]?.jsonPrimitive?.let { p -> if (p.isString) p.content else null } }
            val inline = inlineAudioField()?.let { obj[it]?.jsonPrimitive?.let { p -> if (p.isString) p.content else null } }
            val base64 = audioBase64Field()?.let { obj[it]?.jsonPrimitive?.let { p -> if (p.isString) p.content else null } }
            val bytes: ByteArray = when {
                !url.isNullOrEmpty() -> downloadBytes(url)
                !inline.isNullOrEmpty() -> inline.toByteArray(Charsets.UTF_8)
                !base64.isNullOrEmpty() -> android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                else -> throw TtsEngineException.Generic("No audio data in response: ${text.take(200)}")
            }
            request.outputFile.parentFile?.mkdirs()
            request.outputFile.writeBytes(bytes)
            TtsResult(
                outputFile = request.outputFile,
                sampleRate = 24000,
                channels = 1,
                durationMs = -1,
                bytesWritten = bytes.size.toLong(),
            )
        }
    }

    private fun downloadBytes(url: String): ByteArray {
        val rq = Request.Builder().url(url).get().build()
        httpClient.newCall(rq).execute().use { r ->
            if (!r.isSuccessful) throw TtsEngineException.Api(r.code, "audio download failed")
            return r.body?.bytes() ?: throw TtsEngineException.Generic("Empty audio body")
        }
    }

    override suspend fun listVoices(): List<VoiceInfo> = emptyList() // Переопределяется по желанию.
    override suspend fun preload() {}
    override suspend fun close() {}
    override suspend fun cancel() {}
    override fun isAvailable(): Boolean = true

    // --- helpers -----------------------------------------------------------

    protected fun Map<String, String>.toHeaders(): okhttp3.Headers {
        val b = okhttp3.Headers.Builder()
        for ((k, v) in this) b.add(k, v)
        return b.build()
    }

    protected fun putString(target: MutableMap<String, JsonElement>, key: String, value: String) {
        target[key] = JsonPrimitive(value)
    }

    protected fun JsonPrimitive.contentOrNull(): String? =
        if (this.isString) content else content.takeIf { it != "null" }

    companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
