package com.t2v.core.markup

/**
 * Состояние разметки, "приклеиваемое" к каждому TextChunk.
 *
 * Прямой порт `markup_state: dict` из `app/core/ltv_markup.py` (1 378 строк).
 * Поля названы в `camelCase`, но семантика 1:1.
 */
data class MarkupState(
    val voice: String? = null,
    val language: String? = null,
    val speed: Double? = null,
    val volume: Double? = null,         // линейный множитель, 1.0 = 100%
    val pitch: Double? = null,
    val emotion: String? = null,
    val delivery: String? = null,
    val emphasis: String? = null,
    /** One-shot cues consumed by the next spoken span. */
    val vocalCues: List<String> = emptyList(),
    val chapter: String? = null,
    val custom: Map<String, String> = emptyMap(),
) {
    fun merge(other: MarkupState): MarkupState = MarkupState(
        voice = other.voice ?: voice,
        language = other.language ?: language,
        speed = other.speed ?: speed,
        volume = other.volume ?: volume,
        pitch = other.pitch ?: pitch,
        emotion = other.emotion ?: emotion,
        delivery = other.delivery ?: delivery,
        emphasis = other.emphasis ?: emphasis,
        vocalCues = vocalCues + other.vocalCues,
        chapter = other.chapter ?: chapter,
        custom = custom + other.custom,
    )
}
