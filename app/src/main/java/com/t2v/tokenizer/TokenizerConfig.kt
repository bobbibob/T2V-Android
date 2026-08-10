package com.t2v.tokenizer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Parsed structure of a HuggingFace `tokenizer.json` file.
 * Only the fields needed for the MusicGen-small T5 Unigram tokenizer are modelled.
 */
@Serializable
data class TokenizerConfig(
    val normalizer: NormalizerConfig? = null,
    val pre_tokenizer: PreTokenizerConfig? = null,
    val post_processor: PostProcessorConfig? = null,
    val model: ModelConfig,
    val added_tokens: List<AddedTokenConfig> = emptyList(),
)

@Serializable
data class AddedTokenConfig(
    val id: Int = 0,
    val content: String = "",
    val special: Boolean = false,
)

@Serializable
@SerialName("Unigram")
data class ModelConfig(
    val vocab: List<List<kotlinx.serialization.json.JsonElement>> = emptyList(),
    val unk_id: Int? = null,
    val byte_fallback: Boolean = false,
)

@Serializable
@JsonClassDiscriminator("type")
sealed interface NormalizerConfig {
    @Serializable
    @SerialName("Precompiled")
    data class Precompiled(
        val precompiled_charsmap: String = "",
    ) : NormalizerConfig
}

@Serializable
@JsonClassDiscriminator("type")
sealed interface PreTokenizerConfig {
    @Serializable
    @SerialName("Sequence")
    data class Sequence(
        val pretokenizers: List<PreTokenizerConfig> = emptyList(),
    ) : PreTokenizerConfig

    @Serializable
    @SerialName("WhitespaceSplit")
    data object WhitespaceSplit : PreTokenizerConfig

    @Serializable
    @SerialName("Metaspace")
    data class Metaspace(
        val replacement: String = "▁",
        val add_prefix_space: Boolean = false,
        val prepend_scheme: String = "always",
        val split: Boolean = true,
    ) : PreTokenizerConfig
}

@Serializable
@JsonClassDiscriminator("type")
sealed interface PostProcessorConfig {
    @Serializable
    @SerialName("TemplateProcessing")
    data class TemplateProcessing(
        val single: List<TemplateElement> = emptyList(),
        val special_tokens: Map<String, SpecialTokenInfo> = emptyMap(),
    ) : PostProcessorConfig

    @Serializable
    data class TemplateElement(
        val Sequence: TemplateSequence? = null,
        val SpecialToken: SpecialTokenRef? = null,
    )

    @Serializable
    data class TemplateSequence(
        val id: String = "",
        val type_id: Int = 0,
    )

    @Serializable
    data class SpecialTokenRef(
        val id: String = "",
        val type_id: Int = 0,
    )

    @Serializable
    data class SpecialTokenInfo(
        val id: String = "",
        val ids: List<Int> = emptyList(),
        val tokens: List<String> = emptyList(),
    )
}
