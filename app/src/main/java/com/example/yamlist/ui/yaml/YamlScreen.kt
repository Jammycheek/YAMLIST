package com.example.yamlist.ui.yaml

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.yamlist.R
import com.example.yamlist.data.file.yaml.YamlImportMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun YamlScreen(
    onBack: () -> Unit,
    viewModel: YamlViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard: ClipboardManager = LocalClipboardManager.current
    var tab by remember { mutableIntStateOf(if (viewModel.projectId > 0) 1 else 0) }

    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { input ->
            viewModel.onInputText(input.readBytes().toString(Charsets.UTF_8))
        }
    }
    val createDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/yaml")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { it.write(state.exportText.toByteArray()) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.yaml_io)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        // imePadding lets the input helper bar ride on top of the keyboard.
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.yaml_input)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.yaml_output)) })
            }
            when (tab) {
                0 -> ImportTab(state, viewModel, onOpenFile = { openDoc.launch(arrayOf("*/*")) }, onImported = onBack)
                else -> ExportTab(
                    state = state,
                    onCopy = { clipboard.setText(AnnotatedString(state.exportText)) },
                    onSaveFile = { createDoc.launch("yamlist.yaml") },
                )
            }
        }
    }
}

@Composable
private fun ImportTab(
    state: YamlUiState,
    viewModel: YamlViewModel,
    onOpenFile: () -> Unit,
    onImported: () -> Unit,
) {
    // Local TextFieldValue so helper buttons can insert at the caret.
    var editorValue by remember { mutableStateOf(TextFieldValue(state.inputText)) }
    var editorFocused by remember { mutableStateOf(false) }
    LaunchedEffect(state.inputText) {
        // Adopt text set from outside (sample, file). Compare with the ViewModel's
        // current value, not the captured one, so fast typing is never rolled back.
        val latest = viewModel.state.value.inputText
        if (editorValue.text != latest) editorValue = TextFieldValue(latest, TextRange(latest.length))
    }
    fun onFieldChange(value: TextFieldValue) {
        editorValue = value
        if (value.text != viewModel.state.value.inputText) viewModel.onInputText(value.text)
    }
    fun insert(snippet: String) {
        val start = minOf(editorValue.selection.start, editorValue.selection.end)
        val end = maxOf(editorValue.selection.start, editorValue.selection.end)
        onFieldChange(
            TextFieldValue(editorValue.text.replaceRange(start, end, snippet), TextRange(start + snippet.length))
        )
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenFile) { Text(stringResource(R.string.open_file)) }
                OutlinedButton(onClick = { viewModel.loadSample() }) { Text(stringResource(R.string.load_sample)) }
                OutlinedButton(onClick = { viewModel.loadStructureSample() }) {
                    Text(stringResource(R.string.load_structure_sample))
                }
            }
            Text(
                stringResource(R.string.structure_import_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = editorValue,
                onValueChange = ::onFieldChange,
                label = { Text(stringResource(R.string.paste_yaml)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { editorFocused = it.isFocused },
                minLines = 8,
            )
            Button(onClick = { viewModel.validate() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.validate))
            }

            if (state.errors.isNotEmpty()) {
                Text(stringResource(R.string.errors), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error)
                state.errors.forEach { err ->
                    Text("• ${err.locator}: ${err.message}", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            state.preview?.let { preview ->
                Text(stringResource(R.string.preview), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.preview_summary_fmt, preview.title, preview.taskCount(), preview.maxDepth()))

                Text(stringResource(R.string.import_mode), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    YamlImportMode.entries.forEach { m ->
                        FilterChip(
                            selected = state.importMode == m,
                            onClick = { viewModel.onImportMode(m) },
                            label = { Text(importModeLabel(m)) },
                        )
                    }
                }
                Button(onClick = { viewModel.import { onImported() } }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.do_import))
                }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (editorFocused) YamlInputHelperBar(onInsert = ::insert)
    }
}

/** Keys that are awkward on a phone keyboard but constant in YAML. */
@Composable
private fun YamlInputHelperBar(onInsert: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val padding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        OutlinedButton(onClick = { onInsert("  ") }, contentPadding = padding) {
            Text(stringResource(R.string.yaml_helper_double_space))
        }
        OutlinedButton(onClick = { onInsert(": ") }, contentPadding = padding) {
            Text(stringResource(R.string.yaml_helper_colon))
        }
        OutlinedButton(onClick = { onInsert("- ") }, contentPadding = padding) {
            Text(stringResource(R.string.yaml_helper_hyphen))
        }
    }
}

@Composable
private fun ExportTab(state: YamlUiState, onCopy: () -> Unit, onSaveFile: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopy) { Text(stringResource(R.string.copy)) }
            OutlinedButton(onClick = onSaveFile) { Text(stringResource(R.string.save_file)) }
        }
        OutlinedTextField(
            value = state.exportText,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            minLines = 12,
        )
    }
}

@Composable
private fun importModeLabel(m: YamlImportMode): String = when (m) {
    YamlImportMode.NEW -> stringResource(R.string.import_new)
    YamlImportMode.UUID_UPSERT -> stringResource(R.string.import_upsert)
    YamlImportMode.DUPLICATE -> stringResource(R.string.import_duplicate)
}
