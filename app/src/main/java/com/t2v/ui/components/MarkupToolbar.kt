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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.t2v.R
import com.t2v.ui.theme.LTVColors

/**
 * Панель кнопок LTV-разметки над редактором.
 *
 * Аналог `markup_toolbar` в `main_window.py` (строки 1402-1664).
 * Вставка команд в позицию курсора реализуется через [onInsert].
 */
@Composable
fun MarkupToolbar(
    onInsert: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MarkupChip(
            label = stringResource(R.string.markup_voice),
            icon = Icons.Default.RecordVoiceOver,
            color = LTVColors.VoiceColor,
            bg = LTVColors.VoiceBg,
            snippet = """{{voice "Alice"}}""",
            onInsert = onInsert,
        )
        MarkupChip(
            label = "Эмоция",
            icon = Icons.Default.RecordVoiceOver,
            color = LTVColors.ChapterColor,
            bg = LTVColors.ChapterBg,
            snippet = "{{emotion happy}}",
            onInsert = onInsert,
        )
        MarkupChip(
            label = "Шёпот",
            icon = Icons.Default.RecordVoiceOver,
            color = LTVColors.ChapterColor,
            bg = LTVColors.ChapterBg,
            snippet = "{{delivery whisper}}",
            onInsert = onInsert,
        )
        MarkupChip(
            label = "Вдох",
            icon = Icons.Default.RecordVoiceOver,
            color = LTVColors.ChapterColor,
            bg = LTVColors.ChapterBg,
            snippet = "{{breath}}",
            onInsert = onInsert,
        )
        MarkupChip(
            label = stringResource(R.string.markup_pause),
            icon = Icons.Default.Pause,
            color = LTVColors.PauseColor,
            bg = LTVColors.PauseBg,
            snippet = "{{pause 500ms}}",
            onInsert = onInsert,
        )
        MarkupChip(
            label = stringResource(R.string.markup_speed),
            icon = Icons.Default.Speed,
            color = LTVColors.SpeedColor,
            bg = LTVColors.SpeedBg,
            snippet = "{{speed 0.9}}",
            onInsert = onInsert,
        )
        MarkupChip(
            label = stringResource(R.string.markup_volume),
            icon = Icons.Default.VolumeUp,
            color = LTVColors.VolumeColor,
            bg = LTVColors.VolumeBg,
            snippet = "{{volume 80%}}",
            onInsert = onInsert,
        )
        MarkupChip(
            label = stringResource(R.string.markup_chapter),
            icon = Icons.Default.MusicNote,
            color = LTVColors.ChapterColor,
            bg = LTVColors.ChapterBg,
            snippet = """{{chapter "Title"}}""",
            onInsert = onInsert,
        )
        MarkupChip(
            label = stringResource(R.string.markup_music),
            icon = androidx.compose.material.icons.Icons.Filled.Add,
            color = LTVColors.Accent,
            bg = LTVColors.ChapterBg,
            snippet = "<music>ambient pad, 5 sec</music>",
            onInsert = onInsert,
        )
        MarkupChip(
            label = stringResource(R.string.markup_sfx),
            icon = androidx.compose.material.icons.Icons.Filled.VolumeUp,
            color = LTVColors.Accent,
            bg = LTVColors.ChapterBg,
            snippet = "<sfx>door close</sfx>",
            onInsert = onInsert,
        )
    }
}

@Composable
private fun MarkupChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    bg: Color,
    snippet: String,
    onInsert: (String) -> Unit,
) {
    AssistChip(
        onClick = { onInsert(snippet) },
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = bg,
            labelColor = color,
            leadingIconContentColor = color,
        ),
    )
}
