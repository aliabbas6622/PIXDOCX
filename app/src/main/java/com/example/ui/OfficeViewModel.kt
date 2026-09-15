package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.OfficeDatabase
import com.example.data.model.DocumentType
import com.example.data.model.OfficeDocument
import com.example.data.model.SlideDeck
import com.example.data.model.SlideItem
import com.example.data.model.SlideLayout
import com.example.data.model.SpreadsheetGrid
import com.example.data.repository.OfficeRepository
import com.example.util.FileImporter
import com.example.util.PerformanceMetrics
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HubTab {
    ALL, DOCS, SHEETS, SLIDES, PINNED
}

enum class SortOrder {
    DATE_DESC, DATE_ASC, NAME_ASC, TYPE
}

data class EditorSaveState(
    val status: String = "Saved",
    val lastSavedTime: Long = System.currentTimeMillis()
)

@OptIn(FlowPreview::class)
class OfficeViewModel(application: Application) : AndroidViewModel(application) {

    private val database = OfficeDatabase.getDatabase(application, viewModelScope)
    private val repository = OfficeRepository(database.officeDocumentDao())

    // Hub State
    private val _selectedTab = MutableStateFlow(HubTab.ALL)
    val selectedTab: StateFlow<HubTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val allDocsFlow = repository.allDocuments

    // Debounce keystrokes so typing in the search field does not re-filter
    // (and re-sort) the full document list on every character.
    private val debouncedSearchQuery = _searchQuery
        .debounce { query -> if (query.isBlank()) 0L else 250L }

