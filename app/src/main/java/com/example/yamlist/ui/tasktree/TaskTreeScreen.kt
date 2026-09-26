package com.example.yamlist.ui.tasktree

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.yamlist.R
import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.ParentDisplayState
import com.example.yamlist.domain.model.ProgressMode
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.ui.common.MarkChip
import com.example.yamlist.ui.common.NumberColorBadge
import com.example.yamlist.ui.common.ProgressBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTreeScreen(
    onBack: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onAddRoot: () -> Unit,
    onAddChild: (Long) -> Unit,
    onBulkAddChild: (Long) -> Unit,
    onMoveTask: (Long) -> Unit,
    onEditTask: (Long) -> Unit,
    onExportYaml: () -> Unit,
    onPrint: () -> Unit,
    viewModel: TaskTreeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var topMenu by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var promoteTarget by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.project?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onPrint) {
                        Icon(Icons.Default.Print, contentDescription = stringResource(R.string.print))
                    }
                    IconButton(onClick = { topMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.export_yaml)) },
                            onClick = { topMenu = false; onExportYaml() })
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRoot) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_root_task))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OverallHeader(state)
            ExpandControls(
                onExpandAll = viewModel::expandAll,
                onCollapseAll = viewModel::collapseAll,
                onExpandOne = viewModel::expandOneLevel,
                onCollapseOne = viewModel::collapseOneLevel,
            )
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
            ) {
                items(state.rows, key = { it.node.task.id }) { row ->
                    TaskRow(
                        row = row,
                        onToggleDone = { viewModel.toggleDone(row.node.task.id) },
                        onToggleSubtreeDone = { allDone ->
                            viewModel.toggleSubtreeDone(row.node.task.id, allDone)
                        },
                        onToggleExpand = { viewModel.toggleExpand(row.node.task.id) },
                        onOpen = { onOpenTask(row.node.task.id) },
                        onAddChild = { onAddChild(row.node.task.id) },
                        onBulkAddChild = { onBulkAddChild(row.node.task.id) },
                        onMove = { onMoveTask(row.node.task.id) },
                        onMoveUp = { viewModel.moveSiblingUp(row.node.task.id) },
                        onMoveDown = { viewModel.moveSiblingDown(row.node.task.id) },
                        onEdit = { onEditTask(row.node.task.id) },
                        onSetColor = { key -> viewModel.setColor(row.node.task.id, key) },
                        onClearMark = { viewModel.clearMark(row.node.task.id) },
                        onBulk = { status -> viewModel.setSubtreeStatus(row.node.task.id, status) },
                        onDelete = {
                            scope.launch {
                                val n = viewModel.descendantCount(row.node.task.id)
                                deleteTarget = row.node.task.id to n
                            }
                        },
                        onDeletePromotingChildren = { promoteTarget = row.node.task.id },
                    )
                }
            }
        }
    }

    promoteTarget?.let { id ->
        AlertDialog(
            onDismissRequest = { promoteTarget = null },
            title = { Text(stringResource(R.string.promote_children_title)) },
            text = { Text(stringResource(R.string.promote_children_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTaskPromotingChildren(id); promoteTarget = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { promoteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    deleteTarget?.let { (id, count) ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_with_children_fmt, count)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTask(id); deleteTarget = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun OverallHeader(state: TaskTreeUiState) {
    val mode = state.project?.progressMode ?: ProgressMode.BOTH
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        val p = state.overall
        val text = when {
            !p.hasTargets -> stringResource(R.string.no_progress_target)
            mode == ProgressMode.COUNT -> stringResource(R.string.count_progress_fmt, p.countDone, p.countTotal)
            mode == ProgressMode.WEIGHT -> stringResource(R.string.weight_progress_fmt, p.weightPercent)
            else -> stringResource(R.string.weight_progress_fmt, p.weightPercent) + "　" +
                stringResource(R.string.count_progress_fmt, p.countDone, p.countTotal)
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        val fraction = if (mode == ProgressMode.COUNT) p.countFraction.toFloat() else p.weightFraction.toFloat()
        ProgressBar(fraction = fraction, height = 10.dp)
        if (state.depthWarning) {
            Text(stringResource(R.string.depth_warning), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Whole-tree expansion controls.
 *
 * The two "one level" actions step the tree in and out a layer at a time, which
 * is what you want on a deep site structure where "expand everything" produces
 * far more rows than fit on a phone.
 */
@Composable
private fun ExpandControls(
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onExpandOne: () -> Unit,
    onCollapseOne: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val padding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        TextButton(onClick = onExpandAll, contentPadding = padding) {
            Text(stringResource(R.string.expand_all), style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = onCollapseAll, contentPadding = padding) {
            Text(stringResource(R.string.collapse_all), style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = onExpandOne, contentPadding = padding) {
            Text(stringResource(R.string.expand_one_level), style = MaterialTheme.typography.labelMedium)
        }
        TextButton(onClick = onCollapseOne, contentPadding = padding) {
            Text(stringResource(R.string.collapse_one_level), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    row: TreeRow,
    onToggleDone: () -> Unit,
    onToggleSubtreeDone: (allDone: Boolean) -> Unit,
    onToggleExpand: () -> Unit,
    onOpen: () -> Unit,
    onAddChild: () -> Unit,
    onBulkAddChild: () -> Unit,
    onMove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onSetColor: (String?) -> Unit,
    onClearMark: () -> Unit,
    onBulk: (TaskStatus) -> Unit,
    onDelete: () -> Unit,
    onDeletePromotingChildren: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val task = row.node.task
    // Cap indentation so deep levels don't starve the text column (spec §14.2).
    val indent = (row.depth.coerceAtMost(6) * 16).dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
            .padding(start = indent, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Expand arrow only where there is something to open; leaves keep the
        // same gap so every checkbox in a level stays vertically aligned.
        if (row.isLeaf) {
            Spacer(Modifier.width(32.dp))
        } else {
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                Icon(
                    if (row.expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.expand_collapse),
                )
            }
        }

        // Fixed-width slot so checkboxes stay aligned whether or not a task has comments.
        Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (row.hasComments) {
                Text("💬", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (row.isLeaf) {
            Checkbox(
                checked = task.status == TaskStatus.DONE,
                onCheckedChange = { onToggleDone() },
            )
        } else {
            // Parents show the state of everything underneath: fully done, part
            // way, or untouched. Ticking completes the subtree, unticking clears it.
            val allDone = row.parentState == ParentDisplayState.DONE
            TriStateCheckbox(
                state = when (row.parentState) {
                    ParentDisplayState.DONE -> ToggleableState.On
                    ParentDisplayState.IN_PROGRESS -> ToggleableState.Indeterminate
                    else -> ToggleableState.Off
                },
                onClick = { onToggleSubtreeDone(allDone) },
            )
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.localNumber.isNotEmpty()) {
                    NumberColorBadge(
                        number = row.localNumber,
                        colorKey = task.colorCode,
                        onClick = onOpen,
                        onSelectColor = onSetColor,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                MarkChip(task.markType)
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            // Resolve composable strings before entering the plain buildString lambda.
            val parentLabel = if (!row.isLeaf) parentStateLabel(row.parentState) else ""
            val holdLabel = stringResource(R.string.status_hold)
            val weightLabel = if (task.weight != 1.0 && task.isProgressTarget)
                stringResource(R.string.weight_fmt, trimWeight(task.weight)) else ""
            val sub = buildString {
                if (!row.isLeaf) {
                    append(parentLabel)
                    if (row.progress.hasTargets) append("　${row.progress.weightPercent}%")
                } else {
                    if (task.status == TaskStatus.HOLD) append(holdLabel)
                    if (weightLabel.isNotEmpty()) {
                        if (isNotEmpty()) append("　")
                        append(weightLabel)
                    }
                }
                task.dueDate?.let {
                    if (isNotEmpty()) append("　")
                    append(it.toString())
                }
            }
            if (sub.isNotBlank()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.add_child_task)) },
                    onClick = { menu = false; onAddChild() })
                DropdownMenuItem(text = { Text(stringResource(R.string.bulk_add_children)) },
                    onClick = { menu = false; onBulkAddChild() })
                DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) },
                    onClick = { menu = false; onMoveUp() })
                DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) },
                    onClick = { menu = false; onMoveDown() })
                DropdownMenuItem(text = { Text(stringResource(R.string.move_to_other_parent)) },
                    onClick = { menu = false; onMove() })
                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) },
                    onClick = { menu = false; onEdit() })
                if (task.markType != MarkType.NONE) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_mark)) },
                        onClick = { menu = false; onClearMark() })
                }
                if (!row.isLeaf) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.mark_all_done)) },
                        onClick = { menu = false; onBulk(TaskStatus.DONE) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.mark_all_todo)) },
                        onClick = { menu = false; onBulk(TaskStatus.TODO) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.mark_all_hold)) },
                        onClick = { menu = false; onBulk(TaskStatus.HOLD) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) },
                    onClick = { menu = false; onDelete() })
                if (!row.isLeaf) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_promoting_children)) },
                        onClick = { menu = false; onDeletePromotingChildren() },
                    )
                }
            }
        }
    }
}

@Composable
private fun parentStateLabel(state: ParentDisplayState): String = when (state) {
    ParentDisplayState.NOT_STARTED -> stringResource(R.string.state_not_started)
    ParentDisplayState.IN_PROGRESS -> stringResource(R.string.state_in_progress)
    ParentDisplayState.DONE -> stringResource(R.string.state_done)
    ParentDisplayState.NO_TARGET -> stringResource(R.string.no_progress_target)
}

private fun trimWeight(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
