package com.example.nestprogress.ui.print

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nestprogress.R
import com.example.nestprogress.data.file.pdf.ChecklistPdfGenerator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintScreen(
    onBack: () -> Unit,
    viewModel: PrintViewModel = hiltViewModel(),
) {
    val project by viewModel.project.collectAsState()
    val options by viewModel.options.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    val createPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.generateTo { context.contentResolver.openOutputStream(uri) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.print_preview)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(project?.title ?: "", style = MaterialTheme.typography.titleMedium)

            ToggleRow(stringResource(R.string.include_done), options.includeDone) { v -> viewModel.update { it.copy(includeDone = v) } }
            ToggleRow(stringResource(R.string.include_comments), options.includeComments) { v -> viewModel.update { it.copy(includeComments = v) } }
            ToggleRow(stringResource(R.string.include_weight), options.includeWeight) { v -> viewModel.update { it.copy(includeWeight = v) } }
            ToggleRow(stringResource(R.string.include_due), options.includeDueDate) { v -> viewModel.update { it.copy(includeDueDate = v) } }
            ToggleRow(stringResource(R.string.include_number), options.includeHierarchyNumber) { v -> viewModel.update { it.copy(includeHierarchyNumber = v) } }

            Text(stringResource(R.string.orientation), style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !options.landscape, onClick = { viewModel.update { it.copy(landscape = false) } },
                    label = { Text(stringResource(R.string.a4_portrait)) })
                FilterChip(selected = options.landscape, onClick = { viewModel.update { it.copy(landscape = true) } },
                    label = { Text(stringResource(R.string.a4_landscape)) })
            }

            Text(stringResource(R.string.done_style), style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChecklistPdfGenerator.DoneStyle.entries.forEach { style ->
                    FilterChip(
                        selected = options.doneStyle == style,
                        onClick = { viewModel.update { it.copy(doneStyle = style) } },
                        label = { Text(doneStyleLabel(style)) },
                    )
                }
            }

            Button(
                onClick = { createPdf.launch("${(project?.title ?: "checklist")}.pdf") },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text(stringResource(R.string.generate_pdf)) }

            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun doneStyleLabel(style: ChecklistPdfGenerator.DoneStyle): String = when (style) {
    ChecklistPdfGenerator.DoneStyle.CHECKED -> stringResource(R.string.done_checked)
    ChecklistPdfGenerator.DoneStyle.STRIKETHROUGH -> stringResource(R.string.done_strike)
    ChecklistPdfGenerator.DoneStyle.STATUS_TEXT -> stringResource(R.string.done_text)
    ChecklistPdfGenerator.DoneStyle.SAME_AS_TODO -> stringResource(R.string.done_same)
}