    val documentList: StateFlow<List<OfficeDocument>> = combine(
        allDocsFlow,
        _selectedTab,
        debouncedSearchQuery,
        _sortOrder
    ) { docs, tab, query, sort ->
        val filtered = docs.filter { doc ->
            val matchesTab = when (tab) {
                HubTab.ALL -> true
                HubTab.DOCS -> doc.type == DocumentType.DOC
                HubTab.SHEETS -> doc.type == DocumentType.XLS
                HubTab.SLIDES -> doc.type == DocumentType.PPT
                HubTab.PINNED -> doc.isPinned
            }
            val matchesQuery = query.isBlank() ||
                    doc.title.contains(query, ignoreCase = true) ||
                    doc.content.contains(query, ignoreCase = true)
            matchesTab && matchesQuery
        }

        when (sort) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.lastModified }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.lastModified }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.TYPE -> filtered.sortedBy { it.type.name }
        }
    }
        // Filter + sort off the main thread; combine blocks can be expensive
        // with large document lists (content is searched too).
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Editor State
    private val _currentDocument = MutableStateFlow<OfficeDocument?>(null)
    val currentDocument: StateFlow<OfficeDocument?> = _currentDocument.asStateFlow()

    private val _saveState = MutableStateFlow(EditorSaveState())
    val saveState: StateFlow<EditorSaveState> = _saveState.asStateFlow()

    // Undo / Redo for Doc Editor
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Presentation Presentation Mode (Slide Show)
    private val _isPresenting = MutableStateFlow(false)
    val isPresenting: StateFlow<Boolean> = _isPresenting.asStateFlow()

    private var autoSaveJob: Job? = null

    fun selectTab(tab: HubTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun openDocument(document: OfficeDocument) {
        _currentDocument.value = document
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        _saveState.value = EditorSaveState(status = "Saved", lastSavedTime = document.lastModified)
    }

    fun closeDocument() {
        // Save immediately before exiting
        saveCurrentDocumentNow()
        _currentDocument.value = null
        _isPresenting.value = false
    }

    fun togglePresentationMode(presenting: Boolean) {
        _isPresenting.value = presenting
    }

    fun updateDocumentTitle(newTitle: String) {
        val current = _currentDocument.value ?: return
        if (newTitle.isBlank() || newTitle == current.title) return
        val updated = current.copy(title = newTitle, lastModified = System.currentTimeMillis())
        _currentDocument.value = updated
        viewModelScope.launch {
            repository.update(updated)
        }
    }

    fun updateDocumentContent(newContent: String, trackHistory: Boolean = true) {
        val current = _currentDocument.value ?: return
        if (newContent == current.content) return

        if (trackHistory) {
            undoStack.add(current.content)
            if (undoStack.size > 50) undoStack.removeAt(0)
            redoStack.clear()
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = false
        }

        val wordCount = if (current.type == DocumentType.DOC) {
            countWords(newContent)
        } else current.wordCount

        val updated = current.copy(
            content = newContent,
            wordCount = wordCount,
            lastModified = System.currentTimeMillis()
        )
        _currentDocument.value = updated
        _saveState.value = EditorSaveState(status = "Saving...")

        // Debounce auto-save
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(600)
            repository.update(updated)
            _saveState.value = EditorSaveState(status = "Saved", lastSavedTime = System.currentTimeMillis())
        }
    }

    fun performUndo() {
        val current = _currentDocument.value ?: return
        if (undoStack.isNotEmpty()) {
            val previousContent = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(current.content)
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
            updateDocumentContent(previousContent, trackHistory = false)
        }
    }

    fun performRedo() {
        val current = _currentDocument.value ?: return
        if (redoStack.isNotEmpty()) {
            val nextContent = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(current.content)
            _canUndo.value = true
            _canRedo.value = redoStack.isNotEmpty()
            updateDocumentContent(nextContent, trackHistory = false)
        }
    }

    fun saveCurrentDocumentNow() {
        val current = _currentDocument.value ?: return
        autoSaveJob?.cancel()
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            repository.update(current)
            PerformanceMetrics.record(PerformanceMetrics.Keys.SAVE_OPERATION_TIME, System.currentTimeMillis() - startTime)
            _saveState.value = EditorSaveState(status = "Saved", lastSavedTime = System.currentTimeMillis())
        }
    }

    fun createNewDocument(title: String, type: DocumentType, initialContent: String = "") {
        viewModelScope.launch {
            val content = if (initialContent.isNotBlank()) initialContent else when (type) {
                DocumentType.DOC -> "# $title\n\nStart typing your document here..."
                DocumentType.XLS -> SpreadsheetGrid().serialize()
                DocumentType.PPT -> {
                    val deck = SlideDeck()
                    deck.slides.add(
                        SlideItem(
                            layout = SlideLayout.TITLE_SLIDE,
                            title = title,
                            subtitle = "Subtitle or presenter details"
                        )
                    )
                    deck.serialize()
                }
                DocumentType.PDF -> "# $title\n\nDocument Notes"
            }

            val doc = OfficeDocument(
                title = title.ifBlank { "Untitled ${type.name}" },
                type = type,
                content = content,
                category = when (type) {
                    DocumentType.DOC -> "Docs"
                    DocumentType.XLS -> "Sheets"
                    DocumentType.PPT -> "Slides"
                    DocumentType.PDF -> "PDF"
                },
                wordCount = if (type == DocumentType.DOC) 5 else 0,
                sheetRows = if (type == DocumentType.XLS) 10 else 0,
                slideCount = if (type == DocumentType.PPT) 1 else 0,
                sizeLabel = "8 KB"
            )
            val newId = repository.insert(doc)
            val created = repository.getDocumentByIdDirect(newId)
            if (created != null) {
                openDocument(created)
            }
        }
    }

    fun togglePin(doc: OfficeDocument) {
        viewModelScope.launch {
            repository.setPinned(doc.id, !doc.isPinned)
            if (_currentDocument.value?.id == doc.id) {
                _currentDocument.value = _currentDocument.value?.copy(isPinned = !doc.isPinned)
            }
        }
    }

    fun renameDocument(id: Long, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.rename(id, newTitle)
            if (_currentDocument.value?.id == id) {
                _currentDocument.value = _currentDocument.value?.copy(title = newTitle)
            }
        }
    }

    fun deleteDocument(doc: OfficeDocument) {
        viewModelScope.launch {
            repository.delete(doc)
            if (_currentDocument.value?.id == doc.id) {
                _currentDocument.value = null
            }
        }
    }

    fun duplicateDocument(doc: OfficeDocument) {
        viewModelScope.launch {
            repository.duplicateDocument(doc.id)
        }
    }

    companion object {
        // Hoisted: compiling a Regex on every keystroke is wasteful.
        private val WHITESPACE = Regex("\\s+")

        fun countWords(text: String): Int = text.split(WHITESPACE).count { it.isNotBlank() }
    }

    /**
     * Imports a real file (md, txt, csv, docx, xlsx, pptx, pdf...) from the
     * device into PixDocx. Runs on IO; the heavy parsing never touches Main.
     */
    fun importFile(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                FileImporter.import(getApplication(), uri)
            }
            val doc = OfficeDocument(
                title = result.title,
                type = result.type,
                content = result.content,
                category = when (result.type) {
                    DocumentType.DOC -> "Docs"
                    DocumentType.XLS -> "Sheets"
                    DocumentType.PPT -> "Slides"
                    DocumentType.PDF -> "PDF"
                },
                wordCount = if (result.type == DocumentType.DOC) countWords(result.content) else 0,
                sheetRows = if (result.type == DocumentType.XLS) 20 else 0,
                sizeLabel = result.sizeLabel
            )
            val newId = repository.insert(doc)
            repository.getDocumentByIdDirect(newId)?.let { openDocument(it) }
        }
    }

    fun importCsv(csvContent: String, title: String) {
        viewModelScope.launch {
            val grid = SpreadsheetGrid.fromCsv(csvContent)
            val doc = OfficeDocument(
                title = title.ifBlank { "Imported Spreadsheet" },
                type = DocumentType.XLS,
                content = grid.serialize(),
                category = "Finance",
                sheetRows = grid.maxRows,
                sizeLabel = "${csvContent.length / 1024 + 1} KB"
            )
            val newId = repository.insert(doc)
            val created = repository.getDocumentByIdDirect(newId)
            if (created != null) {
                openDocument(created)
            }
        }
    }
}
