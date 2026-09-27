package com.example.yamlist.ui.yaml

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.data.file.yaml.ParsedProject
import com.example.yamlist.data.file.yaml.YamlComment
import com.example.yamlist.data.file.yaml.YamlError
import com.example.yamlist.data.file.yaml.YamlExporter
import com.example.yamlist.data.file.yaml.YamlImportMode
import com.example.yamlist.data.file.yaml.YamlImportService
import com.example.yamlist.data.file.yaml.YamlImporter
import com.example.yamlist.data.file.yaml.YamlParseResult
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.progress.TaskTreeBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class YamlUiState(
    val inputText: String = "",
    val errors: List<YamlError> = emptyList(),
    val preview: ParsedProject? = null,
    val importMode: YamlImportMode = YamlImportMode.NEW,
    val importDone: Boolean = false,
    val exportText: String = "",
    /** Set when saving an import failed; holds the underlying error detail. */
    val importFailure: String? = null,
)

@HiltViewModel
class YamlViewModel @Inject constructor(
    private val repo: YamlistRepository,
    private val importService: YamlImportService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = (savedStateHandle.get<Long>("projectId") ?: -1L)

    private val _state = MutableStateFlow(YamlUiState())
    val state = _state.asStateFlow()

    init {
        if (projectId > 0) exportProject(projectId)
    }

    fun onInputText(text: String) = _state.update { it.copy(inputText = text, preview = null, errors = emptyList(), importDone = false) }

    fun onImportMode(mode: YamlImportMode) = _state.update { it.copy(importMode = mode) }

    fun validate() {
        when (val result = YamlImporter.parse(_state.value.inputText)) {
            is YamlParseResult.Success ->
                _state.update { it.copy(preview = result.project, errors = emptyList()) }
            is YamlParseResult.Failure ->
                _state.update { it.copy(preview = null, errors = result.errors) }
        }
    }

    /** Samples live in res/raw (English) and res/raw-ja, so the screen reads them in the app's language. */
    fun loadSample(text: String) = _state.update { it.copy(inputText = text, preview = null, errors = emptyList()) }

    fun import(onDone: (Long) -> Unit) {
        val preview = _state.value.preview ?: return
        viewModelScope.launch {
            try {
                val id = importService.import(preview, _state.value.importMode)
                _state.update { it.copy(importDone = true, importFailure = null) }
                onDone(id)
            } catch (e: Exception) {
                _state.update { it.copy(importFailure = e.message.orEmpty()) }
            }
        }
    }

    fun exportProject(id: Long) {
        viewModelScope.launch {
            val project = repo.getProjectOnce(id) ?: return@launch
            val tasks = repo.observeTasks(id).first()
            val forest = TaskTreeBuilder.build(tasks)
            val comments = repo.commentsForProject(id)
                .mapValues { (_, list) -> list.map { YamlComment(it.body, it.struck) } }
            _state.update {
                it.copy(exportText = YamlExporter.export(project, forest, commentsByTask = comments))
            }
        }
    }
}
