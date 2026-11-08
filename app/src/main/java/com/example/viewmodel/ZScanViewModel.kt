package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.DocumentEntity
import com.example.data.DocumentLayoutMode
import com.example.data.DocumentRepository
import com.example.data.DocumentSortOption
import com.example.data.StorageUsageStats
import com.example.data.ThemePreferencesRepository
import com.example.data.UserSettingsState
import com.example.data.ZScanDatabase
import com.example.data.zScanDataStore
import com.example.processing.DocumentFilterMode
import com.example.processing.ImageProcessor
import com.example.processing.NormalizedRect
import com.example.processing.PdfCreationConfig
import com.example.processing.PdfGenerator
import com.example.processing.PdfOrientationOption
import com.example.processing.PdfPageSizeOption
import com.example.processing.PdfQualityOption
import com.example.processing.ScanDraftPage
import com.example.ui.theme.AppThemeMode
import com.example.util.DocumentFormatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class BottomNavTab {
    HOME,
    DOCUMENTS,
    SETTINGS
}

sealed interface AppScreen {
    data class Main(val tab: BottomNavTab = BottomNavTab.HOME) : AppScreen
    data object CameraScanner : AppScreen
    data object StudioReview : AppScreen
    data class PageEditor(val pageId: String) : AppScreen
    data class PdfViewer(
        val documentId: Long? = null,
        val externalUri: Uri? = null,
        val initialTitle: String = "PDF Document"
    ) : AppScreen
}

data class PdfGenerationProgress(
    val isGenerating: Boolean = false,
    val currentPage: Int = 0,
    val totalPages: Int = 0
)

data class StudioUiState(
    val pages: List<ScanDraftPage> = emptyList(),
    val documentName: String = DocumentFormatters.defaultScanDocumentName(),
    val pageSize: PdfPageSizeOption = PdfPageSizeOption.A4,
    val orientation: PdfOrientationOption = PdfOrientationOption.AUTO,
    val quality: PdfQualityOption = PdfQualityOption.HIGH,
    val includeMargins: Boolean = true,
    val isProcessingImages: Boolean = false,
    val pdfProgress: PdfGenerationProgress = PdfGenerationProgress(),
    val lastCreatedDocument: DocumentEntity? = null
)

