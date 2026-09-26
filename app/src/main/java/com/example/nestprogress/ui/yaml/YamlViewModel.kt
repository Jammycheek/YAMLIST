package com.example.nestprogress.ui.yaml

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nestprogress.data.file.yaml.ParsedProject
import com.example.nestprogress.data.file.yaml.YamlError
import com.example.nestprogress.data.file.yaml.YamlExporter
import com.example.nestprogress.data.file.yaml.YamlImportMode
import com.example.nestprogress.data.file.yaml.YamlImportService
import com.example.nestprogress.data.file.yaml.YamlImporter
import com.example.nestprogress.data.file.yaml.YamlParseResult
import com.example.nestprogress.data.repository.NestRepository
import com.example.nestprogress.domain.progress.TaskTreeBuilder
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
    val message: String? = null,
)

@HiltViewModel
class YamlViewModel @Inject constructor(
    private val repo: NestRepository,
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

    fun loadSample() = _state.update { it.copy(inputText = SAMPLE_YAML, preview = null, errors = emptyList()) }

    fun loadStructureSample() = _state.update {
        it.copy(inputText = SAMPLE_STRUCTURE_YAML, preview = null, errors = emptyList())
    }

    fun import(onDone: (Long) -> Unit) {
        val preview = _state.value.preview ?: return
        viewModelScope.launch {
            try {
                val id = importService.import(preview, _state.value.importMode)
                _state.update { it.copy(importDone = true, message = null) }
                onDone(id)
            } catch (e: Exception) {
                _state.update { it.copy(message = "インポートに失敗しました: ${e.message}") }
            }
        }
    }

    fun exportProject(id: Long) {
        viewModelScope.launch {
            val project = repo.getProjectOnce(id) ?: return@launch
            val tasks = repo.observeTasks(id).first()
            val forest = TaskTreeBuilder.build(tasks)
            _state.update { it.copy(exportText = YamlExporter.export(project, forest)) }
        }
    }

    companion object {
        val SAMPLE_YAML = """
            schema_version: 1
            project:
              title: "現場A / Site A"
              description: "空調自動制御試運転"
              progress_mode: "weight"
              tasks:
                - title: "事前準備"
                  tasks:
                    - title: "図面確認"
                      weight: 2
                      mark: "important"
                    - title: "I/Oリスト確認"
                      weight: 3
                - title: "現場作業"
                  tasks:
                    - title: "盤チェック"
                      weight: 2
                    - title: "自動制御試験"
                      weight: 8
                      mark: "warning"
        """.trimIndent()

        /**
         * Structure-only outline. Nested names are turned into a task tree on
         * import; weight/status are left at defaults and edited in the app.
         */
        val SAMPLE_STRUCTURE_YAML = """
            案件名: 昭和田中生命ビル新築
            種別: 工事
            階層:
              1階:
                東:
                  自動制御盤:
                    - CP-01-01
                  空調機:
                    AHU-01-01:
                      VAV:
                        - VAV101
                        - VAV102
                    AHU-01-02:
                  中央監視:
                西:
                  空調機:
                    - AHU-01-03
                    - AHU-01-04
        """.trimIndent()
    }
}
