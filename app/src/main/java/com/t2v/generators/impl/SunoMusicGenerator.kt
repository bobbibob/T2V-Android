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
 * Suno API v1 — генерация музыки (до 4 минут).
 *
 * Async workflow:
 *   1. POST /v1/generations    → {id, status: "queued"}
 *   2. GET  /v1/generations/id → {status: "complete", audio_url}
 *   3. GET  audio_url          → mp3 bytes
 *
 * **Status: scaffold.** Реальный polling-цикл будет добавлен после стабилизации
 * Suno API v1 в production (на 2026-07-28 — open beta, rate limits часто меняются).
 */
class SunoMusicGenerator(
    private val apiKey: String,
    private val baseUrl: String = "https://api.suno.ai/v1",
) : Generator {
    override val id: String = "suno.api"
    override val displayName: String = "Suno API (cloud)"
    override val category: GeneratorCategory = GeneratorCategory.Music

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .callTimeout(240, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(request: GeneratorRequest): GeneratorResult =
        withContext(Dispatchers.IO) {
            require(request.prompt.isNotBlank()) { "Suno prompt must not be blank" }
            // TODO: implement the full async workflow (POST → poll → download).
            //       Right now we just throw a clear error so the UI shows it.
            throw UnsupportedOperationException(
                "Suno API client is scaffold-only; will be wired once the v1 API " +
                    "stabilises. Use ElevenLabs Music or Stable Audio cloud for now.",
            )
            @Suppress("UNREACHABLE_CODE")
            val body = buildJsonObject {
                put("prompt", request.prompt)
                put("make_instrumental", false)
                put("wait_audio", true)
            }
            val req = Request.Builder()
                .url("$baseUrl/generations")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw RuntimeException("Suno ${resp.code}: ${err.take(200)}")
                }
                val bytes = resp.body?.bytes() ?: throw RuntimeException("Suno: empty body")
                request.outputFile.parentFile?.mkdirs()
                request.outputFile.writeBytes(bytes)
                GeneratorResult(
                    outputFile = request.outputFile,
                    sampleRate = 44100,
                    channels = 1,
                    durationMs = request.durationSeconds * 1000,
                    bytesWritten = bytes.size.toLong(),
                )
            }
        }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
