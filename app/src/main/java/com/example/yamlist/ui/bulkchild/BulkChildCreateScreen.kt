package com.example.yamlist.ui.bulkchild

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.yamlist.R
import com.example.yamlist.domain.bulk.BulkTitleParser
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.ui.common.ColorPickerRow
import com.example.yamlist.ui.common.MarkPickerRow

/**
 * SCR-10: creates many children of one parent at once (spec §1.3).
 *
 * Titles come from a multi-line box, one per line; every other attribute is set
 * once and applied to the whole batch. Per-task differences are made afterwards
 * in the normal task edit screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkChildCreateScreen(
    onDone: () -> Unit,
    viewModel: BulkChildCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bulk_add_children)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.bulk_parent_fmt, state.parentTitle),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = state.inputText,
                onValueChange = viewModel::onInputText,
                label = { Text(stringResource(R.string.bulk_titles_label)) },
                supportingText = { Text(stringResource(R.string.bulk_titles_hint)) },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.stripBullets, onCheckedChange = viewModel::onStripBullets)
                Text(
                    stringResource(R.string.bulk_strip_bullets),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            ProblemList(state.problems)
            PreviewCard(state)

            HorizontalDivider()
            Text(
                stringResource(R.string.bulk_shared_attributes),
                style = MaterialTheme.typography.titleMedium,
            )

            OutlinedTextField(
                value = state.weightText,
                onValueChange = viewModel::onWeightText,
                label = { Text(stringResource(R.string.weight)) },
                isError = state.weightError,
                supportingText = {
                    if (state.weightError) Text(stringResource(R.string.weight_range))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.status), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskStatus.entries.forEach { status ->
                    FilterChip(
                        selected = state.status == status,
                        onClick = { viewModel.onStatus(status) },
                        label = {
                            Text(
                                when (status) {
                                    TaskStatus.TODO -> stringResource(R.string.status_todo)
                                    TaskStatus.DONE -> stringResource(R.string.status_done)
                                    TaskStatus.HOLD -> stringResource(R.string.status_hold)
                                }
                            )
                        },
                    )
                }
            }

            MarkPickerRow(
                selected = state.markType,
                onSelect = viewModel::onMark,
                label = stringResource(R.string.mark),
            )
            ColorPickerRow(
                selectedKey = state.colorKey,
                onSelect = viewModel::onColor,
                label = stringResource(R.string.color),
            )

            OutlinedTextField(
                value = state.plannedMonth,
                onValueChange = viewModel::onPlannedMonth,
                label = { Text(stringResource(R.string.planned_month)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.dueDate,
                onValueChange = viewModel::onDueDate,
                label = { Text(stringResource(R.string.due_date_input)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.fixedComment,
                onValueChange = viewModel::onFixedComment,
                label = { Text(stringResource(R.string.fixed_comment)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = state.isProgressTarget,
                    onCheckedChange = viewModel::onProgressTarget,
                )
                Text(
                    stringResource(R.string.progress_target),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = { viewModel.save(onDone) },
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.bulk_create_fmt, state.count))
            }
        }
    }
}

/** Validation problems, phrased so the user knows which line to fix. */
@Composable
private fun ProblemList(problems: List<BulkTitleParser.Problem>) {
    if (problems.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        problems.forEach { problem ->
            val text = when (problem) {
                is BulkTitleParser.Problem.Empty ->
                    stringResource(R.string.bulk_error_empty)

                is BulkTitleParser.Problem.TooLong ->
                    stringResource(
                        R.string.bulk_error_too_long_fmt,
                        problem.lineNumber,
                        BulkTitleParser.MAX_TITLE_LENGTH,
                    )

                is BulkTitleParser.Problem.TooMany ->
                    stringResource(
                        R.string.bulk_error_too_many_fmt,
                        BulkTitleParser.MAX_COUNT,
                        problem.count,
                    )
            }
            Text(text, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Exactly what will be created, numbered as in spec §1.7. */
@Composable
private fun PreviewCard(state: BulkChildUiState) {
    if (state.titles.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.bulk_preview_fmt, state.count),
                style = MaterialTheme.typography.titleSmall,
            )
            state.titles.forEachIndexed { index, title ->
                Text("${index + 1}. $title", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
