package com.t2v.ui.screens.editor

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.t2v.R
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.t2v.ui.components.LTVScaffold
import com.t2v.ui.components.MarkupToolbar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    nav: NavController,
    vm: EditorViewModel = viewModel(factory = EditorViewModelFactory(LocalContext.current)),
    windowSizeClass: WindowSizeClass? = null,
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            vm.setOutputTreeUri(uri.toString())
        }
    }

    LTVScaffold(
        nav = nav,
        title = stringResource(R.string.nav_editor),
        windowSizeClass = windowSizeClass,
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = vm::setTitle,
                label = { Text("Project title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.author,
                onValueChange = vm::setAuthor,
                label = { Text("Автор (необязательно)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.outputTreeUri.isBlank()) "Выбрать папку проекта" else "Папка проекта выбрана")
            }

            // Track TextFieldValue for selection-aware tag wrapping
            var textFieldValue by remember { mutableStateOf(TextFieldValue(state.text)) }
            val currentEngineId = remember { "" } // TODO: pass from settings

            if (state.markupToolbar) {
                MarkupToolbar(
                    onInsert = { snippet ->
                        val newText = textFieldValue.text.substring(0, textFieldValue.selection.start) +
                            snippet + textFieldValue.text.substring(textFieldValue.selection.end)
                        textFieldValue = TextFieldValue(newText, TextRange(textFieldValue.selection.start + snippet.length))
                        vm.setText(newText)
                    },
                    onWrap = { openTag, closeTag ->
                        if (closeTag.isEmpty()) {
                            // Self-closing tag (e.g. <breath/>): insert at cursor
                            val pos = textFieldValue.selection.end
                            val newText = textFieldValue.text.substring(0, pos) +
                                openTag + textFieldValue.text.substring(pos)
                            textFieldValue = TextFieldValue(newText, TextRange(pos + openTag.length))
                            vm.setText(newText)
                        } else {
                            // Paired tag: wrap selected text
                            val start = textFieldValue.selection.start
                            val end = textFieldValue.selection.end
                            val selected = textFieldValue.text.substring(start, end)
                            val wrapped = openTag + selected + closeTag
                            val newText = textFieldValue.text.substring(0, start) +
                                wrapped + textFieldValue.text.substring(end)
                            textFieldValue = TextFieldValue(
                                newText,
                                TextRange(start + openTag.length, end + openTag.length),
                            )
                            vm.setText(newText)
                        }
                    },
                    engineId = currentEngineId,
                )
            }

            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    vm.setText(newValue.text)
                },
                label = { Text(stringResource(R.string.editor_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 8,
            )

            Text(
                "${state.text.length} chars • ${state.chunkCount} chunks",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("safe_chunks", "sentence", "paragraph").forEach { mode ->
                    FilterChip(
                        selected = state.splitMode == mode,
                        onClick = { vm.setSplitMode(mode) },
                        label = { Text(mode) },
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        val projectId = vm.save(context)
                        if (projectId != null) {
                            nav.navigate(com.t2v.ui.navigation.Routes.generation(projectId))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.text.isNotBlank() && state.outputTreeUri.isNotBlank(),
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Text("  " + stringResource(R.string.gen_start))
            }
        }
    }
}
