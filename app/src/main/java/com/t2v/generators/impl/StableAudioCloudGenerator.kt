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
 * Stability AI Stable Audio 2.0 (cloud).
 *
 * Один и тот же эндпоинт работает для music и SFX; модель выбирается
 * параметром `model`. Здесь мы регистрируем генератор в обеих категориях.
 *
 * Документация: https://platform.stability.ai/docs/api-reference#tag/v2betaaudio
 *
 * **Status: scaffold.** Реальный вызов появится, когда владелец введёт
 * Stability API-ключ в Settings.
 */
class StableAudioCloudGenerator(
    private val apiKey: String,
    private val baseUrl: String = "https://api.stability.ai/v2beta/audio",
) : Generator {
    override val id: String = "stable-audio.cloud"
    override val displayName: String = "Stable Audio (Stability, cloud)"
    /**
     * One physical engine, two logical categories. ModelsScreen routes
     * <music> prompts to instances where the user picked music; <sfx> prompts
     * to instances where they picked SFX. Same id, same instance.
     */
    override val category: GeneratorCategory = GeneratorCategory.Music

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(request: GeneratorRequest): GeneratorResult =
        withContext(Dispatchers.IO) {
            require(request.prompt.isNotBlank()) { "Audio prompt must not be blank" }
            val body = buildJsonObject {
                put("prompt", request.prompt)
                put("model", "stable-audio-2")
                if (request.durationSeconds in 1..180) {
                    put("duration", request.durationSeconds)
                }
            }
            val req = Request.Builder()
                .url("$baseUrl/stable-audio-2/text-to-audio")
                .header("Authorization", "Bearer $apiKey")
                .header("Accept", "audio/mpeg")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val err = resp.body?.string().orEmpty()
                    throw RuntimeException("Stable Audio ${resp.code}: ${err.take(200)}")
                }
                val bytes = resp.body?.bytes() ?: throw RuntimeException("Stable Audio: empty body")
                request.outputFile.parentFile?.mkdirs()
                request.outputFile.writeBytes(bytes)
                GeneratorResult(
                    outputFile = request.outputFile,
                    sampleRate = 44100,
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
