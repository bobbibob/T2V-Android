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
 * OpenAI Music generation (gpt-4o-audio-preview / lyria).
 *
 * Документация: https://platform.openai.com/docs/guides/audio
 *
 * **Status: scaffold.** Реальный вызов появится, когда OpenAI стабилизирует
 * Music API в production-grade (на 2026-07-28 — preview).
 */
class OpenAiMusicGenerator(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
) : Generator {
    override val id: String = "openai.music"
    override val displayName: String = "OpenAI Music (cloud)"
    override val category: GeneratorCategory = GeneratorCategory.Music

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(request: GeneratorRequest): GeneratorResult =
        withContext(Dispatchers.IO) {
            require(request.prompt.isNotBlank()) { "Music prompt must not be blank" }
            // TODO: actual OpenAI Music endpoint. Until they ship the production
            //       variant, surface a clear "endpoint not yet wired" error so the
            //       UI shows a meaningful failure.
            throw UnsupportedOperationException(
                "OpenAI Music is in private preview; please use ElevenLabs Music, " +
                    "Suno, or Stable Audio cloud for now.",
            )
            @Suppress("UNREACHABLE_CODE")
            val body = buildJsonObject {
                put("prompt", request.prompt)
                put("model", "gpt-4o-audio-preview")
            }
            val req = Request.Builder()
                .url("$baseUrl/audio/generations")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw RuntimeException("OpenAI music ${resp.code}: ${err.take(200)}")
                }
                val bytes = resp.body?.bytes() ?: throw RuntimeException("OpenAI music: empty body")
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
