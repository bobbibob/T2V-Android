package com.t2v.core.markup

import com.t2v.core.model.GenerationModelCatalog

/**
 * Registry of all LTV markup tags, their categories, default snippets and
 * per-engine support status.
 *
 * Used by [com.t2v.ui.components.MarkupToolbar] to show only the tags the
 * selected engine actually supports — and mark partial ones differently.
 *
 * The support mapping mirrors [GenerationModelCatalog.TagDocs] so the
 * toolbar and the TagDocs info dialog stay in sync.
 */
object MarkupTagRegistry {

    enum class SupportLevel {
        /** The engine natively handles this tag. */
        SUPPORTED,
        /** The engine approximates this tag (e.g. emotion → speed/pitch). */
        PARTIAL,
        /** The engine ignores this tag. */
        IGNORED,
    }

    data class Tag(
        val key: String,
        val label: String,
        val category: Category,
        val snippet: String,
        val description: String,
        /** True if this tag wraps selected text (paired open/close). */
        val isInline: Boolean = false,
        /** Open tag for inline wrapping, e.g. "<whisper>". */
        val openTag: String = "",
        /** Close tag for inline wrapping, e.g. "</whisper>". */
        val closeTag: String = "",
    )

    enum class Category {
        VOICE,        // {{voice "..."}}
        LANGUAGE,     // {{lang ...}}
        SPEED,        // {{speed ...}}
        VOLUME,       // {{volume ...}}
        PITCH,        // {{pitch ...}}
        EMOTION,      // {{emotion ...}}
        DELIVERY,     // {{delivery ...}}
        EMPHASIS,     // {{emphasis ...}}
        VOCAL_CUE,    // {{breath}} {{laugh}} {{sigh}} {{gasp}}
        PAUSE,        // {{pause ...}}
        CHAPTER,      // {{chapter "..."}}
        RESET,        // {{reset ...}}
        MUSIC,        // <music>...</music>
        SFX,          // <sfx>...</sfx>
    }

    /** All tags T2V knows, in display order. */
    val allTags: List<Tag> = listOf(
        // ── Inline expression tags (wrap selected text) ──
        Tag("whisper", "Шёпот", Category.DELIVERY, "{{delivery whisper}}",
            "Шёпот — только для выделенного текста",
            isInline = true, openTag = "<whisper>", closeTag = "</whisper>"),
        Tag("shout", "Крик", Category.DELIVERY, "{{delivery shout}}",
            "Крик — только для выделенного текста",
            isInline = true, openTag = "<shout>", closeTag = "</shout>"),
        Tag("soft", "Мягко", Category.DELIVERY, "{{delivery soft}}",
            "Мягкая подача",
            isInline = true, openTag = "<soft>", closeTag = "</soft>"),
        Tag("loud", "Громко", Category.DELIVERY, "{{delivery loud}}",
            "Громкая подача",
            isInline = true, openTag = "<loud>", closeTag = "</loud>"),
        Tag("narrator", "Повествователь", Category.DELIVERY, "{{delivery narrator}}",
            "Повествовательский стиль",
            isInline = true, openTag = "<narrator>", closeTag = "</narrator>"),
        Tag("happy", "Радость", Category.EMOTION, "{{emotion happy}}",
            "Радостная интонация",
            isInline = true, openTag = "<happy>", closeTag = "</happy>"),
        Tag("sad", "Грусть", Category.EMOTION, "{{emotion sad}}",
            "Грустная интонация",
            isInline = true, openTag = "<sad>", closeTag = "</sad>"),
        Tag("angry", "Злость", Category.EMOTION, "{{emotion angry}}",
            "Злая интонация",
            isInline = true, openTag = "<angry>", closeTag = "</angry>"),
        Tag("excited", "Восторг", Category.EMOTION, "{{emotion excited}}",
            "Восторженная интонация",
            isInline = true, openTag = "<excited>", closeTag = "</excited>"),
        Tag("calm", "Спокойствие", Category.EMOTION, "{{emotion calm}}",
            "Спокойная интонация",
            isInline = true, openTag = "<calm>", closeTag = "</calm>"),
        Tag("afraid", "Страх", Category.EMOTION, "{{emotion afraid}}",
            "Испуганная интонация",
            isInline = true, openTag = "<afraid>", closeTag = "</afraid>"),
        Tag("tired", "Усталость", Category.EMOTION, "{{emotion tired}}",
            "Уставшая интонация",
            isInline = true, openTag = "<tired>", closeTag = "</tired>"),
        Tag("surprised", "Удивление", Category.EMOTION, "{{emotion surprised}}",
            "Удивлённая интонация",
            isInline = true, openTag = "<surprised>", closeTag = "</surprised>"),
        Tag("fast", "Быстро", Category.SPEED, "{{speed 1.3}}",
            "Ускоренный темп",
            isInline = true, openTag = "<fast>", closeTag = "</fast>"),
        Tag("slow", "Медленно", Category.SPEED, "{{speed 0.7}}",
            "Замедленный темп",
            isInline = true, openTag = "<slow>", closeTag = "</slow>"),
        Tag("emphasis", "Акцент", Category.EMPHASIS, "{{emphasis strong}}",
            "Сила акцента: reduced, moderate, strong",
            isInline = true, openTag = "<emphasis strong>", closeTag = "</emphasis>"),

        // ── Vocal cues (momentary, self-closing or paired) ──
        Tag("breath", "Вдох", Category.VOCAL_CUE, "{{breath}}",
            "Вокальная реакция: вздох/вдох",
            isInline = true, openTag = "<breath/>", closeTag = ""),
        Tag("laugh", "Смех", Category.VOCAL_CUE, "{{laugh}}",
            "Вокальная реакция: смех",
            isInline = true, openTag = "<laugh/>", closeTag = ""),
        Tag("sigh", "Выдох", Category.VOCAL_CUE, "{{sigh}}",
            "Вокальная реакция: выдох",
            isInline = true, openTag = "<sigh/>", closeTag = ""),
        Tag("gasp", "Вздох", Category.VOCAL_CUE, "{{gasp}}",
            "Вокальная реакция: испуганный вздох",
            isInline = true, openTag = "<gasp/>", closeTag = ""),

        // ── Structural tags (insert at cursor) ──
        Tag("voice", "Голос", Category.VOICE, """{{voice "Alice"}}""",
            "Переключение голоса диктора"),
        Tag("lang", "Язык", Category.LANGUAGE, "{{lang en-US}}",
            "Язык синтеза (BCP-47)"),
        Tag("speed", "Скорость", Category.SPEED, "{{speed 0.9}}",
            "Множитель скорости (0.5–2.0)"),
        Tag("volume", "Громкость", Category.VOLUME, "{{volume 80%}}",
            "Громкость (0–4)"),
        Tag("pitch", "Высота", Category.PITCH, "{{pitch 1.1}}",
            "Сдвиг высоты тона (0.5–2.0)"),
        Tag("emotion", "Эмоция", Category.EMOTION, "{{emotion happy}}",
            "Эмоция: happy, sad, angry, afraid, excited, calm, neutral, serious, friendly, hopeful, terrified, empathetic, tired, surprised, anxious"),
        Tag("delivery", "Подача", Category.DELIVERY, "{{delivery whisper}}",
            "Подача: whisper, shout, soft, loud, slow, fast, narrator, conversational, dramatic, hesitant, urgent, normal, secretive"),
        Tag("pause", "Пауза", Category.PAUSE, "{{pause 500ms}}",
            "Пауза: {{pause 700ms}}, {{pause 0.7s}}, {{pause.short}}, {{pause.long}}"),
        Tag("chapter", "Глава", Category.CHAPTER, """{{chapter "Название"}}""",
            "Маркер главы на шкале проекта"),
        Tag("reset", "Сброс", Category.RESET, "{{reset emotion}}",
            "Сброс: emotion, delivery, emphasis, speed, pitch, volume, all"),
        Tag("music", "Музыка", Category.MUSIC, "<music>тёплый эмбиент, 80 BPM, 10 сек</music>",
            "Генерация музыки на_device или cloud"),
        Tag("sfx", "Звук", Category.SFX, "<sfx>дверь закрывается</sfx>",
            "Генерация звукового эффекта"),
    )

