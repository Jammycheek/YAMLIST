package com.example.yamlist.ui.weightsheet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.yamlist.R
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightSheetScreen(
    onDone: () -> Unit,
    viewModel: WeightSheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    fun tryBack() {
        if (state.saving) return
        if (state.hasChanges) confirmDiscard = true else onDone()
    }
    BackHandler(enabled = state.saving || state.hasChanges) { tryBack() }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weight_sheet_title)) },
                navigationIcon = {
                    IconButton(onClick = ::tryBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(modifier = Modifier.navigationBarsPadding(), shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        val total = state.total
                        Text(
                            if (total == null) stringResource(R.string.weight_sheet_invalid)
                            else stringResource(R.string.weight_sheet_total,
                                WeightSheetRules.display(total)),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        if (total != null) Text(
                            stringResource(R.string.weight_sheet_target_count, state.targetCount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = state.loaded && !state.saving && !state.hasInvalid &&
                            state.error == null,
                    ) { Text(stringResource(R.string.save)) }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                state.projectTitle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.weight_sheet_hint),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.error != null) {
                Text(
                    stringResource(
                        if (state.error == WeightSheetError.CONFLICT)
                            R.string.weight_sheet_conflict else R.string.weight_sheet_failed
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.title), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.weight_sheet_target), Modifier.width(44.dp),
                    style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.weight), Modifier.width(84.dp),
                    style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.weight_sheet_share), Modifier.width(52.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
            HorizontalDivider()
            if (state.loaded && state.rows.isEmpty()) {
                Text(stringResource(R.string.weight_sheet_empty), Modifier.padding(16.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.rows, key = { it.id }) { row ->
                    WeightSheetItem(
                        row = row,
                        value = state.drafts[row.id].orEmpty(),
                        isTarget = state.isTarget(row),
                        share = state.share(row),
                        enabled = !state.saving && state.error == null,
                        onValueChange = { viewModel.changeWeight(row.id, it) },
                        onTargetChange = { viewModel.changeTarget(row.id, it) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text(stringResource(R.string.weight_sheet_discard_title)) },
        text = { Text(stringResource(R.string.weight_sheet_discard_body)) },
        confirmButton = {
            TextButton(onClick = { confirmDiscard = false; onDone() }) {
                Text(stringResource(R.string.weight_sheet_discard))
            }
        },
        dismissButton = {
            TextButton(onClick = { confirmDiscard = false }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun WeightSheetItem(
    row: WeightSheetRow,
    value: String,
    isTarget: Boolean,
    share: Double?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onTargetChange: (Boolean) -> Unit,
) {
    val valid = !row.isLeaf || WeightSheetRules.parse(value) != null
    val indent = (row.depth.coerceAtMost(4) * 12).dp
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f).padding(start = indent)) {
            Text(
                "${row.number}  ${row.title}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (row.isLeaf) FontWeight.Normal else FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.isLeaf && !isTarget) Text(
                stringResource(R.string.weight_sheet_excluded),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.isLeaf) {
            Checkbox(
                checked = isTarget,
                onCheckedChange = onTargetChange,
                modifier = Modifier.width(44.dp).semantics {
                    contentDescription = row.title
                },
                enabled = enabled,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(84.dp),
                enabled = enabled,
                isError = !valid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
            )
        } else {
            Text("", Modifier.width(44.dp))
            Text("—", Modifier.width(84.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            share?.let { String.format(Locale.getDefault(), "%.1f%%", it) } ?: "—",
            Modifier.width(52.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
