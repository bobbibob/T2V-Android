package com.t2v.generators.impl

import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Lyria 2 (Google) — генерация музыки через Gemini API.
 *
 * Эндпоинт: `models/lyria-2:generate`. Возвращает WAV 48 кГц стерео.
 * До 60 секунд за один запрос, не более 10 запросов в минуту.
 *
 * **Лицензия:** Google terms, по умолчанию только non-commercial.
 *
 * **Status: scaffold.** Реальный вызов появится, когда Google откроет
 * Lyria 2 для general availability (на 2026-07-28 — limited preview).
 */
class Lyria2MusicGenerator(
    private val apiKey: String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) : Generator {
    override val id: String = "lyria-2.gemini"
    override val displayName: String = "Lyria 2 (Google, via Gemini)"
    override val category: GeneratorCategory = GeneratorCategory.Music

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(request: GeneratorRequest): GeneratorResult =
        withContext(Dispatchers.IO) {
            require(request.prompt.isNotBlank()) { "Lyria prompt must not be blank" }
            val body = buildJsonObject {
                put("prompt", request.prompt)
                put("model", "lyria-2")
                if (request.durationSeconds in 1..60) {
                    put("duration_seconds", request.durationSeconds)
                }
            }
            val req = Request.Builder()
                .url("$baseUrl/models/lyria-2:generate?key=$apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw RuntimeException("Lyria ${resp.code}: ${err.take(200)}")
                }
                val bytes = resp.body?.bytes() ?: throw RuntimeException("Lyria: empty body")
                request.outputFile.parentFile?.mkdirs()
                request.outputFile.writeBytes(bytes)
                GeneratorResult(
                    outputFile = request.outputFile,
                    sampleRate = 48000,
                    channels = 2,
                    durationMs = request.durationSeconds * 1000,
                    bytesWritten = bytes.size.toLong(),
                )
            }
        }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
