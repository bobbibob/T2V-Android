package com.t2v.core.text

/**
 * Раздел текста с заголовком (глава, урок, модуль).
 *
 * Прямой порт [TextSection] из app/core/text_processor.py.
 */
data class TextSection(
    val title: String,
    val text: String,
)
