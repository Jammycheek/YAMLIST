package com.example.yamlist.ui.project

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.yamlist.R
import com.example.yamlist.domain.model.ProgressMode
import com.example.yamlist.ui.common.ProgressBar

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
    viewModel: ProjectListViewModel = hiltViewModel(),
) {
    val cards by viewModel.cards.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(cards, key = { it.project.id }) { card ->
                ProjectCard(
                    card = card,
                    onOpen = { onOpenProject(card.project.id) },
                    onEdit = { onEditProject(card.project.id) },
                    onArchive = { viewModel.archiveProject(card.project.id) },
                    onDelete = { viewModel.deleteProject(card.project.id) },
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ProjectCard(
    card: ProjectCardUi,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val p = card.project
    val prog = card.progress

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.edit)) },
                        onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.archive)) },
                        onClick = { menu = false; onArchive() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete)) },
                        onClick = { menu = false; onDelete() })
                }
            }
            Spacer(Modifier.height(6.dp))

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
