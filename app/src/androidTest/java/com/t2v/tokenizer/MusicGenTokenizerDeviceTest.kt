package com.t2v.tokenizer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device verification of the MusicGen T5 tokenizer.
 *
 * Runs on a real ART runtime / ICU so that the grapheme-cluster handling
 * (BreakIterator) and the byte-level Unigram Viterbi are checked where the
 * app actually runs, not just on the JVM. Golden vectors come from the
 * reference `tokenizers` crate (same values as the JVM unit tests).
 */
@RunWith(AndroidJUnit4::class)
class MusicGenTokenizerDeviceTest {

    private fun loadTokenizer(): MusicGenTokenizer {
        val json = InstrumentationRegistry.getInstrumentation()
            .context
            .assets
            .open("tokenizer.json")
            .bufferedReader()
            .use { it.readText() }
        return MusicGenTokenizer.fromJson(json)
    }

    @Test
    fun ascii_prompt_matches_golden_ids() {
        val tokenizer = loadTokenizer()
        val result = tokenizer("80s pop track with bassy drums and synth")
        assertEquals(
            listOf(2775, 7, 2783, 1463, 28, 7981, 63, 5253, 7, 11, 13353, 1),
            result.ids,
        )
        assertEquals(
            listOf(
                "▁80", "s", "▁pop", "▁track", "▁with", "▁bass", "y", "▁drum",
                "s", "▁and", "▁synth", "</s>",
            ),
            result.tokens,
        )
    }

    @Test
    fun unicode_prompt_matches_golden_ids() {
        val tokenizer = loadTokenizer()
        // Accented / precomposed letters exercise the ICU grapheme break iterator.
        val result = tokenizer("música eletrônica suave")
        assertEquals(
            listOf(3, 51, 2, 7, 2617, 3, 400, 17, 52, 10079, 12878, 2629, 9, 162, 1),
            result.ids,
        )
        assertEquals(
            listOf(
                "▁", "m", "ú", "s", "ica", "▁", "ele", "t", "r", "ô", "nica",
                "▁su", "a", "ve", "</s>",
            ),
            result.tokens,
        )
    }

    @Test
    fun empty_input_produces_only_eos() {
        val tokenizer = loadTokenizer()
        val result = tokenizer("")
        assertEquals(listOf(1), result.ids)
        assertEquals(listOf("</s>"), result.tokens)
    }
}
