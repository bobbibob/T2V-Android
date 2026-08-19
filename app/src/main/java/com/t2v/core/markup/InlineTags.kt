package com.t2v.core.markup

/**
 * Inline (paired) expression tags: `<whisper>text</whisper>`,
 * `<emotion sad>text</emotion>`, `<fast>text</fast>`.
 *
 * Unlike `{{...}}` commands which change state until `{{reset}}`,
 * inline tags apply only to the text they enclose. Nesting is supported:
 * `<whisper><emotion sad>I'm so tired</emotion></whisper>` applies both.
 *
 * Short aliases map to the same [MarkupCommand] types that `{{...}}`
 * produces, so downstream code (engines, pipeline, audio inserter) does
 * not need to change.
 *
 * Supported inline tags:
 *   Delivery:  <whisper> <shout> <soft> <loud> <narrator> <conversational>
 *   Emotion:   <emotion happy|sad|angry|...>  (closing </emotion> or </>)
 *   Speed:     <fast> <slow>  (or <speed 1.3>...</speed>)
 *   Volume:    <loud> <soft>  (or <volume 1.5>...</volume>)
 *   Pitch:     <pitch 1.2>...</pitch>
 *   Vocal cue: <breath> <laugh> <sigh> <gasp> <chuckle> <giggle> <cry>
 *              (self-closing or paired)
 *   Emphasis:  <emphasis strong|moderate|reduced>...</emphasis>
 *
 * Also supports self-closing: `<breath/>`, `<laugh/>`.
 */
object InlineTags {

    /**
     * Maps short tag names to the [MarkupCommand] they produce.
     * The command's offset is filled in by the parser.
     */
    private val DELIVERY_ALIASES = mapOf(
        "whisper" to "whisper",
        "shout" to "shout",
        "soft" to "soft",
        "loud" to "loud",
        "narrator" to "narrator",
        "conversational" to "conversational",
        "dramatic" to "dramatic",
        "hesitant" to "hesitant",
        "urgent" to "urgent",
        "secretive" to "secretive",
    )

    private val EMOTION_ALIASES = mapOf(
        "happy" to "happy",
        "sad" to "sad",
        "angry" to "angry",
        "afraid" to "afraid",
        "excited" to "excited",
        "calm" to "calm",
        "neutral" to "neutral",
        "serious" to "serious",
        "friendly" to "friendly",
        "hopeful" to "hopeful",
        "terrified" to "terrified",
        "empathetic" to "empathetic",
        "tired" to "tired",
        "surprised" to "surprised",
        "anxious" to "anxious",
    )

    private val SPEED_ALIASES = mapOf(
        "fast" to 1.3,
        "slow" to 0.7,
        "rapid" to 1.5,
        "languid" to 0.6,
    )

    private val VOLUME_ALIASES = mapOf(
        "quiet" to 0.5,
    )

    private val VOCAL_CUE_TAGS = setOf(
        "breath", "laugh", "sigh", "gasp", "chuckle", "giggle",
        "cry", "sob", "yawn", "cough", "clear_throat", "sniff", "pant", "hmm",
    )

    /** All tag names that can open an inline expression block. */
    val allOpenTags: Set<String> = buildSet {
        addAll(DELIVERY_ALIASES.keys)
        addAll(EMOTION_ALIASES.keys)
        addAll(SPEED_ALIASES.keys)
        addAll(VOLUME_ALIASES.keys)
        addAll(VOCAL_CUE_TAGS)
        addAll(listOf("emotion", "delivery", "speed", "volume", "pitch", "emphasis", "style"))
    }

    /**
     * Creates the open command for an inline tag.
     * Returns null if [tagName] is not a known inline tag.
     */
    fun openCommand(tagName: String, arg: String, offset: Int): MarkupCommand? {
        val name = tagName.lowercase()
        return when {
            // <emotion happy> or <emotion> → needs arg
            name == "emotion" -> MarkupCommand.Emotion(arg.ifBlank { "neutral" }, offset)
            name in EMOTION_ALIASES -> MarkupCommand.Emotion(EMOTION_ALIASES[name]!!, offset)

            // <delivery whisper> or <whisper>
            name == "delivery" || name == "style" -> MarkupCommand.Delivery(arg.ifBlank { "normal" }, offset)
            name in DELIVERY_ALIASES -> MarkupCommand.Delivery(DELIVERY_ALIASES[name]!!, offset)

            // <speed 1.3> or <fast>
            name == "speed" -> MarkupCommand.Speed(arg.toDoubleOrNull() ?: 1.0, offset)
            name in SPEED_ALIASES -> MarkupCommand.Speed(SPEED_ALIASES[name]!!, offset)

            // <volume 1.5> or <loud>
            name == "volume" -> MarkupCommand.Volume(arg.toDoubleOrNull() ?: 1.0, offset)
            name in VOLUME_ALIASES -> MarkupCommand.Volume(VOLUME_ALIASES[name]!!, offset)

            // <pitch 1.2>
            name == "pitch" -> MarkupCommand.Pitch(arg.toDoubleOrNull() ?: 1.0, offset)

            // <emphasis strong>
            name == "emphasis" -> MarkupCommand.Emphasis(arg.ifBlank { "moderate" }, offset)

            // Vocal cues — self-closing or paired (cue happens at open tag)
            name in VOCAL_CUE_TAGS -> MarkupCommand.VocalCue(name, arg, offset)

            else -> null
        }
    }

    /**
     * Creates the close (reset) command for an inline tag.
     * Returns null if [tagName] doesn't need a reset.
     */
    fun closeCommand(tagName: String, offset: Int): MarkupCommand? {
        val name = tagName.lowercase()
        return when {
            name == "emotion" || name in EMOTION_ALIASES ->
                MarkupCommand.Reset("emotion", offset)
            name == "delivery" || name == "style" || name in DELIVERY_ALIASES ->
                MarkupCommand.Reset("delivery", offset)
            name == "speed" || name in SPEED_ALIASES ->
                MarkupCommand.Reset("speed", offset)
            name == "volume" || name in VOLUME_ALIASES ->
                MarkupCommand.Reset("volume", offset)
            name == "pitch" ->
                MarkupCommand.Reset("pitch", offset)
            name == "emphasis" ->
                MarkupCommand.Reset("emphasis", offset)
            // Vocal cues don't need reset — they're momentary
            name in VOCAL_CUE_TAGS -> null
            else -> null
        }
    }

    /**
     * Pattern for opening inline tags.
     * Matches: <whisper>, <emotion sad>, <speed 1.3>, <breath/>
     * Does NOT match: <music>, <sfx> (handled separately)
     */
    val openPattern = Regex("""<(?!music|sfx|/)(\w+)(?:\s+([^/>]*))?\s*/?>""")

    /** Pattern for closing inline tags: </whisper>, </emotion>, </speed> */
    val closePattern = Regex("""</(\w+)\s*>""")
}