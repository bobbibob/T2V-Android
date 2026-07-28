package com.t2v.tts.engines

import com.t2v.tts.EngineInfo
import com.t2v.tts.EngineInfo.EngineKind
import com.t2v.tts.ExpressiveSpeech
import com.t2v.tts.TtsRequest
import com.t2v.tts.VoiceInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OpenAI TTS API. Полностью совместим с совместимыми провайдерами
 * (Groq, OpenRouter и т.д.) — достаточно передать baseUrl через
 * конфиг или переопределить [endpoint].
 *
 * Прямой порт `app/tts/api_engines.py:OpenAITTSEngine`.
 */
class OpenAiTtsEngine(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    private val defaultModel: String = "gpt-4o-mini-tts",
    private val defaultVoice: String = "alloy",
) : AbstractHttpEngine(ENGINE_INFO) {

    override fun endpoint(): String = "$baseUrl/audio/speech"

    override fun headers(): Map<String, String> = mapOf(
        "Authorization" to "Bearer $apiKey",
        "Accept" to "audio/wav",
    )

    override fun buildBody(request: TtsRequest): JsonObject = buildJsonObject {
        put("model", defaultModel)
        put("input", request.text)
        put("voice", request.voice.voice.ifEmpty { defaultVoice })
        put("response_format", "wav")
        put("speed", request.voice.speed.coerceIn(0.25, 4.0))
        ExpressiveSpeech.instruction(request.voice)?.let { put("instructions", it) }
    }

    override suspend fun listVoices(): List<VoiceInfo> = listOf(
        "alloy", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse"
    ).map { id ->
        VoiceInfo(
            id = id,
            displayName = id.replaceFirstChar { it.uppercase() },
            language = "en",
            engineId = ENGINE_INFO.id,
        )
    }

    companion object {
        val ENGINE_INFO = EngineInfo(
            id = "openai",
            displayName = "OpenAI TTS",
            kind = EngineKind.Cloud,
            requiresApiKey = true,
            configSchema = listOf(
                EngineInfo.ConfigField("apiKey", "API Key", EngineInfo.ConfigField.FieldType.Password, secret = true),
                EngineInfo.ConfigField("baseUrl", "Base URL", EngineInfo.ConfigField.FieldType.String, default = "https://api.openai.com/v1"),
                EngineInfo.ConfigField("model", "Model", EngineInfo.ConfigField.FieldType.String, default = "gpt-4o-mini-tts"),
            ),
        )
    }
}
