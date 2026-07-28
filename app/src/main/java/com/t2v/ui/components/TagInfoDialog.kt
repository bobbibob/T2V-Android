package com.t2v.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.t2v.R
import com.t2v.core.model.GenerationModelCatalog

/**
 * Диалог с описанием поддерживаемых LTV-тегов для конкретной модели / движка / генератора.
 *
 * Используется с кнопки Info в ModelsScreen.
 */
@Composable
fun TagInfoDialog(
    title: String,
    tagline: String?,
    tags: GenerationModelCatalog.TagDocs?,
    runtimeLabel: String? = null,
    repository: String? = null,
    license: String? = null,
    categoryLabel: String? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.info_close))
            }
        },
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (!categoryLabel.isNullOrBlank()) {
                    Text(
                        categoryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!tagline.isNullOrBlank()) {
                    Text(
                        tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                InfoRow(label = stringResource(R.string.info_runtime), value = runtimeLabel)
                InfoRow(label = stringResource(R.string.info_repository), value = repository)
                InfoRow(label = stringResource(R.string.info_license), value = license)

                if (tags == null) {
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        stringResource(R.string.info_no_docs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    if (tags.supported.isNotEmpty()) {
                        TagSection(
                            title = stringResource(R.string.info_supported),
                            items = tags.supported,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (tags.partial.isNotEmpty()) {
                        TagSection(
                            title = stringResource(R.string.info_partial),
                            items = tags.partial,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (tags.ignored.isNotEmpty()) {
                        TagSection(
                            title = stringResource(R.string.info_ignored),
                            items = tags.ignored,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (tags.examples.isNotEmpty()) {
                        TagSection(
                            title = stringResource(R.string.info_examples),
                            items = tags.examples,
                            color = MaterialTheme.colorScheme.secondary,
                            monospace = true,
                        )
                    }
                    if (!tags.promptHelp.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.info_prompt_help),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            tags.promptHelp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TagSection(
    title: String,
    items: List<String>,
    color: androidx.compose.ui.graphics.Color,
    monospace: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = color)
        items.forEach { line ->
            val style = if (monospace) {
                MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            } else {
                MaterialTheme.typography.bodySmall
            }
            Text("- " + line, style = style)
        }
    }
}
