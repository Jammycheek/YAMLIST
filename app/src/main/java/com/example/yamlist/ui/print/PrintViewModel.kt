package com.example.yamlist.ui.print

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.data.file.pdf.ChecklistPdfGenerator
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

@HiltViewModel
class PrintViewModel @Inject constructor(
    private val repo: YamlistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val projectId: Long = savedStateHandle.get<Long>("projectId") ?: -1L
    private val generator = ChecklistPdfGenerator()

    private val _project = MutableStateFlow<Project?>(null)
    val project = _project.asStateFlow()

    private val _options = MutableStateFlow(ChecklistPdfGenerator.Options())
    val options = _options.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        viewModelScope.launch { _project.value = repo.getProjectOnce(projectId) }
    }

    fun update(transform: (ChecklistPdfGenerator.Options) -> ChecklistPdfGenerator.Options) =
        _options.update(transform)

    /** Streams the PDF into the SAF-provided output stream (spec §21.3 keeps partials out). */
    fun generateTo(openStream: () -> OutputStream?) {
        viewModelScope.launch {
            try {
                val project = _project.value ?: return@launch
                val tasks = repo.observeTasks(projectId).first()
                withContext(Dispatchers.IO) {
                    openStream()?.use { out ->
                        generator.generate(project, tasks, _options.value, out)
                    } ?: return@withContext
                }
                _message.value = "PDFを保存しました。"
            } catch (e: Exception) {
                _message.value = "PDF生成に失敗しました: ${e.message}"
            }
        }
    }
}
