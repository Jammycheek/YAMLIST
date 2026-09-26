package com.example.yamlist.ui.taskedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.yamlist.R
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.ui.common.ColorPickerRow
import com.example.yamlist.ui.common.MarkPickerRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    onDone: () -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (state.isNew) stringResource(R.string.add_task) else stringResource(R.string.edit_task))
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text(stringResource(R.string.title)) },
                isError = state.titleError,
                supportingText = { if (state.titleError) Text(stringResource(R.string.title_required)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = { Text(stringResource(R.string.description)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.fixedComment,
                onValueChange = viewModel::onComment,
                label = { Text(stringResource(R.string.fixed_comment)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.weightText,
                onValueChange = viewModel::onWeight,
                label = { Text(stringResource(R.string.weight)) },
                isError = state.weightError,
                supportingText = { if (state.weightError) Text(stringResource(R.string.weight_range)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.plannedMonth,
                onValueChange = viewModel::onPlannedMonth,
                label = { Text(stringResource(R.string.planned_month)) },
                placeholder = { Text("2026-08") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Text(stringResource(R.string.status), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskStatus.entries.forEach { s ->
                    FilterChip(
                        selected = state.status == s,
                        onClick = { viewModel.onStatus(s) },
                        label = { Text(statusLabel(s)) },
                    )
                }
            }

            MarkPickerRow(selected = state.mark, onSelect = viewModel::onMark, label = stringResource(R.string.mark))
            ColorPickerRow(selectedKey = state.colorCode, onSelect = viewModel::onColor, label = stringResource(R.string.color))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.progress_target), modifier = Modifier.weight(1f))
                Switch(checked = state.isProgressTarget, onCheckedChange = viewModel::onProgressTarget)
            }

            Button(
                onClick = { viewModel.save(onDone) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun statusLabel(s: TaskStatus): String = when (s) {
    TaskStatus.TODO -> stringResource(R.string.status_todo)
    TaskStatus.DONE -> stringResource(R.string.status_done)
    TaskStatus.HOLD -> stringResource(R.string.status_hold)
}
