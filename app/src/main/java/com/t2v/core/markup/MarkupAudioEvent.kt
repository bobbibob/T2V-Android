package com.t2v.core.markup

/**
 * Аудио-событие, описанное в LTV-разметке.
 * Соответствует `MarkupAudioEvent` из оригинального парсера.
 */
sealed interface MarkupAudioEvent {
    /** {{sfx "laugh" 0ms}} */
    data class Sfx(
        val name: String,
        val offsetMs: Int = 0,
        val volume: Double = 1.0,
    ) : MarkupAudioEvent

    /** {{music "calm" -2.0dB}} */
    data class Music(
        val name: String,
        val volumeDb: Double = -3.0,
        val fadeInMs: Int = 0,
        val fadeOutMs: Int = 0,
    ) : MarkupAudioEvent

    /** {{silence 500ms}} — то же, что и {{pause}}, но для семантики. */
    data class Silence(
        val durationMs: Int,
    ) : MarkupAudioEvent
}
