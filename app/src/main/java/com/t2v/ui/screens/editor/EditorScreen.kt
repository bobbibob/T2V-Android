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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.t2v.R
import com.t2v.ui.components.LTVScaffold
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    nav: NavController,
    vm: EditorViewModel = viewModel(factory = EditorViewModelFactory(LocalContext.current)),
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

            OutlinedTextField(
                value = state.text,
                onValueChange = vm::setText,
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
