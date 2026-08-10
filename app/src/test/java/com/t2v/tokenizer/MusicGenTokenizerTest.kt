package com.t2v.tokenizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the MusicGen T5 Unigram tokenizer.
 *
 * Golden vectors are produced by the reference Python `tokenizers` library
 * (which wraps the same Rust code as the huggingface/tokenizers crate).
 */
class MusicGenTokenizerTest {

    private lateinit var tokenizer: MusicGenTokenizer

    @Before
    fun setUp() {
        // Load tokenizer.json from the test resources.
        val jsonStr = this::class.java.classLoader
            .getResourceAsStream("tokenizer.json")!!
            .bufferedReader()
            .readText()
        tokenizer = MusicGenTokenizer.fromJson(jsonStr)
    }

    // ---- Golden vector tests ----

    @Test
    fun `encodes EDM prompt correctly`() {
        val prompt = "a light and cheerly EDM track, with syncopated drums, aery pads, and strong emotions bpm: 130"
        val result = tokenizer(prompt)

        assertEquals(
            listOf(
                3, 9, 659, 11, 11288, 120, 262, 7407, 1463, 6, 28, 8953, 10845,
                920, 5253, 7, 6, 3, 9, 4203, 17484, 6, 11, 1101, 7848, 3, 115,
                2028, 10, 12778, 1,
            ),
            result.ids,
        )
        assertEquals(31, result.ids.size)
        assertEquals(result.ids.size, result.attentionMask.size)
        assertTrue(result.attentionMask.all { it == 1 })

        assertEquals(
            listOf(
                "▁", "a", "▁light", "▁and", "▁cheer", "ly", "▁E", "DM", "▁track",
                ",", "▁with", "▁syn", "cop", "ated", "▁drum", "s", ",", "▁", "a",
                "ery", "▁pads", ",", "▁and", "▁strong", "▁emotions", "▁", "b", "pm",
                ":", "▁130", "</s>",
            ),
            result.tokens,
        )
    }

    @Test
    fun `encodes pop prompt correctly`() {
        val prompt = "80s pop track with bassy drums and synth"
        val result = tokenizer(prompt)

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
    fun `encodes ambient prompt correctly`() {
        val prompt = "ambient rain at night, calm and slow"
        val result = tokenizer(prompt)

        assertEquals(
            listOf(21128, 3412, 44, 706, 6, 4447, 11, 2684, 1),
            result.ids,
        )
        assertEquals(
            listOf(
                "▁ambient", "▁rain", "▁at", "▁night", ",", "▁calm", "▁and",
                "▁slow", "</s>",
            ),
            result.tokens,
        )
    }

    @Test
    fun `encodes rock prompt correctly`() {
        val prompt = "rock guitar riff, energetic, 120 bpm"
        val result = tokenizer(prompt)

        assertEquals(
            listOf(2480, 5507, 3, 17048, 6, 11273, 6, 5864, 3, 115, 2028, 1),
            result.ids,
        )
        assertEquals(
            listOf(
                "▁rock", "▁guitar", "▁", "riff", ",", "▁energetic", ",",
                "▁120", "▁", "b", "pm", "</s>",
            ),
            result.tokens,
        )
    }

    // ---- Component tests ----

    @Test
    fun `precompiled normalizer returns identity for ASCII`() {
        val normalizer = PrecompiledNormalizer(
            java.util.Base64.getEncoder().encodeToString(ByteArray(0)),
        )
        assertEquals("hello", normalizer.normalizeString("hello"))
    }

    @Test
    fun `whitespace split handles multiple spaces`() {
        val pt = PreTokenizerPipeline()
        assertEquals(
            listOf("a", "b", "c"),
            pt.whitespaceSplit("  a  b  c  "),
        )
    }

    @Test
    fun `metaspace prepends underscore`() {
        val pt = PreTokenizerPipeline()
        assertEquals(listOf("▁light"), pt.metaspace("light"))
    }

    @Test
    fun `unigram encode basic`() {
        val vocab = listOf(
            "<unk>" to 0.0,
            "a" to -0.3,
            "b" to -0.4,
            "c" to -0.5,
            "ab" to 0.0,
            "abc" to -0.2,
            "abcd" to 10.0,
        )
        val model = UnigramModel(vocab, unkId = 0, byteFallback = false)
        assertEquals(listOf("abcd"), model.encode("abcd"))
    }

    @Test
    fun `unigram encode falls back to unk when no single-char match`() {
        val vocab = listOf(
            "<unk>" to 0.0,
            "ab" to 0.0,
        )
        val model = UnigramModel(vocab, unkId = 0, byteFallback = false)
        val result = model.encode("xyz")
        assertEquals(1, result.size)
        assertEquals("xyz", result[0])
    }

    @Test
    fun `eos token id is 1`() {
        val result = tokenizer("test")
        assertEquals(1, result.ids.last())
        assertEquals("</s>", result.tokens.last())
    }

    @Test
    fun `empty input produces only eos`() {
        val result = tokenizer("")
        assertEquals(listOf(1), result.ids)
        assertEquals(listOf("</s>"), result.tokens)
    }
}
