package com.t2v.tts.auto

import com.t2v.tts.VoiceInfo

/**
 * Auto-TTS voice suggestion: after [AutoTtsDetector] decides the text
 * language, this picks the best available voice among the ones the caller
 * currently has (local installed voices, cloud engine voices, …).
 *
 * Matching is: exact BCP-47 language first, then any voice sharing the
 * primary language subtag (e.g. an `en-GB` voice satisfies a detected
 * `en-US` text), otherwise `null` so the caller can keep its default.
 */
object AutoVoicePicker {

    fun pickVoice(voices: List<VoiceInfo>, detectedBcp47: String): VoiceInfo? {
        if (voices.isEmpty()) return null
        val exact = voices.firstOrNull { it.language == detectedBcp47 }
        if (exact != null) return exact
        val primary = detectedBcp47.substringBefore('-')
        return voices.firstOrNull { it.language.startsWith("$primary-") }
    }
}
