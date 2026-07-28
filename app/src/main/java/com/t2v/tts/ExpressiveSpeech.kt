package com.t2v.tts

object ExpressiveSpeech {
    const val DELIVERY = "t2v.delivery"
    const val EMPHASIS = "t2v.emphasis"
    const val VOCAL_CUES = "t2v.vocalCues"

    fun instruction(voice: VoiceConfig): String? {
        val parts = buildList {
            voice.emotion?.takeIf { it.isNotBlank() }?.let { add("Emotion: $it") }
            voice.extras[DELIVERY]?.takeIf { it.isNotBlank() }?.let { add("Delivery: $it") }
            voice.extras[EMPHASIS]?.takeIf { it.isNotBlank() }?.let { add("Emphasis: $it") }
            voice.extras[VOCAL_CUES]?.takeIf { it.isNotBlank() }?.let {
                add("Before speaking, perform these audible reactions naturally: ${it.replace('|', ',')}")
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(". ")
    }

    fun elevenV3Prefix(voice: VoiceConfig): String = buildList {
        voice.emotion?.takeIf { it.isNotBlank() && it != "neutral" }?.let { add("[$it]") }
        voice.extras[DELIVERY]?.takeIf { it.isNotBlank() && it != "normal" }?.let {
            add("[${deliveryTag(it)}]")
        }
        voice.extras[VOCAL_CUES]
            ?.split('|')
            ?.filter { it.isNotBlank() }
            ?.forEach { add("[${cueTag(it.substringBefore(':'))}]") }
    }.joinToString(" ")

    fun localFallback(voice: VoiceConfig): VoiceConfig {
        val emotion = voice.emotion?.lowercase()
        val delivery = voice.extras[DELIVERY]?.lowercase()
        val speedFactor = when (emotion) {
            "sad", "tired", "calm" -> 0.92
            "angry", "excited", "surprised", "anxious" -> 1.07
            else -> 1.0
        } * when (delivery) {
            "slow", "dramatic", "hesitant" -> 0.9
            "fast", "urgent" -> 1.1
            else -> 1.0
        }
        val pitchFactor = when (emotion) {
            "sad", "angry", "serious", "tired" -> 0.96
            "happy", "excited", "surprised" -> 1.04
            else -> 1.0
        }
        val volumeFactor = when (delivery) {
            "whisper", "soft", "secretive" -> 0.7
            "shout", "loud" -> 1.2
            else -> 1.0
        }
        // Local engines cannot honour cloud-only semantic keys; strip them so
        // nothing leaks into logs/UI as raw tag names.
        return voice.copy(
            speed = (voice.speed * speedFactor).coerceIn(0.5, 2.0),
            pitch = (voice.pitch * pitchFactor).coerceIn(0.5, 2.0),
            volume = (voice.volume * volumeFactor).coerceIn(0.0, 4.0),
            extras = voice.extras - setOf(EMPHASIS, VOCAL_CUES),
        )
    }

    private fun deliveryTag(value: String): String = when (value.lowercase()) {
        "whisper" -> "whispers"
        "shout" -> "shouts"
        else -> value
    }

    private fun cueTag(value: String): String = when (value) {
        "laugh" -> "laughs"
        "chuckle" -> "chuckles"
        "giggle" -> "giggles"
        "cry" -> "crying"
        "clear_throat" -> "clears throat"
        "breath" -> "takes a breath"
        else -> value
    }
}
