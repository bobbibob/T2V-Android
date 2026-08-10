package com.t2v.tokenizer

import java.text.BreakIterator

/**
 * Pre-tokenizer pipeline: applies [WhitespaceSplit] then [Metaspace].
 */
class PreTokenizerPipeline {

    /**
     * Splits on Unicode whitespace characters (matching Rust's `char::is_whitespace`).
     * Whitespace is removed; only non-whitespace segments are returned.
     */
    fun whitespaceSplit(text: String): List<String> {
        val words = mutableListOf<String>()
        val sb = StringBuilder()
        for (cp in text.codePoints()) {
            if (Character.isWhitespace(cp)) {
                if (sb.isNotEmpty()) {
                    words.add(sb.toString())
                    sb.clear()
                }
            } else {
                sb.appendCodePoint(cp)
            }
        }
        if (sb.isNotEmpty()) {
            words.add(sb.toString())
        }
        return words
    }

    /**
     * Metaspace pretokenizer: for each word, replace spaces with '▁',
     * prepend '▁' (prepend_scheme=always), then split on '▁' with
     * MergedWithNext behaviour.
     */
    fun metaspace(word: String): List<String> {
        // WhitespaceSplit already stripped internal whitespace, so
        // replace(' ', '▁') is effectively a no-op, but we do it for correctness.
        val replaced = word.replace(' ', '\u2581')
        val prepared = if (replaced.isNotEmpty() && replaced[0] != '\u2581') {
            "\u2581$replaced"
        } else {
            replaced
        }
        // Split on '▁' with MergedWithNext: the delimiter merges with the following segment.
        // For "▁light" this produces ["▁light"] (leading ▁ is the delimiter, merges with "light").
        // For "▁" alone it produces ["▁"].
        return splitMergedWithNext(prepared, '\u2581')
    }

    /**
     * Applies the full pre-tokenizer pipeline to [text].
     * Returns a list of sub-strings, each fed independently to the Unigram model.
     */
    fun pretokenize(text: String): List<String> {
        val words = whitespaceSplit(text)
        val pieces = mutableListOf<String>()
        for (word in words) {
            pieces.addAll(metaspace(word))
        }
        return pieces
    }

    /**
     * Splits [input] on [delimiter] with MergedWithNext behaviour:
     * each delimiter is merged with the segment that follows it.
     * Consecutive delimiters produce individual delimiter-only segments.
     */
    private fun splitMergedWithNext(input: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        for (c in input) {
            if (c == delimiter) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current.clear()
                }
                current.append(c)
            } else {
                current.append(c)
            }
        }
        if (current.isNotEmpty()) {
            result.add(current.toString())
        }
        return result
    }
}
