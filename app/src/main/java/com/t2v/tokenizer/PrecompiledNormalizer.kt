package com.t2v.tokenizer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.BreakIterator

/**
 * SentencePiece-style precompiled normalizer.
 *
 * Parses the binary `precompiled_charsmap` format:
 *   [u32: trie_byte_size][trie: u32 × (size/4)][normalized: UTF-8 with \0 delimiters]
 *
 * The trie is a Dart DoubleArray used for common_prefix_search.
 */
class PrecompiledNormalizer(charsmapBase64: String) {

    private val array: IntArray
    private val normalized: String

    init {
        val raw = java.util.Base64.getDecoder().decode(charsmapBase64)
        val buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val trieSizeBytes = buf.int
        val trieEntries = trieSizeBytes / 4
        array = IntArray(trieEntries)
        for (i in 0 until trieEntries) {
            array[i] = buf.int
        }
        val normalizedBytes = ByteArray(buf.remaining())
        buf.get(normalizedBytes)
        normalized = String(normalizedBytes, Charsets.UTF_8)
    }

    /* ---- DoubleArray trait methods ---- */

    private fun hasLeaf(unit: Int): Boolean = (unit ushr 8) and 1 == 1

    private fun value(unit: Int): Int = unit and 0x7FFFFFFF

    private fun label(unit: Int): Int = unit and (0x80000000.toInt() or 0xFF)

    private fun offset(unit: Int): Int {
        val base = unit ushr 10
        val shift = (unit and 0x200) ushr 6
        return base shl shift
    }

    /**
     * Walks the DoubleArray trie and returns all values (offsets into [normalized])
     * for prefixes of [key] that are leaves.
     */
    private fun commonPrefixSearch(key: ByteArray): List<Int> {
        val results = mutableListOf<Int>()
        var nodePos = 0
        var unit = array[nodePos]
        nodePos = nodePos xor offset(unit)
        for (c in key) {
            if (c == 0.toByte()) break
            nodePos = nodePos xor (c.toInt() and 0xFF)
            unit = array[nodePos]
            if (label(unit) != (c.toInt() and 0xFF)) return results
            nodePos = nodePos xor offset(unit)
            if (hasLeaf(unit)) {
                results.add(value(array[nodePos]))
            }
        }
        return results
    }

    /**
     * Looks up a single byte sequence in the trie and returns the corresponding
     * substring of the normalized string (from the first NUL before the value
     * up to the next NUL).
     */
    private fun transform(chunk: String): String? {
        val results = commonPrefixSearch(chunk.toByteArray(Charsets.UTF_8))
        if (results.isEmpty()) return null
        val idx = results[0]
        var end = idx
        while (end < normalized.length) {
            if (normalized[end] == '\u0000') break
            end++
        }
        return normalized.substring(idx, end)
    }

    /**
     * Normalizes [original] using the precompiled charsmap.
     *
     * Replicates sentencepiece's grapheme-aware normalization:
     * - For each extended grapheme cluster of < 6 bytes, try the whole cluster.
     * - Otherwise iterate codepoints and try each.
     */
    fun normalizeString(original: String): String {
        val sb = StringBuilder(original.length)
        val graphIter = BreakIterator.getCharacterInstance()
        graphIter.setText(original)
        var start = graphIter.first()
        var end = graphIter.next()
        while (end != BreakIterator.DONE) {
            val grapheme = original.substring(start, end)
            if (grapheme.toByteArray(Charsets.UTF_8).size < 6) {
                val norm = transform(grapheme)
                if (norm != null) {
                    sb.append(norm)
                    start = end
                    end = graphIter.next()
                    continue
                }
            }
            // Fallback: iterate individual codepoints within the grapheme
            var ci = 0
            while (ci < grapheme.length) {
                val cp = grapheme.codePointAt(ci)
                val charLen = Character.charCount(cp)
                val part = grapheme.substring(ci, ci + charLen)
                val norm = transform(part)
                if (norm != null) {
                    sb.append(norm)
                } else {
                    sb.appendCodePoint(cp)
                }
                ci += charLen
            }
            start = end
            end = graphIter.next()
        }
        return sb.toString()
    }
}
