package com.example.yamlist.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.R
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.model.Task
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(repo: YamlistRepository) : ViewModel() {
    val grouped: StateFlow<List<Pair<LocalDate, List<Task>>>> =
        repo.observeCompletedHistory().map { tasks ->
            tasks.filter { it.completedAt != null }
                .groupBy { it.completedAt!!.toLocalDate() }
                .toSortedMap(reverseOrder())
                .map { (date, list) ->
                    date to list.sortedByDescending { it.completedAt }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val grouped by viewModel.grouped.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
        ) {
            grouped.forEach { (date, tasks) ->
                item(key = date.toString()) {
                    Text(date.format(dateFmt), style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(tasks.size, key = { tasks[it].id }) { i ->
                    val t = tasks[i]
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text("${t.completedAt!!.format(timeFmt)}  ${t.title}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
