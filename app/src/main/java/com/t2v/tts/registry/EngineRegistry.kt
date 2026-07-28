package com.t2v.tts.registry

import android.content.Context
import com.t2v.tts.EngineInfo
import com.t2v.tts.TtsEngineException
import com.t2v.tts.VoiceConfig
import com.t2v.tts.VoiceInfo
import com.t2v.tts.engines.AzureTtsEngine
import com.t2v.tts.engines.CustomHttpTtsEngine
import com.t2v.tts.engines.ElevenLabsTtsEngine
import com.t2v.tts.engines.GeminiTtsEngine
import com.t2v.tts.engines.KokoroTtsEngine
import com.t2v.tts.engines.OpenAiTtsEngine
import com.t2v.tts.engines.PiperRussianTtsEngine
import com.t2v.tts.engines.TtsEngine
import java.io.File

/**
 * Реестр TTS-движков. Прямой порт `app/tts/engine_registry.py`.
 *
 * Хранит лениво создаваемые экземпляры движков + их конфигурацию.
 * Используется пайплайном и UI.
 */
class EngineRegistry(
    private val appContext: Context,
    private val settingsProvider: () -> EngineSettings,
) {

    /** Настройки конкретного движка (API-ключи и т.п.). */
    data class EngineSettings(
        val engines: Map<String, Map<String, String>> = emptyMap(),
    )

    private val instances = mutableMapOf<String, TtsEngine>()

    /** Список движков, выполняемых на устройстве или через облачный API. */
    fun allEngineInfos(): List<EngineInfo> = buildList {
        val kokoroRoot = File(appContext.filesDir, "models/$KOKORO_DIRECTORY")
        if (
            File(kokoroRoot, "model.onnx").isFile &&
            File(kokoroRoot, "voices.bin").isFile &&
            File(kokoroRoot, "tokens.txt").isFile &&
            File(kokoroRoot, "espeak-ng-data").isDirectory
        ) {
            add(KokoroTtsEngine.ENGINE_INFO)
        }
        val russianRoot = File(appContext.filesDir, "models/piper-ru")
        if (PiperRussianTtsEngine(russianRoot).isAvailable()) {
            add(PiperRussianTtsEngine.ENGINE_INFO)
        }
        add(OpenAiTtsEngine.ENGINE_INFO)
        add(ElevenLabsTtsEngine.ENGINE_INFO)
        add(GeminiTtsEngine.ENGINE_INFO)
        add(AzureTtsEngine.ENGINE_INFO)
        add(CustomHttpTtsEngine.ENGINE_INFO)
    }

    /** Получить или создать экземпляр движка по id. */
    @Synchronized
    fun get(engineId: String): TtsEngine {
        instances[engineId]?.let { return it }
        val created = createEngine(engineId)
            ?: throw TtsEngineException.Generic("Unknown engine: $engineId")
        instances[engineId] = created
        return created
    }

    /** Освободить все движки. */
    suspend fun closeAll() {
        for (e in instances.values) e.close()
        instances.clear()
    }

    private fun createEngine(id: String): TtsEngine? {
        val cfg = settingsProvider().engines[id] ?: emptyMap()
        return when (id) {
            "kokoro" -> {
                KokoroTtsEngine(File(appContext.filesDir, "models/$KOKORO_DIRECTORY"))
            }
            "piper_ru" -> PiperRussianTtsEngine(File(appContext.filesDir, "models/piper-ru"))
            "openai" -> OpenAiTtsEngine(
                apiKey = cfg["apiKey"]?.takeIf { it.isNotBlank() } ?: return null,
                baseUrl = cfg["baseUrl"] ?: "https://api.openai.com/v1",
                defaultModel = cfg["model"] ?: "gpt-4o-mini-tts",
            )
            "elevenlabs" -> ElevenLabsTtsEngine(
                apiKey = cfg["apiKey"]?.takeIf { it.isNotBlank() } ?: return null,
                baseUrl = cfg["baseUrl"] ?: "https://api.elevenlabs.io/v1",
                defaultVoiceId = cfg["voiceId"] ?: "21m00Tcm4TlvDq8ikWAM",
                modelId = cfg["modelId"] ?: "eleven_multilingual_v2",
            )
            "gemini" -> GeminiTtsEngine(
                apiKey = cfg["apiKey"]?.takeIf { it.isNotBlank() } ?: return null,
                model = cfg["model"] ?: "gemini-2.5-flash-preview-tts",
                voiceName = cfg["voiceName"] ?: "Kore",
            )
            "azure" -> AzureTtsEngine(
                subscriptionKey = cfg["subscriptionKey"]?.takeIf { it.isNotBlank() } ?: return null,
                region = cfg["region"]?.takeIf { it.isNotBlank() } ?: return null,
                defaultVoice = cfg["voice"] ?: "en-US-JennyNeural",
            )
            "custom_http" -> CustomHttpTtsEngine(
                CustomHttpTtsEngine.Config(
                    url = cfg["url"]?.takeIf { it.isNotBlank() } ?: return null,
                    headers = parseHeaders(cfg["headers"] ?: ""),
                    bodyTemplate = cfg["bodyTemplate"]
                        ?: """{"text": "{{text}}", "voice": "{{voice}}", "lang": "{{lang}}"}""",
                    responseAudioField = cfg["responseAudioField"]?.ifEmpty { "audio" },
                    responseUrlField = cfg["responseUrlField"]?.ifEmpty { null },
                ),
            )
            else -> null
        }
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = mutableMapOf<String, String>()
        for (line in raw.lines()) {
            val idx = line.indexOf(':')
            if (idx > 0) {
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                if (key.isNotEmpty()) out[key] = value
            }
        }
        return out
    }

    companion object {
        private const val KOKORO_DIRECTORY = "8ae649d98c616269e26efb10"
    }
}
