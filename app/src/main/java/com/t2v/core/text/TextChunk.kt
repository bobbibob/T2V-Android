package com.t2v.core.text

/**
 * Безопасный чанк текста для отправки в TTS.
 *
 * Поля один-в-один повторяют dataclass `TextChunk` из оригинального
 * `app/core/text_processor.py`. Разметочные паузы/состояния заполняются
 * парсером LTV-разметки (см. [com.t2v.core.markup.MarkupState]).
 */
data class TextChunk(
    val text: String,
    val endsParagraph: Boolean,
    val paragraphLength: Int = 0,
    val paragraphNumber: Int = 0,
    val markupPauseBeforeMs: Int = 0,
    val markupPauseAfterMs: Int? = null,
    val markupState: com.t2v.core.markup.MarkupState = com.t2v.core.markup.MarkupState(),
    val markupAudioEvents: List<AudioEventRef> = emptyList(),
) {
    /** Примерная длительность TTS в секундах (по 16 символов/сек, как в оригинале). */
    val estimatedDurationSec: Double
        get() = text.length / 16.0
}