class ZScanViewModel(
    application: Application,
    val documentRepository: DocumentRepository,
    private val preferencesRepository: ThemePreferencesRepository
) : AndroidViewModel(application) {

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Main(BottomNavTab.HOME))
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _studioState = MutableStateFlow(StudioUiState())
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageUsageStats())
    val storageStats: StateFlow<StorageUsageStats> = _storageStats.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Pending PDF file waiting for SAF export destination URI selection
    private val _pendingSafExportFile = MutableStateFlow<File?>(null)
    val pendingSafExportFile: StateFlow<File?> = _pendingSafExportFile.asStateFlow()

    val userSettings: StateFlow<UserSettingsState> = preferencesRepository.userSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettingsState()
        )

    val allDocuments: StateFlow<List<DocumentEntity>> = documentRepository.allDocuments
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredDocuments: StateFlow<List<DocumentEntity>> = combine(
        allDocuments,
        _searchQuery,
        userSettings
    ) { docs, query, settings ->
        val trimmed = query.trim()
        val matched = if (trimmed.isEmpty()) {
            docs
        } else {
            docs.filter { doc ->
                doc.title.contains(trimmed, ignoreCase = true) ||
                    doc.fileName.contains(trimmed, ignoreCase = true) ||
                    doc.pageSizeLabel.contains(trimmed, ignoreCase = true)
            }
        }

        when (settings.sortOption) {
            DocumentSortOption.NEWEST_FIRST -> matched.sortedByDescending { it.createdAt }
            DocumentSortOption.OLDEST_FIRST -> matched.sortedBy { it.createdAt }
            DocumentSortOption.NAME_ASC -> matched.sortedBy { it.title.lowercase() }
            DocumentSortOption.NAME_DESC -> matched.sortedByDescending { it.title.lowercase() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        refreshStorageStats()
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun clearMessage() {
        _snackbarMessage.value = null
    }

    // Navigation Actions
    fun selectMainTab(tab: BottomNavTab) {
        _currentScreen.value = AppScreen.Main(tab)
        if (tab == BottomNavTab.SETTINGS) {
            refreshStorageStats()
        }
    }

    fun openCameraScanner(resetSessionIfEmpty: Boolean = true) {
        if (resetSessionIfEmpty && _studioState.value.pages.isEmpty()) {
            _studioState.update {
                it.copy(
                    documentName = DocumentFormatters.defaultScanDocumentName(),
                    lastCreatedDocument = null
                )
            }
        }
        _currentScreen.value = AppScreen.CameraScanner
    }

    fun openStudioReview() {
        if (_studioState.value.documentName.isBlank()) {
            _studioState.update { it.copy(documentName = DocumentFormatters.defaultScanDocumentName()) }
        }
        _currentScreen.value = AppScreen.StudioReview
    }

    fun openPageEditor(pageId: String) {
        _currentScreen.value = AppScreen.PageEditor(pageId)
    }

    fun openSavedPdf(document: DocumentEntity) {
        _currentScreen.value = AppScreen.PdfViewer(
            documentId = document.id,
            externalUri = null,
            initialTitle = document.title
        )
    }

    fun openExternalPdfUri(uri: Uri) {
        _currentScreen.value = AppScreen.PdfViewer(
            documentId = null,
            externalUri = uri,
            initialTitle = "External PDF"
        )
    }

    fun navigateBack() {
        when (val current = _currentScreen.value) {
            is AppScreen.PageEditor -> {
                _currentScreen.value = AppScreen.StudioReview
            }
            is AppScreen.CameraScanner -> {
                if (_studioState.value.pages.isNotEmpty()) {
                    _currentScreen.value = AppScreen.StudioReview
                } else {
                    _currentScreen.value = AppScreen.Main(BottomNavTab.HOME)
                }
            }
            is AppScreen.StudioReview -> {
                _currentScreen.value = AppScreen.Main(BottomNavTab.HOME)
            }
            is AppScreen.PdfViewer -> {
                _currentScreen.value = AppScreen.Main(BottomNavTab.DOCUMENTS)
            }
            is AppScreen.Main -> {
                if (current.tab != BottomNavTab.HOME) {
                    _currentScreen.value = AppScreen.Main(BottomNavTab.HOME)
                }
            }
        }
    }

    // Search, Sort, Layout & Theme Preferences
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setLayoutMode(mode: DocumentLayoutMode) {
        viewModelScope.launch {
            preferencesRepository.setLayoutMode(mode)
        }
    }

    fun setSortOption(option: DocumentSortOption) {
        viewModelScope.launch {
            preferencesRepository.setSortOption(option)
        }
    }

    // Camera & Image Import Actions
    fun onCameraPhotoCaptured(rawFile: File, onPageReady: (ScanDraftPage) -> Unit = {}) {
        viewModelScope.launch {
            _studioState.update { it.copy(isProcessingImages = true) }
            val workspace = documentRepository.getWorkingScanCacheDirectory()
            val result = ImageProcessor.createScanPageFromCameraFile(
                rawCameraFile = rawFile,
                workspaceDir = workspace,
                autoDetectEdges = true
            )
            result.onSuccess { newPage ->
                _studioState.update { state ->
                    state.copy(
                        pages = state.pages + newPage,
                        isProcessingImages = false,
                        lastCreatedDocument = null
                    )
                }
                onPageReady(newPage)
            }.onFailure { err ->
                _studioState.update { it.copy(isProcessingImages = false) }
                showMessage("Failed to process captured photo: ${err.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun importImagesFromUris(uris: List<Uri>, navigateToStudio: Boolean = true) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    isProcessingImages = true,
                    documentName = if (it.pages.isEmpty()) DocumentFormatters.defaultScanDocumentName() else it.documentName
                )
            }
            if (navigateToStudio) {
                _currentScreen.value = AppScreen.StudioReview
            }

            val workspace = documentRepository.getWorkingScanCacheDirectory()
            val importedPages = mutableListOf<ScanDraftPage>()
            var failedCount = 0

            for (uri in uris) {
                val res = ImageProcessor.importUriToScanPage(
                    context = getApplication(),
                    uri = uri,
                    workspaceDir = workspace,
                    autoDetectEdges = false
                )
                res.onSuccess { importedPages.add(it) }
                    .onFailure { failedCount++ }
            }

            _studioState.update { state ->
                state.copy(
                    pages = state.pages + importedPages,
                    isProcessingImages = false,
                    lastCreatedDocument = null
                )
            }

            if (importedPages.isNotEmpty()) {
                val msg = if (failedCount > 0) {
                    "Imported ${importedPages.size} image(s) ($failedCount skipped)."
                } else {
                    "Imported ${importedPages.size} image(s)."
                }
                showMessage(msg)
            } else if (failedCount > 0) {
                showMessage("Could not import the selected image(s).")
            }
        }
    }

    // Studio Page Management (Reorder, Delete, Rotate, Edit, Reset)
    fun movePage(fromIndex: Int, toIndex: Int) {
        _studioState.update { state ->
            if (fromIndex !in state.pages.indices || toIndex !in state.pages.indices) {
                return@update state
            }
            val mutable = state.pages.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            state.copy(pages = mutable, lastCreatedDocument = null)
        }
    }

    fun deleteDraftPage(pageId: String) {
        _studioState.update { state ->
            val target = state.pages.find { it.id == pageId }
            if (target != null) {
                runCatching { File(target.originalFilePath).delete() }
                runCatching { File(target.processedFilePath).delete() }
            }
            state.copy(
                pages = state.pages.filterNot { it.id == pageId },
                lastCreatedDocument = null
            )
        }
    }

    fun clearDraftSession() {
        _studioState.value.pages.forEach { page ->
            runCatching { File(page.originalFilePath).delete() }
            runCatching { File(page.processedFilePath).delete() }
        }
        _studioState.value = StudioUiState(documentName = DocumentFormatters.defaultScanDocumentName())
    }

    fun rotateDraftPageQuick(pageId: String) {
        val current = _studioState.value.pages.find { it.id == pageId } ?: return
        val updated = current.copy(rotationDegrees = (current.rotationDegrees + 90) % 360)
        applyPageEdits(updated)
    }

    fun applyPageEdits(updatedPage: ScanDraftPage, onFinished: () -> Unit = {}) {
        viewModelScope.launch {
            _studioState.update { it.copy(isProcessingImages = true) }
            val result = ImageProcessor.reprocessDraftPage(updatedPage)
            result.onSuccess { reprocessed ->
                _studioState.update { state ->
                    state.copy(
                        pages = state.pages.map { if (it.id == reprocessed.id) reprocessed else it },
                        isProcessingImages = false,
                        lastCreatedDocument = null
                    )
                }
                onFinished()
            }.onFailure { err ->
                _studioState.update { it.copy(isProcessingImages = false) }
                showMessage("Could not apply image adjustments: ${err.localizedMessage ?: "Error"}")
            }
        }
    }

    fun resetPageEdits(pageId: String) {
        val current = _studioState.value.pages.find { it.id == pageId } ?: return
        val resetPage = current.copy(
            filterMode = DocumentFilterMode.ORIGINAL,
            brightness = 0f,
            contrast = 1.0f,
            rotationDegrees = 0,
            cropRect = NormalizedRect()
        )
        applyPageEdits(resetPage) {
            showMessage("Page restored to original image.")
        }
    }

    // PDF Creation Config Updates
    fun updateStudioDocumentName(name: String) {
        _studioState.update { it.copy(documentName = name) }
    }

    fun updateStudioPageSize(size: PdfPageSizeOption) {
        _studioState.update { it.copy(pageSize = size) }
    }

    fun updateStudioOrientation(orientation: PdfOrientationOption) {
        _studioState.update { it.copy(orientation = orientation) }
    }

    fun updateStudioQuality(quality: PdfQualityOption) {
        _studioState.update { it.copy(quality = quality) }
    }

    fun updateStudioMargins(includeMargins: Boolean) {
        _studioState.update { it.copy(includeMargins = includeMargins) }
    }

    /**
     * Generates the multi-page PDF and saves it to the local Document Library.
     */
    fun generatePdfDocument(
        openViewerWhenDone: Boolean = true,
        requestExternalExport: Boolean = false,
        onExportReady: (File, String) -> Unit = { _, _ -> }
    ) {
        val state = _studioState.value
        if (state.pages.isEmpty()) {
            showMessage("Please scan or import at least one page first.")
            return
        }
        if (state.pdfProgress.isGenerating) return

        val config = PdfCreationConfig(
            fileName = state.documentName.ifBlank { DocumentFormatters.defaultScanDocumentName() },
            pageSize = state.pageSize,
            orientation = state.orientation,
            quality = state.quality,
            includeMargins = state.includeMargins
        )

        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    pdfProgress = PdfGenerationProgress(
                        isGenerating = true,
                        currentPage = 1,
                        totalPages = state.pages.size
                    )
                )
            }

            val result = PdfGenerator.createPdfFromPages(
                pages = state.pages,
                config = config,
                repository = documentRepository,
                onProgress = { current, total ->
                    _studioState.update { s ->
                        s.copy(
                            pdfProgress = PdfGenerationProgress(
                                isGenerating = true,
                                currentPage = current,
                                totalPages = total
                            )
                        )
                    }
                }
            )

            result.onSuccess { genResult ->
                _studioState.update {
                    it.copy(
                        pdfProgress = PdfGenerationProgress(isGenerating = false),
                        lastCreatedDocument = genResult.document
                    )
                }
                refreshStorageStats()
                showMessage("PDF '${genResult.document.fileName}' created successfully (${genResult.document.pageCount} page(s)).")

                if (requestExternalExport) {
                    _pendingSafExportFile.value = genResult.pdfFile
                    onExportReady(genResult.pdfFile, genResult.document.fileName)
                }

                if (openViewerWhenDone) {
                    openSavedPdf(genResult.document)
                }
            }.onFailure { err ->
                _studioState.update {
                    it.copy(pdfProgress = PdfGenerationProgress(isGenerating = false))
                }
                showMessage("PDF creation failed: ${err.localizedMessage ?: "Unable to write PDF."}")
            }
        }
    }

    fun prepareSafExportForDocument(document: DocumentEntity, onLaunchPicker: (String) -> Unit) {
        val file = File(document.filePath)
        if (!file.exists() || file.length() <= 0L) {
            showMessage("Source PDF file could not be found.")
            return
        }
        _pendingSafExportFile.value = file
        onLaunchPicker(document.fileName)
    }

    fun completeSafExport(destinationUri: Uri?) {
        val sourceFile = _pendingSafExportFile.value
        _pendingSafExportFile.value = null
        if (destinationUri == null || sourceFile == null) return

        viewModelScope.launch {
            val result = PdfGenerator.exportPdfToSafUri(
                context = getApplication(),
                sourcePdfFile = sourceFile,
                destinationUri = destinationUri
            )
            result.onSuccess { bytes ->
                showMessage("Exported PDF successfully (${DocumentFormatters.formatFileSize(bytes)}).")
            }.onFailure { err ->
                showMessage("Export failed: ${err.localizedMessage ?: "Could not write to selected location."}")
            }
        }
    }

    // Library Management (Rename, Delete, Cache Clear)
    fun renameSavedDocument(document: DocumentEntity, newName: String) {
        viewModelScope.launch {
            val res = documentRepository.renameDocument(document, newName)
            res.onSuccess { updated ->
                showMessage("Renamed to '${updated.fileName}'.")
            }.onFailure { err ->
                showMessage("Rename failed: ${err.localizedMessage ?: "Invalid filename"}")
            }
        }
    }

    fun deleteSavedDocument(document: DocumentEntity, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            val res = documentRepository.deleteDocument(document)
            res.onSuccess {
                refreshStorageStats()
                showMessage("Deleted '${document.fileName}'.")
                onDeleted()
            }.onFailure { err ->
                showMessage("Could not delete document: ${err.localizedMessage ?: "Error"}")
            }
        }
    }

    fun deleteMultipleDocuments(documents: List<DocumentEntity>, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            var count = 0
            for (doc in documents) {
                documentRepository.deleteDocument(doc)
                count++
            }
            refreshStorageStats()
            showMessage("Deleted $count document(s).")
            onDeleted()
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch {
            _storageStats.value = documentRepository.calculateStorageUsage()
        }
    }

    fun clearTemporaryCache() {
        viewModelScope.launch {
            val res = documentRepository.clearTemporaryCache()
            res.onSuccess { freedBytes ->
                _studioState.update { it.copy(pages = emptyList(), lastCreatedDocument = null) }
                refreshStorageStats()
                showMessage("Cleared ${DocumentFormatters.formatFileSize(freedBytes)} of temporary cache. Saved PDFs were kept safe.")
            }.onFailure { err ->
                showMessage("Failed to clear cache: ${err.localizedMessage ?: "Error"}")
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val database = ZScanDatabase.getInstance(application)
            val docRepo = DocumentRepository(application, database.documentDao())
            val prefRepo = ThemePreferencesRepository(application.zScanDataStore)
            return ZScanViewModel(application, docRepo, prefRepo) as T
        }
    }
}
