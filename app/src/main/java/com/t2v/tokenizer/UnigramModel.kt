package com.t2v.tokenizer

/**
 * Unigram language model for subword tokenization.
 *
 * Implements the `encode_optimized` path from the Rust tokenizers library:
 * a greedy best-path walk over a byte-level trie, with unknown-token fallback.
 *
 * @param vocab list of (token_string, log-score) pairs.
 * @param unkId vocabulary id for unknown tokens, or null.
 * @param byteFallback if true, unknown segments are split into individual byte tokens.
 */
class UnigramModel(
    private val vocab: List<Pair<String, Double>>,
    private val unkId: Int?,
    private val byteFallback: Boolean,
) {

    val minScore: Double = vocab.minOfOrNull { it.second } ?: 0.0
    private val tokenToId: Map<String, Int> = vocab.mapIndexed { i, (s, _) -> s to i }.toMap()
    private val trie: UnigramTrie = UnigramTrie().also { b ->
        for ((token, _) in vocab) {
            b.push(token.toByteArray(Charsets.UTF_8))
        }
    }

    companion object {
        private const val UNK_PENALTY = 10.0
    }

    /**
     * Encodes [sentence] into a list of token strings (Viterbi best path).
     */
    fun encode(sentence: String): List<String> {
        if (sentence.isEmpty()) return emptyList()
        val bytes = sentence.toByteArray(Charsets.UTF_8)
        val size = bytes.size
        val unkScore = minScore - UNK_PENALTY

        // Best-path DP array: one entry per byte position + end sentinel.
        data class BestNode(
            var id: Int = 0,
            var bestPathScore: Double = 0.0,
            var startsAt: Int = -1,
        )

        val best = Array(size + 1) { BestNode() }

        var startsAt = 0
        while (startsAt < size) {
            val bestPathScoreHere = best[startsAt].bestPathScore
            // Determine the UTF-8 byte length of the char starting at startsAt
            val mblen = charByteLength(bytes, startsAt)
            var hasSingleNode = false

            for (tokBytes in trie.commonPrefixSearch(bytes, startsAt)) {
                val keyPos = startsAt + tokBytes.size
                val token = String(tokBytes, Charsets.UTF_8)
                val target = best[keyPos]
                val length = keyPos - startsAt
                val id = tokenToId[token] ?: continue
                val score = vocab[id].second
                val candidate = score + bestPathScoreHere
                if (target.startsAt == -1 || candidate > target.bestPathScore) {
                    target.bestPathScore = candidate
                    target.startsAt = startsAt
                    target.id = id
                }
                if (!hasSingleNode && length == mblen) {
                    hasSingleNode = true
                }
            }
            if (!hasSingleNode && unkId != null) {
                val target = best[startsAt + mblen]
                val candidate = unkScore + bestPathScoreHere
                if (target.startsAt == -1 || candidate > target.bestPathScore) {
                    target.bestPathScore = candidate
                    target.startsAt = startsAt
                    target.id = unkId
                }
            }
            startsAt += mblen
        }

        // Backtrack to extract tokens.
        val results = mutableListOf<String>()
        val fuseBuffer = mutableListOf<String>()
        var endsAt = size
        while (endsAt > 0) {
            val node = best[endsAt]
            val sa = node.startsAt
            if (sa < 0) break
            val piece = String(bytes.copyOfRange(sa, endsAt), Charsets.UTF_8)
            if (unkId != null && node.id == unkId) {
                fuseBuffer.add(piece)
            } else {
                if (fuseBuffer.isNotEmpty()) {
                    results.add(fuseBuffer.asReversed().joinToString(""))
                    fuseBuffer.clear()
                }
                results.add(piece)
            }
            endsAt = sa
        }
        if (fuseBuffer.isNotEmpty()) {
            results.add(fuseBuffer.asReversed().joinToString(""))
        }
        results.reverse()
        return results
    }

    /**
     * Maps a token string to its vocabulary id.
     * If [byteFallback] is enabled and the token is not in vocab,
     * it is split into byte-level tokens <0xHH>.
     */
    fun tokenize(piece: String): List<Pair<Int, String>> {
        val id = tokenToId[piece]
        if (id != null) return listOf(id to piece)
        if (byteFallback) {
            val byteTokens = mutableListOf<Pair<Int, String>>()
            for (b in piece.toByteArray(Charsets.UTF_8)) {
                val hex = String.format("<0x%02X>", b.toInt() and 0xFF)
                val byteId = tokenToId[hex]
                if (byteId != null) {
                    byteTokens.add(byteId to hex)
                } else {
                    byteTokens.add((unkId ?: 0) to "<unk>")
                }
            }
            return byteTokens
        }
        return listOf((unkId ?: 0) to piece)
    }

    /** UTF-8 byte length of the character starting at [pos]. */
    private fun charByteLength(bytes: ByteArray, pos: Int): Int {
        val b = bytes[pos].toInt() and 0xFF
        return when {
            b < 0x80 -> 1
            b < 0xE0 -> 2
            b < 0xF0 -> 3
            else -> 4
        }
    }
}

/**
 * Byte-level trie for Unigram common_prefix_search.
 * Results are returned in order of increasing prefix length (shallowest leaf first).
 */
class UnigramTrie {
    private val root = TrieNode()

    fun push(element: ByteArray) {
        var node = root
        for (b in element) {
            node = node.children.getOrPut(b) { TrieNode() }
        }
        node.isLeaf = true
    }

    /**
     * Returns all complete tokens (leaf nodes) that are prefixes of
     * the byte slice starting at [start], in order of increasing byte length.
     */
    fun commonPrefixSearch(bytes: ByteArray, start: Int): List<ByteArray> {
        val results = mutableListOf<ByteArray>()
        var node = root
        var pos = start
        while (pos < bytes.size) {
            val child = node.children[bytes[pos]] ?: break
            node = child
            pos++
            if (node.isLeaf) {
                results.add(bytes.copyOfRange(start, pos))
            }
        }
        return results
    }

    private class TrieNode {
        var isLeaf = false
        val children = HashMap<Byte, TrieNode>()
    }
}
