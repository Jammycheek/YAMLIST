package com.example.nestprogress.ui.movetask

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.nestprogress.R

/**
 * SCR-11: choose a new parent for one task. Project root and every eligible
 * existing task (its own subtree excluded) are offered as destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveTaskScreen(
    onDone: () -> Unit,
    viewModel: MoveTaskViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.moved) {
        if (state.moved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.move_task_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Text(
                stringResource(R.string.move_task_subject_fmt, state.taskTitle),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )
            LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    DestinationRow(
                        label = stringResource(R.string.move_to_root),
                        depth = 0,
                        number = null,
                        highlighted = state.isCurrentlyRoot,
                        onClick = { viewModel.moveTo(null) },
                    )
                    HorizontalDivider()
                }
                items(state.candidates, key = { it.taskId }) { row ->
                    DestinationRow(
                        label = row.title,
                        depth = row.depth,
                        number = row.number,
                        highlighted = row.isCurrentParent,
                        onClick = { viewModel.moveTo(row.taskId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationRow(
    label: String,
    depth: Int,
    number: String?,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val indent = (depth.coerceAtMost(6) * 16).dp
    val base = Modifier
        .fillMaxWidth()
        .clickable(onClick = onClick)
    val withBackground =
        if (highlighted) base.background(MaterialTheme.colorScheme.secondaryContainer) else base
    Row(
        withBackground.padding(start = indent + 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!number.isNullOrEmpty()) {
            Text(
                number,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
