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

class ElevenLabsSoundEffectsGenerator(
    private val apiKey: String,
    private val baseUrl: String = "https://api.elevenlabs.io/v1",
) : Generator {

    override val id: String = "elevenlabs.sound"
    override val displayName: String = "ElevenLabs Sound Effects"
    override val category: GeneratorCategory = GeneratorCategory.Sound

    override fun isAvailable(): Boolean = apiKey.isNotBlank()

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun generate(request: GeneratorRequest): GeneratorResult = withContext(Dispatchers.IO) {
        require(request.prompt.isNotBlank()) { "Sound prompt must not be blank" }
        val body = buildJsonObject {
            put("text", request.prompt)
            if (request.durationSeconds in 1..22) put("duration_seconds", request.durationSeconds)
            put("prompt_influence", 0.3)
        }
        val req = Request.Builder()
            .url("$baseUrl/sound-generation")
            .header("xi-api-key", apiKey)
            .header("Accept", "audio/mpeg")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val err = resp.body?.string().orEmpty()
                throw RuntimeException("ElevenLabs sound ${resp.code}: ${err.take(200)}")
            }
            val bytes = resp.body?.bytes() ?: throw RuntimeException("ElevenLabs sound: empty body")
            request.outputFile.parentFile?.mkdirs()
            request.outputFile.writeBytes(bytes)
            GeneratorResult(
                outputFile = request.outputFile,
                sampleRate = 44100,
                channels = 1,
                durationMs = -1,
                bytesWritten = bytes.size.toLong(),
            )
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
