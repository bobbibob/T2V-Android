package com.t2v.llm

/**
 * Заглушка для LLM-пайплайнов из оригинала (`app/llm/base.py`).
 *
 * В оригинале используется litellm. На Android это не имеет смысла
 * (запросы через интернет не дают offline-преимущества).
 * LLM-функции не входят в приложение.
 *
 * Этот stub сохранён для совместимости импортов и для будущей
 * реализации on-device LLM (например, через MediaPipe LLM).
 */
object LLMStub {
    fun isAvailable(): Boolean = false
    suspend fun rewrite(text: String, style: String = "neutral"): String = text
}
