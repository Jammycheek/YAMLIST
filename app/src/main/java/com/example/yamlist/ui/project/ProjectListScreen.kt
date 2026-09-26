package com.example.yamlist.ui.project

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.yamlist.R
import com.example.yamlist.domain.model.ProgressMode
import com.example.yamlist.domain.model.ProjectGroup
import com.example.yamlist.ui.common.ProgressBar

/** Which name dialog is open: a new group (optionally moving a project into it) or a rename. */
private sealed interface GroupNameRequest {
    data class Create(val projectId: Long?) : GroupNameRequest
    data class Rename(val group: ProjectGroup) : GroupNameRequest
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    onOpenProject: (Long) -> Unit,
    onNewProject: () -> Unit,
    onEditProject: (Long) -> Unit,
    onOpenYaml: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenArchive: () -> Unit,
    viewModel: ProjectListViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var nameRequest by remember { mutableStateOf<GroupNameRequest?>(null) }
    var moveTarget by remember { mutableStateOf<ProjectCardUi?>(null) }
    var deleteGroupTarget by remember { mutableStateOf<ProjectGroup?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenArchive) {
                        Icon(Icons.Default.Archive, contentDescription = stringResource(R.string.archive_list))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.new_group)) },
                            onClick = { menuOpen = false; nameRequest = GroupNameRequest.Create(null) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.yaml_io)) },
                            onClick = { menuOpen = false; onOpenYaml() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.history)) },
                            onClick = { menuOpen = false; onOpenHistory() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.backup_restore)) },
                            onClick = { menuOpen = false; onOpenBackup() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.settings)) },
                            onClick = { menuOpen = false; onOpenSettings() })
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewProject) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_project))
            }
        },
    ) { padding ->
        @Composable
        fun ListCard(card: ProjectCardUi) = ProjectCard(
            card = card,
            onOpen = { onOpenProject(card.project.id) },
        ) { dismiss ->
            DropdownMenuItem(text = { Text(stringResource(R.string.edit)) },
                onClick = { dismiss(); onEditProject(card.project.id) })
            DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) },
                onClick = { dismiss(); viewModel.moveProjectUp(card.project.id) })
            DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) },
                onClick = { dismiss(); viewModel.moveProjectDown(card.project.id) })
            DropdownMenuItem(text = { Text(stringResource(R.string.move_to_group)) },
                onClick = { dismiss(); moveTarget = card })
            DropdownMenuItem(text = { Text(stringResource(R.string.archive)) },
                onClick = { dismiss(); viewModel.archiveProject(card.project.id) })
            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) },
                onClick = { dismiss(); viewModel.deleteProject(card.project.id) })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ui.ungrouped, key = { it.project.id }) { card -> ListCard(card) }

            ui.groups.forEach { section ->
                item(key = "group-${section.group.id}") {
                    GroupHeader(
                        section = section,
                        onToggle = { viewModel.toggleGroupCollapsed(section.group) },
                        onRename = { nameRequest = GroupNameRequest.Rename(section.group) },
                        onMoveUp = { viewModel.moveGroupUp(section.group.id) },
                        onMoveDown = { viewModel.moveGroupDown(section.group.id) },
                        onDelete = { deleteGroupTarget = section.group },
                    )
                }
                if (!section.group.isCollapsed) {
                    items(section.cards, key = { it.project.id }) { card ->
                        Box(Modifier.padding(start = 12.dp)) { ListCard(card) }
                    }
                }
            }
        }
    }

    nameRequest?.let { request ->
        GroupNameDialog(
            title = stringResource(
                if (request is GroupNameRequest.Rename) R.string.rename_group else R.string.new_group
            ),
            initial = (request as? GroupNameRequest.Rename)?.group?.title.orEmpty(),
            onDismiss = { nameRequest = null },
            onConfirm = { name ->
                when (request) {
                    is GroupNameRequest.Create -> viewModel.createGroup(name, request.projectId)
                    is GroupNameRequest.Rename -> viewModel.renameGroup(request.group.id, name)
                }
                nameRequest = null
            },
        )
    }

    moveTarget?.let { card ->
        MoveToGroupDialog(
            projectTitle = card.project.title,
            currentGroupId = card.project.groupId,
            groups = ui.groups.map { it.group },
            onDismiss = { moveTarget = null },
            onSelect = { groupId ->
                viewModel.moveProjectToGroup(card.project.id, groupId)
                moveTarget = null
            },
            onCreateGroup = {
                moveTarget = null
                nameRequest = GroupNameRequest.Create(card.project.id)
            },
        )
    }

    deleteGroupTarget?.let { group ->
        AlertDialog(
            onDismissRequest = { deleteGroupTarget = null },
            title = { Text(stringResource(R.string.delete_group)) },
            text = { Text(stringResource(R.string.delete_group_body_fmt, group.title)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteGroup(group.id); deleteGroupTarget = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteGroupTarget = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun GroupHeader(
    section: GroupSectionUi,
    onToggle: () -> Unit,
    onRename: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val group = section.group
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (group.isCollapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.expand_collapse),
            )
            Text(
                group.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(start = 4.dp),
            )
            Text(
                "（${section.cards.size}）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.rename_group)) },
                        onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_up)) },
                        onClick = { menu = false; onMoveUp() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.move_down)) },
                        onClick = { menu = false; onMoveDown() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_group)) },
                        onClick = { menu = false; onDelete() })
                }
            }
        }
        HorizontalDivider()
    }
}

/**
 * Project summary card shared by the project list and the archive list.
 * [menuContent] fills the card's overflow menu; call `dismiss` before acting.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProjectCard(
    card: ProjectCardUi,
    onOpen: () -> Unit,
    menuContent: @Composable (dismiss: () -> Unit) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val p = card.project
    val prog = card.progress

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        menuContent { menu = false }
                    }
                }
            }

            Column(Modifier.padding(end = 10.dp)) {
                if (!prog.hasTargets) {
                    Text(stringResource(R.string.no_progress_target), style = MaterialTheme.typography.bodyMedium)
                } else {
                    if (p.progressMode == ProgressMode.WEIGHT || p.progressMode == ProgressMode.BOTH) {
                        Text(stringResource(R.string.weight_progress_fmt, prog.weightPercent),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (p.progressMode == ProgressMode.COUNT || p.progressMode == ProgressMode.BOTH) {
                        Text(stringResource(R.string.count_progress_fmt, prog.countDone, prog.countTotal),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }

                p.dueDate?.let {
                    Text(stringResource(R.string.due_fmt, it.toString()), style = MaterialTheme.typography.bodySmall)
                }
                if (card.importantCount > 0 || card.confirmCount > 0) {
                    Text(
                        stringResource(R.string.marks_fmt, card.importantCount, card.confirmCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                val fraction = if (p.progressMode == ProgressMode.COUNT) prog.countFraction.toFloat()
                else prog.weightFraction.toFloat()
                ProgressBar(fraction = fraction)
            }
        }
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.group_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun MoveToGroupDialog(
    projectTitle: String,
    currentGroupId: Long?,
    groups: List<ProjectGroup>,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit,
    onCreateGroup: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_group)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.move_task_subject_fmt, projectTitle),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                GroupChoice(stringResource(R.string.no_group), currentGroupId == null) { onSelect(null) }
                groups.forEach { g ->
                    GroupChoice(g.title, currentGroupId == g.id) { onSelect(g.id) }
                }
                GroupChoice("＋ " + stringResource(R.string.new_group), false, onCreateGroup)
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun GroupChoice(label: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text = if (current) "● $label" else label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !current, onClick = onClick)
            .padding(vertical = 10.dp),
    )
}
