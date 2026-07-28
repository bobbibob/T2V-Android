package com.t2v.tts

import java.io.File

/**
 * Запрос на синтез одного фрагмента текста.
 * Соответствует `synthesize_to_wav(text, output_wav, voice_config)` из оригинала.
 */
data class TtsRequest(
    val text: String,
    val outputFile: File,
    val voice: VoiceConfig,
    /** Доп. контекст, например SSML. */
    val ssml: String? = null,
    /** Таймаут в мс (0 = без таймаута). */
    val timeoutMs: Long = 60_000,
)

/** Результат синтеза. */
data class TtsResult(
    val outputFile: File,
    val sampleRate: Int,
    val channels: Int,
    val durationMs: Int,
    val bytesWritten: Long,
)

/** Исключение движка. Прямой порт TTSEngineError / TTSCancelled. */
sealed class TtsEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class Generic(message: String, cause: Throwable? = null) : TtsEngineException(message, cause)
    class Cancelled(message: String = "Generation cancelled") : TtsEngineException(message)
    class NotInstalled(val engineId: String) : TtsEngineException("Engine $engineId is not installed")
    class InvalidConfig(message: String) : TtsEngineException(message)
    class Network(message: String, cause: Throwable? = null) : TtsEngineException(message, cause)
    class Api(val statusCode: Int, message: String) : TtsEngineException("API $statusCode: $message")
}
