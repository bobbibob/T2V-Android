package com.t2v.tts.engines

import com.t2v.tts.EngineInfo
import com.t2v.tts.EngineInfo.EngineKind
import com.t2v.tts.ExpressiveSpeech
import com.t2v.tts.TtsEngineException
import com.t2v.tts.TtsRequest
import com.t2v.tts.VoiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * ElevenLabs Text-to-Speech.
 *
 * Прямой порт `app/tts/api_engines.py:ElevenLabsTTSEngine`.
 */
class ElevenLabsTtsEngine(
    private val apiKey: String,
    private val baseUrl: String = "https://api.elevenlabs.io/v1",
    private val defaultVoiceId: String = "21m00Tcm4TlvDq8ikWAM", // "Rachel"
    private val modelId: String = "eleven_multilingual_v2",
) : AbstractHttpEngine(ENGINE_INFO) {

    override fun endpoint(): String = "$baseUrl/text-to-speech/$defaultVoiceId?output_format=mp3_44100_128"

    override fun endpoint(request: TtsRequest): String {
        val voiceId = request.voice.voice.ifBlank { defaultVoiceId }
        return "$baseUrl/text-to-speech/$voiceId?output_format=mp3_44100_128"
    }

    override fun headers(): Map<String, String> = mapOf(
        "xi-api-key" to apiKey,
        "Accept" to "audio/mpeg",
        "Content-Type" to "application/json",
    )

    override fun buildBody(request: TtsRequest): JsonObject = buildJsonObject {
        val prefix = if (modelId == "eleven_v3") ExpressiveSpeech.elevenV3Prefix(request.voice) else ""
        put("text", listOf(prefix, request.text).filter { it.isNotBlank() }.joinToString(" "))
        put("model_id", modelId)
        put(
            "voice_settings",
            buildJsonObject {
                put("stability", 0.5)
                put("similarity_boost", 0.75)
                put("style", 0.0)
                put("use_speaker_boost", true)
            },
        )
        request.voice.lang.takeIf { it.isNotBlank() }?.let { put("language_code", it) }
    }

    override suspend fun listVoices(): List<VoiceInfo> = withContext(Dispatchers.IO) {
        val rq = Request.Builder()
            .url("$baseUrl/voices")
            .header("xi-api-key", apiKey)
            .get()
            .build()
        runCatching {
            httpClient.newCall(rq).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching emptyList()
                val text = resp.body?.string().orEmpty()
                val obj = json.parseToJsonElement(text).let { it as? JsonObject } ?: return@runCatching emptyList()
                val arr = obj["voices"] as? JsonArray ?: return@runCatching emptyList()
                arr.mapNotNull { el ->
                    val v = el as? JsonObject ?: return@mapNotNull null
                    VoiceInfo(
                        id = v["voice_id"]?.toString()?.trim('"') ?: return@mapNotNull null,
                        displayName = v["name"]?.toString()?.trim('"').orEmpty(),
                        language = "multi",
                        engineId = ENGINE_INFO.id,
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun cloneVoice(name: String, audioFile: File, mimeType: String): String =
        withContext(Dispatchers.IO) {
            require(name.isNotBlank()) { "Voice name is required" }
            require(audioFile.isFile && audioFile.length() > 0) { "Voice recording is empty" }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("name", name.trim())
                .addFormDataPart("description", "Created in T2V with the speaker's consent")
                .addFormDataPart("labels", """{"language":"ru"}""")
                .addFormDataPart(
                    "files",
                    audioFile.name,
                    audioFile.asRequestBody(mimeType.toMediaType()),
                )
                .build()
            val request = Request.Builder()
                .url("$baseUrl/voices/add")
                .header("xi-api-key", apiKey)
                .post(body)
                .build()
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw TtsEngineException.Api(response.code, text.take(500))
                }
                val result = json.parseToJsonElement(text) as? JsonObject
                    ?: throw TtsEngineException.Generic("Invalid cloning response")
                result["voice_id"]?.toString()?.trim('"')
                    ?: throw TtsEngineException.Generic("Voice ID is missing")
            }
        }

    companion object {
        val ENGINE_INFO = EngineInfo(
            id = "elevenlabs",
            displayName = "ElevenLabs",
            kind = EngineKind.Cloud,
            requiresApiKey = true,
            supportsCloning = true,
            configSchema = listOf(
                EngineInfo.ConfigField("apiKey", "API Key", EngineInfo.ConfigField.FieldType.Password, secret = true),
                EngineInfo.ConfigField("voiceId", "Voice ID", EngineInfo.ConfigField.FieldType.String, default = "21m00Tcm4TlvDq8ikWAM"),
                EngineInfo.ConfigField("modelId", "Model", EngineInfo.ConfigField.FieldType.Select, default = "eleven_multilingual_v2", options = listOf("eleven_multilingual_v2", "eleven_turbo_v2_5", "eleven_flash_v2_5")),
            ),
        )
    }
}
