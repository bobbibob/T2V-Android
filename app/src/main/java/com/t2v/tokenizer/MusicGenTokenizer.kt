package com.t2v.tokenizer

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * MusicGen T5 Unigram tokenizer.
 *
 * Pipeline: normalize → pre-tokenize → encode → post-process.
 * Loads the tokenizer configuration from a HuggingFace `tokenizer.json` file.
 *
 * Usage:
 * ```kotlin
 * val tokenizer = MusicGenTokenizer.fromAssets(context, "tokenizer.json")
 * val result = tokenizer("a light and cheerly EDM track")
 * println(result.ids)       // [3, 9, 659, ...]
 * println(result.tokens)    // ["▁", "a", "▁light", ...]
 * ```
 */
class MusicGenTokenizer private constructor(
    private val normalizer: PrecompiledNormalizer?,
    private val preTokenizer: PreTokenizerPipeline,
    private val model: UnigramModel,
    private val eosTokenIds: List<Int>,
    private val eosTokenStrings: List<String>,
    private val padTokenId: Int,
    private val unkTokenId: Int,
) {
    data class EncodeResult(
        val ids: List<Int>,
        val tokens: List<String>,
        val attentionMask: List<Int>,
    )

    /**
     * Tokenizes [text] and returns ids, token strings, and attention mask.
     */
    operator fun invoke(text: String): EncodeResult {
        // 1. Normalize
        val normalized = normalizer?.normalizeString(text) ?: text

        // 2. Pre-tokenize
        val pieces = preTokenizer.pretokenize(normalized)

        // 3. Encode each piece through the Unigram model
        val allIds = mutableListOf<Int>()
        val allTokens = mutableListOf<String>()

        for (piece in pieces) {
            val substrings = model.encode(piece)
            for (sub in substrings) {
                val tokenPairs = model.tokenize(sub)
                for ((id, tok) in tokenPairs) {
                    allIds.add(id)
                    allTokens.add(tok)
                }
            }
        }

        // 4. Post-process: append </s> (EOS)
        allIds.addAll(eosTokenIds)
        if (eosTokenStrings.size == eosTokenIds.size) {
            allTokens.addAll(eosTokenStrings)
        } else {
            allTokens.addAll(eosTokenIds.map { it.toString() })
        }

        val attentionMask = List(allIds.size) { 1 }

        return EncodeResult(
            ids = allIds,
            tokens = allTokens,
            attentionMask = attentionMask,
        )
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        /**
         * Loads the tokenizer from an asset file in an Android app.
         */
        fun fromAssets(context: Context, assetPath: String): MusicGenTokenizer {
            val raw = context.assets.open(assetPath).bufferedReader().readText()
            return fromJson(raw)
        }

        /**
         * Loads the tokenizer from a JSON string (for JVM unit tests).
         */
        fun fromJson(jsonStr: String): MusicGenTokenizer {
            val config = json.decodeFromString<TokenizerConfig>(jsonStr)
            return fromConfig(config)
        }

        /**
         * Loads the tokenizer from a [TokenizerConfig] object.
         */
        fun fromConfig(config: TokenizerConfig): MusicGenTokenizer {
            // Normalizer
            val normalizer = when (val n = config.normalizer) {
                is NormalizerConfig.Precompiled -> PrecompiledNormalizer(n.precompiled_charsmap)
                null -> null
            }

            // Pre-tokenizer
            val preTokenizer = PreTokenizerPipeline()

            // Model
            val vocab = config.model.vocab.map { pair ->
                val token = pair[0].jsonPrimitive.content
                val score = pair[1].jsonPrimitive.double
                token to score
            }
            val model = UnigramModel(
                vocab = vocab,
                unkId = config.model.unk_id,
                byteFallback = config.model.byte_fallback,
            )

            // Post-processor: extract EOS token ids
            val eosTokenIds = mutableListOf<Int>()
            val eosTokenStrings = mutableListOf<String>()
            var padTokenId = 0
            when (val pp = config.post_processor) {
                is PostProcessorConfig.TemplateProcessing -> {
                    for (element in pp.single) {
                        val specialTokenRef = element.SpecialToken
                        if (specialTokenRef != null) {
                            val info = pp.special_tokens[specialTokenRef.id]
                            if (info != null) {
                                eosTokenIds.addAll(info.ids)
                                eosTokenStrings.addAll(info.tokens)
                            }
                        }
                    }
                }
                null -> {}
            }

            // Added tokens: find <pad> and <unk> ids
            for (at in config.added_tokens) {
                when (at.content) {
                    "<pad>" -> padTokenId = at.id
                    "<unk>" -> {} // unk_id comes from model config
                }
            }

            val unkTokenId = config.model.unk_id ?: 2

            return MusicGenTokenizer(
                normalizer = normalizer,
                preTokenizer = preTokenizer,
                model = model,
                eosTokenIds = eosTokenIds,
                eosTokenStrings = eosTokenStrings,
                padTokenId = padTokenId,
                unkTokenId = unkTokenId,
            )
        }
    }
}
