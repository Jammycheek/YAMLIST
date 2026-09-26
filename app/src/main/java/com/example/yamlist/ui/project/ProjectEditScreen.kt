package com.example.yamlist.ui.project

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.yamlist.R
import com.example.yamlist.domain.model.ProgressMode
import com.example.yamlist.ui.common.ColorPickerRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditScreen(
    onDone: () -> Unit,
    viewModel: ProjectEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.isNew) stringResource(R.string.new_project)
                        else stringResource(R.string.edit_project)
                    )
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
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text(stringResource(R.string.project_name)) },
                isError = state.titleError,
                supportingText = { if (state.titleError) Text(stringResource(R.string.title_required)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescription,
                label = { Text(stringResource(R.string.description)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.progress_mode), style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgressMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.progressMode == mode,
                        onClick = { viewModel.onProgressMode(mode) },
                        label = { Text(progressModeLabel(mode)) },
                    )
                }
            }

            ColorPickerRow(
                selectedKey = state.colorCode,
                onSelect = viewModel::onColor,
                label = stringResource(R.string.color),
            )

            Button(
                onClick = { viewModel.save(onDone) },
                enabled = !state.titleError,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun progressModeLabel(mode: ProgressMode): String = when (mode) {
    ProgressMode.COUNT -> stringResource(R.string.mode_count)
    ProgressMode.WEIGHT -> stringResource(R.string.mode_weight)
    ProgressMode.BOTH -> stringResource(R.string.mode_both)
}
