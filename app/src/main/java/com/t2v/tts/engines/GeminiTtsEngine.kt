package com.t2v.tts.engines

import com.t2v.tts.EngineInfo
import com.t2v.tts.EngineInfo.EngineKind
import com.t2v.tts.ExpressiveSpeech
import com.t2v.tts.TtsRequest
import com.t2v.tts.VoiceInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Google Gemini TTS (text-to-speech через generateContent).
 *
 * Прямой порт `app/tts/api_engines.py:GeminiTTSEngine`.
 *
 * Используется endpoint:
 *   POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
 *
 * Тело запроса содержит `contents[].parts[].text` и `generationConfig.responseModalities=["AUDIO"]`
 * + `speechConfig.voiceConfig`.
 */
class GeminiTtsEngine(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash-preview-tts",
    private val voiceName: String = "Kore",
) : AbstractHttpEngine(ENGINE_INFO) {

    override fun endpoint(): String =
        "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    override fun headers(): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
    )

    override fun buildBody(request: TtsRequest): JsonObject = buildJsonObject {
        put("contents", kotlinx.serialization.json.buildJsonArray {
            add(kotlinx.serialization.json.buildJsonObject {
                put("parts", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.buildJsonObject {
                        val direction = ExpressiveSpeech.instruction(request.voice)
                        put(
                            "text",
                            if (direction == null) request.text
                            else "$direction.\nRecite exactly this text:\n${request.text}",
                        )
                    })
                })
            })
        })
        put("generationConfig", kotlinx.serialization.json.buildJsonObject {
            put("responseModalities", kotlinx.serialization.json.buildJsonArray { add("AUDIO") })
            put("speechConfig", kotlinx.serialization.json.buildJsonObject {
                put("voiceConfig", kotlinx.serialization.json.buildJsonObject {
                    put("prebuiltVoiceConfig", kotlinx.serialization.json.buildJsonObject {
                        put("voiceName", request.voice.extras["voiceName"] ?: voiceName)
                    })
                })
            })
        })
    }

    override suspend fun listVoices(): List<VoiceInfo> = listOf(
        "Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Perchiron"
    ).map { name ->
        VoiceInfo(id = name, displayName = name, language = "multi", engineId = ENGINE_INFO.id)
    }

    companion object {
        val ENGINE_INFO = EngineInfo(
            id = "gemini",
            displayName = "Google Gemini TTS",
            kind = EngineKind.Cloud,
            requiresApiKey = true,
            configSchema = listOf(
                EngineInfo.ConfigField("apiKey", "API Key", EngineInfo.ConfigField.FieldType.Password, secret = true),
                EngineInfo.ConfigField("model", "Model", EngineInfo.ConfigField.FieldType.String, default = "gemini-2.5-flash-preview-tts"),
                EngineInfo.ConfigField("voiceName", "Voice", EngineInfo.ConfigField.FieldType.Select, default = "Kore", options = listOf("Kore", "Puck", "Charon", "Fenrir", "Aoede", "Leda", "Orus", "Perchiron")),
            ),
        )
    }
}
