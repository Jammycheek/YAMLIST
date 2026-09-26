package com.example.yamlist.ui.taskdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.yamlist.R
import com.example.yamlist.domain.model.TaskStatus
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    onEdit: (projectId: Long, taskId: Long) -> Unit,
    onAddChild: (projectId: Long, parentId: Long) -> Unit,
    onBulkAddChild: (parentId: Long) -> Unit,
    onMoveTask: (taskId: Long) -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    val task = ui.task
    var commentDraft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(task?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (task == null) return@Scaffold
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FieldRow(stringResource(R.string.status), statusLabel(task.status))
            task.description?.takeIf { it.isNotBlank() }
                ?.let { FieldRow(stringResource(R.string.description), it) }
            task.fixedComment?.takeIf { it.isNotBlank() }
                ?.let { FieldRow(stringResource(R.string.fixed_comment), it) }
            if (task.isProgressTarget) FieldRow(stringResource(R.string.weight), trimWeight(task.weight))
            if (task.markType.glyph.isNotBlank()) FieldRow(stringResource(R.string.mark), task.markType.glyph)
            task.plannedYearMonth?.let { FieldRow(stringResource(R.string.planned_month), it) }
            task.dueDate?.let { FieldRow(stringResource(R.string.due_date), it.toString()) }
            task.completedAt?.let {
                FieldRow(stringResource(R.string.completed_at), it.format(DTF))
            }
            FieldRow(stringResource(R.string.created_at), task.createdAt.format(DTF))
            FieldRow(stringResource(R.string.updated_at), task.updatedAt.format(DTF))

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.children.isEmpty()) {
                    Button(onClick = { viewModel.toggleDone() }) {
                        Text(
                            if (task.status == TaskStatus.DONE) stringResource(R.string.mark_todo)
                            else stringResource(R.string.mark_done)
                        )
                    }
                    OutlinedButton(onClick = { viewModel.setHold() }) {
                        Text(stringResource(R.string.status_hold))
                    }
                }
                OutlinedButton(onClick = { onEdit(task.projectId, task.id) }) {
                    Text(stringResource(R.string.edit))
                }
            }
            OutlinedButton(onClick = { onBulkAddChild(task.id) }) {
                Text(stringResource(R.string.bulk_add_children))
            }
            OutlinedButton(onClick = { onAddChild(task.projectId, task.id) }) {
                Text(stringResource(R.string.add_child_task))
            }
            OutlinedButton(onClick = { onMoveTask(task.id) }) {
                Text(stringResource(R.string.move_to_other_parent))
            }

            if (ui.children.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.child_tasks), style = MaterialTheme.typography.titleSmall)
                ui.children.forEach { child ->
                    Text("• ${child.title}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()
            Text(stringResource(R.string.comments), style = MaterialTheme.typography.titleSmall)
            ui.comments.forEach { c ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "• ${c.body}",
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (c.struck) TextDecoration.LineThrough else null,
                        color = if (c.struck) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { viewModel.toggleCommentStruck(c.id, c.struck) },
                    )
                    IconButton(onClick = { viewModel.deleteComment(c.id) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                }
            }
            OutlinedTextField(
                value = commentDraft,
                onValueChange = { commentDraft = it },
                label = { Text(stringResource(R.string.add_comment)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.addComment(commentDraft); commentDraft = "" },
                enabled = commentDraft.isNotBlank(),
            ) { Text(stringResource(R.string.add_comment)) }

            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = { viewModel.delete(onBack) }) {
                Text(stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row {
        Text("$label：", style = MaterialTheme.typography.labelLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun statusLabel(s: TaskStatus): String = when (s) {
    TaskStatus.TODO -> stringResource(R.string.status_todo)
    TaskStatus.DONE -> stringResource(R.string.status_done)
    TaskStatus.HOLD -> stringResource(R.string.status_hold)
}

private val DTF = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
private fun trimWeight(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
