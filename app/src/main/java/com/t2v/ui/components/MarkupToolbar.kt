package com.t2v.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.t2v.R
import com.t2v.core.markup.MarkupTagRegistry
import com.t2v.core.markup.MarkupTagRegistry.SupportLevel
import com.t2v.ui.theme.LTVColors

/**
 * Контекстная панель LTV-разметки.
 *
 * Показывает только теги, поддерживаемые выбранным движком [engineId].
 * Поддерживает два режима:
 *  - Вставка сниппета (onInsert) — для тегов-команд {{...}}
 *  - Обёртка выделенного текста (onWrap) — для парных inline-тегов
 *
 * Зелёная метка — поддерживается нативно.
 * Оранжевая "≈" — приближённое отображение (через скорость/высоту).
 */
@Composable
fun MarkupToolbar(
    onInsert: (String) -> Unit,
    onWrap: (openTag: String, closeTag: String) -> Unit = { _, _ -> },
    engineId: String = "",
    modifier: Modifier = Modifier,
) {
    val tags = remember(engineId) {
        if (engineId.isBlank()) {
            MarkupTagRegistry.allTags
        } else {
            MarkupTagRegistry.visibleTags(engineId)
        }
    }
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            val support = if (engineId.isBlank()) SupportLevel.SUPPORTED
                else MarkupTagRegistry.supportLevel(tag.key, engineId)
            MarkupChip(
                label = tag.label,
                icon = iconForCategory(tag.category),
                color = colorForCategory(tag.category, support),
                bg = bgForCategory(tag.category, support),
                snippet = tag.snippet,
                supportLevel = support,
                isInline = tag.isInline,
                openTag = tag.openTag,
                closeTag = tag.closeTag,
                onInsert = onInsert,
                onWrap = onWrap,
            )
        }
    }
}

@Composable
private fun MarkupChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    bg: Color,
    snippet: String,
    supportLevel: SupportLevel,
    isInline: Boolean,
    openTag: String,
    closeTag: String,
    onInsert: (String) -> Unit,
    onWrap: (openTag: String, closeTag: String) -> Unit,
) {
    AssistChip(
        onClick = {
            if (isInline) {
                onWrap(openTag, closeTag)
            } else {
                onInsert(snippet)
            }
        },
        label = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(label)
                if (supportLevel == SupportLevel.PARTIAL) {
                    Text(
                        text = " ≈",
                        color = Color(0xFFFF8800),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
            }
        },
        leadingIcon = { Icon(icon, contentDescription = null) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = bg,
            labelColor = color,
            leadingIconContentColor = color,
        ),
    )
}

private fun iconForCategory(category: MarkupTagRegistry.Category) =
    when (category) {
        MarkupTagRegistry.Category.VOICE,
        MarkupTagRegistry.Category.EMOTION,
        MarkupTagRegistry.Category.DELIVERY,
        MarkupTagRegistry.Category.VOCAL_CUE,
        MarkupTagRegistry.Category.EMPHASIS -> Icons.Default.RecordVoiceOver
        MarkupTagRegistry.Category.PAUSE -> Icons.Default.Pause
        MarkupTagRegistry.Category.SPEED -> Icons.Default.Speed
        MarkupTagRegistry.Category.VOLUME -> Icons.Default.VolumeUp
        MarkupTagRegistry.Category.PITCH -> Icons.Default.Speed
        MarkupTagRegistry.Category.LANGUAGE -> Icons.Default.RecordVoiceOver
        MarkupTagRegistry.Category.CHAPTER -> Icons.Default.MusicNote
        MarkupTagRegistry.Category.MUSIC -> Icons.Default.Add
        MarkupTagRegistry.Category.SFX -> Icons.Default.VolumeUp
        MarkupTagRegistry.Category.RESET -> Icons.Default.Add
    }

private fun colorForCategory(category: MarkupTagRegistry.Category, support: SupportLevel): Color =
    when (support) {
        SupportLevel.PARTIAL -> Color(0xFF92400E)
        else -> when (category) {
            MarkupTagRegistry.Category.VOICE -> LTVColors.VoiceColor
            MarkupTagRegistry.Category.LANGUAGE -> LTVColors.LangColor
            MarkupTagRegistry.Category.SPEED -> LTVColors.SpeedColor
            MarkupTagRegistry.Category.VOLUME -> LTVColors.VolumeColor
            MarkupTagRegistry.Category.PAUSE -> LTVColors.PauseColor
            MarkupTagRegistry.Category.CHAPTER, MarkupTagRegistry.Category.MUSIC, MarkupTagRegistry.Category.SFX -> LTVColors.ChapterColor
            MarkupTagRegistry.Category.EMOTION, MarkupTagRegistry.Category.DELIVERY, MarkupTagRegistry.Category.VOCAL_CUE, MarkupTagRegistry.Category.EMPHASIS -> LTVColors.VoiceColor
            MarkupTagRegistry.Category.PITCH -> LTVColors.SpeedColor
            MarkupTagRegistry.Category.RESET -> LTVColors.OnSurface
        }
    }

private fun bgForCategory(category: MarkupTagRegistry.Category, support: SupportLevel): Color =
    when (support) {
        SupportLevel.PARTIAL -> Color(0xFFFFF7ED)
        else -> when (category) {
            MarkupTagRegistry.Category.VOICE -> LTVColors.VoiceBg
            MarkupTagRegistry.Category.LANGUAGE -> LTVColors.LangBg
            MarkupTagRegistry.Category.SPEED -> LTVColors.SpeedBg
            MarkupTagRegistry.Category.VOLUME -> LTVColors.VolumeBg
            MarkupTagRegistry.Category.PAUSE -> LTVColors.PauseBg
            MarkupTagRegistry.Category.CHAPTER, MarkupTagRegistry.Category.MUSIC, MarkupTagRegistry.Category.SFX -> LTVColors.ChapterBg
            MarkupTagRegistry.Category.EMOTION, MarkupTagRegistry.Category.DELIVERY, MarkupTagRegistry.Category.VOCAL_CUE, MarkupTagRegistry.Category.EMPHASIS -> LTVColors.VoiceBg
            MarkupTagRegistry.Category.PITCH -> LTVColors.SpeedBg
            MarkupTagRegistry.Category.RESET -> LTVColors.Surface
        }
    }