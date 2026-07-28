package com.t2v.generators.impl

import com.t2v.generators.Generator
import com.t2v.generators.GeneratorCategory
import com.t2v.generators.GeneratorRequest
import com.t2v.generators.GeneratorResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * ElevenLabs Music API (Lyria-2).
 *
 * POST /v1/music — генерация композиции по текстовому промпту.
 * Возвращает аудио (mp3) в response body. До 4 минут на композицию.
 * Поддерживает instrumental/vocal, разные секции (intro, verse, chorus).
 *
 * Документация: https://elevenlabs.io/docs/api-reference/music
 *
 * **Status: scaffold.** Реальный вызов будет активирован, когда владелец
 * введёт ElevenLabs API-ключ в Settings.
 */
class ElevenLabsMusicGenerator(
    private val apiKey: String,
    private val baseUrl: String = "https://api.elevenlabs.io/v1",
) : Generator {
    override val id: String = "elevenlabs.music"
    override val displayName: String = "ElevenLabs Music (cloud)"
    override val category: GeneratorCategory = GeneratorCategory.Music

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(request: GeneratorRequest): GeneratorResult =
        withContext(Dispatchers.IO) {
            require(request.prompt.isNotBlank()) { "Music prompt must not be blank" }
            // TODO: parse the prompt for {duration N}, {instrumental}, {section}
            //       tags before serialising. For now we pass them through verbatim.
            val body = buildJsonObject {
                put("prompt", request.prompt)
                if (request.durationSeconds in 10..240) {
                    put("music_length_ms", request.durationSeconds * 1000)
                }
            }
            val req = Request.Builder()
                .url("$baseUrl/music")
                .header("xi-api-key", apiKey)
                .header("Accept", "audio/mpeg")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw RuntimeException("ElevenLabs music ${resp.code}: ${err.take(200)}")
                }
                val bytes = resp.body?.bytes() ?: throw RuntimeException("ElevenLabs music: empty body")
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
