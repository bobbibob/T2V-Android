package com.t2v.tts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Голос — общий контракт для всех движков.
 * Прямой порт `voice_config: dict` из оригинала.
 */
@Serializable
data class VoiceConfig(
    /** Имя/ID голоса в конкретном движке. */
    val voice: String = "",
    /** Язык (BCP-47): en, ru, es, fr, de... */
    val lang: String = "",
    /** Скорость речи (1.0 = нормальная). */
    val speed: Double = 1.0,
    /** Громкость, линейный множитель 0..1. */
    val volume: Double = 1.0,
    /** Pitch (1.0 = нормальный). */
    val pitch: Double = 1.0,
    /** Эмоциональная окраска (для движков, которые поддерживают). */
    val emotion: String? = null,
    /** Ссылочное аудио для клонирования (опционально). */
    val referenceAudioPath: String? = null,
    /** Доп. параметры движка, не покрытые общими полями. */
    val extras: Map<String, String> = emptyMap(),
) {
    companion object {
        val EMPTY = VoiceConfig()
    }
}

/** Каталог голосов — то, что движок отдаёт "на витрину". */
@Serializable
data class VoiceInfo(
    val id: String,
    val displayName: String,
    val language: String,
    val gender: String = "",
    val engineId: String = "",
    val previewUrl: String? = null,
    val isLocal: Boolean = false,
    val sampleRate: Int = 22050,
    val tags: List<String> = emptyList(),
    val downloadModelId: String? = null,
    val downloadSizeBytes: Long = -1,
    val isCloned: Boolean = false,
)

/**
 * Описание TTS-движка. Используется в реестре.
 */
@Serializable
data class EngineInfo(
    val id: String,
    val displayName: String,
    val kind: EngineKind,
    val supportsCloning: Boolean = false,
    val supportsLocal: Boolean = false,
    val requiresApiKey: Boolean = false,
    val configSchema: List<ConfigField> = emptyList(),
) {
    @Serializable
    enum class EngineKind {
        @SerialName("local") Local,
        @SerialName("cloud") Cloud,
    }

    @Serializable
    data class ConfigField(
        val key: String,
        val label: String,
        val type: FieldType,
        val default: String = "",
        val secret: Boolean = false,
        val options: List<String> = emptyList(),
        val help: String = "",
    ) {
        @Serializable
        enum class FieldType { @SerialName("string") String, @SerialName("int") Int, @SerialName("double") Double, @SerialName("bool") Bool, @SerialName("select") Select, @SerialName("password") Password }
    }
}