    /** Tags for the music/sound generators, keyed by generator id. */
    val generatorTags: List<Tag> = listOf(
        Tag("duration", "Длительность", Category.MUSIC, "{{duration 10}}",
            "Длительность генерации в секундах"),
    )

    /**
     * Returns the support level of [tagKey] for the given [engineId].
     *
     * Uses [GenerationModelCatalog.TagDocs] to determine which tags are
     * supported, partial or ignored for each engine/model.
     */
    fun supportLevel(tagKey: String, engineId: String): SupportLevel {
        val tagDocs = GenerationModelCatalog.tagDocsForEngine(engineId)
            ?: GenerationModelCatalog.tagDocsFor(engineId)
            ?: GenerationModelCatalog.tagDocsForGenerator(engineId)
            ?: return if (tagKey in DEFAULT_SUPPORTED) SupportLevel.SUPPORTED else SupportLevel.IGNORED

        val supported = tagDocs.supported.map { extractTagKey(it) }
        val partial = tagDocs.partial.map { extractTagKey(it) }
        val ignored = tagDocs.ignored.map { extractTagKey(it) }

        return when {
            tagKey in supported -> SupportLevel.SUPPORTED
            tagKey in partial -> SupportLevel.PARTIAL
            tagKey in ignored -> SupportLevel.IGNORED
            // Tags not mentioned in TagDocs are treated as supported by default
            // (e.g. pause, chapter — universal structural tags)
            tagKey in DEFAULT_SUPPORTED -> SupportLevel.SUPPORTED
            else -> SupportLevel.IGNORED
        }
    }

    /**
     * Extracts the tag key from a TagDocs description string.
     * e.g. "{{emotion happy|sad|...}}" → "emotion"
     *      "<music>...</music>" → "music"
     *      "Свободный промпт" → "prompt" (not a tag)
     */
    private fun extractTagKey(desc: String): String {
        // {{voice "..."}} → voice
        val doubleBrace = Regex("""\{\{(\w+)""").find(desc)?.groupValues?.get(1)
        if (doubleBrace != null) return doubleBrace
        // <music> → music
        val xmlTag = Regex("""<(\w+)""").find(desc)?.groupValues?.get(1)
        if (xmlTag != null) return xmlTag
        // "Свободный промпт: ..." → not a tag
        return desc.substringBefore(' ').substringBefore(':').lowercase()
    }

    /** Tags that work on every engine (structural tags). */
    private val DEFAULT_SUPPORTED = setOf(
        "pause", "chapter", "voice", "lang", "speed", "volume",
    )

    /**
     * Returns the list of tags visible in the toolbar for [engineId],
     * sorted by category then by display order. Tags marked IGNORED
     * are hidden entirely.
     */
    fun visibleTags(engineId: String): List<Tag> = allTags.filter { tag ->
        supportLevel(tag.key, engineId) != SupportLevel.IGNORED
    }
}