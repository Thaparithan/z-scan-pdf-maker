package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.ZScanBrandLogo
import com.example.ui.screens.CameraScannerScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PageEditorScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StudioReviewScreen
import com.example.ui.theme.JetBlack
import com.example.ui.theme.NeonRose
import com.example.ui.theme.ZScanTheme
import com.example.util.DocumentFormatters
import com.example.viewmodel.AppScreen
import com.example.viewmodel.BottomNavTab
import com.example.viewmodel.ZScanViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val zScanViewModel: ZScanViewModel = viewModel(
                factory = ZScanViewModel.Factory(application)
            )
            ZScanApp(viewModel = zScanViewModel)
        }
    }
}

@Composable
fun ZScanApp(
    viewModel: ZScanViewModel,
    showInitialSplash: Boolean = true
) {
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val allDocuments by viewModel.allDocuments.collectAsStateWithLifecycle()
    val filteredDocuments by viewModel.filteredDocuments.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val storageStats by viewModel.storageStats.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var splashVisible by rememberSaveable { mutableStateOf(showInitialSplash) }

    LaunchedEffect(showInitialSplash) {
        if (splashVisible) {
            delay(850)
            splashVisible = false
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    // 1. Multi-Image Photo Picker Launcher
    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImagesFromUris(uris, navigateToStudio = true)
        }
    }

    // 2. External PDF Picker Launcher (Storage Access Framework)
    val openExternalPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openExternalPdfUri(uri)
        }
    }

    // 3. Export PDF to User-Selected Location Launcher (Storage Access Framework)
    val createPdfDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        viewModel.completeSafExport(destinationUri)
    }

    fun launchImagePicker() {
        runCatching {
            multiplePhotoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure {
            viewModel.showMessage("Could not open system image picker on this device.")
        }
    }

    fun launchExternalPdfPicker() {
        runCatching {
            openExternalPdfLauncher.launch(arrayOf("application/pdf"))
        }.onFailure {
            viewModel.showMessage("Could not open file picker on this device.")
        }
    }

    ZScanTheme(themeMode = userSettings.themeMode) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val screen = currentScreen) {
                is AppScreen.Main -> {
                    if (screen.tab != BottomNavTab.HOME) {
                        BackHandler {
                            viewModel.selectMainTab(BottomNavTab.HOME)
                        }
                    }

                    AdaptiveMainScaffold(
                        activeTab = screen.tab,
                        onTabSelect = { viewModel.selectMainTab(it) },
                        onQuickScanClick = { viewModel.openCameraScanner() },
                        snackbarHostState = snackbarHostState
                    ) { contentModifier ->
                        when (screen.tab) {
                            BottomNavTab.HOME -> {
                                HomeScreen(
                                    recentDocuments = allDocuments,
                                    draftPageCount = studioState.pages.size,
                                    onScanDocumentClick = { viewModel.openCameraScanner() },
                                    onImportImagesClick = { launchImagePicker() },
                                    onCreatePdfClick = { viewModel.openStudioReview() },
                                    onMyDocumentsClick = { viewModel.selectMainTab(BottomNavTab.DOCUMENTS) },
                                    onOpenExternalPdfClick = { launchExternalPdfPicker() },
                                    onSettingsClick = { viewModel.selectMainTab(BottomNavTab.SETTINGS) },
                                    onOpenDocumentClick = { doc -> viewModel.openSavedPdf(doc) },
                                    onShareDocumentClick = { doc ->
                                        DocumentFormatters.sharePdfDocument(
                                            context = context,
                                            document = doc,
                                            externalUri = null,
                                            title = doc.title,
                                            onError = { viewModel.showMessage(it) }
                                        )
                                    },
                                    modifier = contentModifier
                                )
                            }

                            BottomNavTab.DOCUMENTS -> {
                                DocumentsScreen(
                                    documents = filteredDocuments,
                                    searchQuery = searchQuery,
                                    sortOption = userSettings.sortOption,
                                    layoutMode = userSettings.layoutMode,
                                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                                    onSortOptionChange = { viewModel.setSortOption(it) },
                                    onLayoutModeChange = { viewModel.setLayoutMode(it) },
                                    onOpenDocument = { doc -> viewModel.openSavedPdf(doc) },
                                    onRenameDocument = { doc, newName ->
                                        viewModel.renameSavedDocument(doc, newName)
                                    },
                                    onShareDocument = { doc ->
                                        DocumentFormatters.sharePdfDocument(
                                            context = context,
                                            document = doc,
                                            externalUri = null,
                                            title = doc.title,
                                            onError = { viewModel.showMessage(it) }
                                        )
                                    },
                                    onExportDocument = { doc ->
                                        viewModel.prepareSafExportForDocument(doc) { suggestedName ->
                                            createPdfDocumentLauncher.launch(suggestedName)
                                        }
                                    },
                                    onDeleteDocument = { doc ->
                                        viewModel.deleteSavedDocument(doc)
                                    },
                                    onDeleteMultipleDocuments = { docs ->
                                        viewModel.deleteMultipleDocuments(docs)
                                    },
                                    onShareMultipleDocuments = { docs ->
                                        DocumentFormatters.shareMultiplePdfDocuments(
                                            context = context,
                                            documents = docs,
                                            onError = { viewModel.showMessage(it) }
                                        )
                                    },
                                    onScanNewClick = { viewModel.openCameraScanner() },
                                    onOpenExternalPdfClick = { launchExternalPdfPicker() },
                                    modifier = contentModifier
                                )
                            }

                            BottomNavTab.SETTINGS -> {
                                SettingsScreen(
                                    currentThemeMode = userSettings.themeMode,
                                    storageStats = storageStats,
                                    autoCropPaper = userSettings.autoCropPaper,
                                    onThemeModeSelected = { viewModel.setThemeMode(it) },
                                    onAutoCropToggled = { viewModel.setAutoCropPaper(it) },
                                    onClearCacheClick = { viewModel.clearTemporaryCache() },
                                    onFeedbackClick = {
                                        DocumentFormatters.openFeedbackWebsite(
                                            context = context,
                                            url = "https://zynor-view.web.app/",
                                            onError = { viewModel.showMessage(it) }
                                        )
                                    },
                                    modifier = contentModifier
                                )
                            }
                        }
                    }
                }

                is AppScreen.CameraScanner -> {
                    CameraScannerScreen(
                        capturedPages = studioState.pages,
                        isProcessing = studioState.isProcessingImages,
                        workingCacheDir = viewModel.documentRepository.getWorkingScanCacheDirectory(),
                        autoCropPaperDefault = userSettings.autoCropPaper,
                        onAutoCropToggled = { viewModel.setAutoCropPaper(it) },
                        onPhotoCaptured = { file, autoCrop, onPageReady ->
                            viewModel.onCameraPhotoCaptured(file, autoCropOverride = autoCrop, onPageReady = onPageReady)
                        },
                        onRetakePage = { pageId -> viewModel.deleteDraftPage(pageId) },
                        onFinishToStudio = { viewModel.openStudioReview() },
                        onImportFromGalleryClick = { launchImagePicker() },
                        onBackClick = { viewModel.navigateBack() }
                    )
                }

                is AppScreen.StudioReview -> {
                    StudioReviewScreen(
                        studioState = studioState,
                        onDocumentNameChange = { viewModel.updateStudioDocumentName(it) },
                        onPageSizeChange = { viewModel.updateStudioPageSize(it) },
                        onOrientationChange = { viewModel.updateStudioOrientation(it) },
                        onQualityChange = { viewModel.updateStudioQuality(it) },
                        onMarginsChange = { viewModel.updateStudioMargins(it) },
                        onMovePage = { from, to -> viewModel.movePage(from, to) },
                        onRotatePage = { pageId -> viewModel.rotateDraftPageQuick(pageId) },
                        onDeletePage = { pageId -> viewModel.deleteDraftPage(pageId) },
                        onEditPageClick = { pageId -> viewModel.openPageEditor(pageId) },
                        onAddCameraPagesClick = { viewModel.openCameraScanner(resetSessionIfEmpty = false) },
                        onImportImagesClick = { launchImagePicker() },
                        onClearAllPages = { viewModel.clearDraftSession() },
                        onGeneratePdfClick = {
                            viewModel.generatePdfDocument(
                                openViewerWhenDone = true,
                                requestExternalExport = false
                            )
                        },
                        onGenerateAndExportSafClick = {
                            viewModel.generatePdfDocument(
                                openViewerWhenDone = true,
                                requestExternalExport = true,
                                onExportReady = { _, fileName ->
                                    createPdfDocumentLauncher.launch(fileName)
                                }
                            )
                        },
                        onBackClick = { viewModel.navigateBack() }
                    )
                }

                is AppScreen.PageEditor -> {
                    val targetPage = studioState.pages.find { it.id == screen.pageId }
                    if (targetPage == null) {
                        LaunchedEffect(Unit) {
                            viewModel.openStudioReview()
                        }
                    } else {
                        PageEditorScreen(
                            page = targetPage,
                            isSaving = studioState.isProcessingImages,
                            onSaveEdits = { updated ->
                                viewModel.applyPageEdits(updated) {
                                    viewModel.openStudioReview()
                                }
                            },
                            onResetToOriginal = {
                                viewModel.resetPageEdits(targetPage.id)
                            },
                            onBackClick = { viewModel.navigateBack() }
                        )
                    }
                }

                is AppScreen.PdfViewer -> {
                    val savedDoc = screen.documentId?.let { id ->
                        allDocuments.find { it.id == id }
                    }
                    PdfViewerScreen(
                        savedDocument = savedDoc,
                        externalUri = screen.externalUri,
                        fallbackTitle = screen.initialTitle,
                        onSharePdf = { doc, extUri, title ->
                            DocumentFormatters.sharePdfDocument(
                                context = context,
                                document = doc,
                                externalUri = extUri,
                                title = title,
                                onError = { viewModel.showMessage(it) }
                            )
                        },
                        onOpenInExternalApp = { doc, extUri ->
                            DocumentFormatters.openPdfInExternalApp(
                                context = context,
                                document = doc,
                                externalUri = extUri,
                                onError = { viewModel.showMessage(it) }
                            )
                        },
                        onPrintPdf = { doc, extUri, title ->
                            DocumentFormatters.printPdfDocument(
                                context = context,
                                document = doc,
                                externalUri = extUri,
                                title = title,
                                onError = { viewModel.showMessage(it) }
                            )
                        },
                        onExportPdfToFolder = { doc ->
                            viewModel.prepareSafExportForDocument(doc) { suggestedName ->
                                createPdfDocumentLauncher.launch(suggestedName)
                            }
                        },
                        onRenameDocument = { doc, newName ->
                            viewModel.renameSavedDocument(doc, newName)
                        },
                        onDeleteDocument = { doc ->
                            viewModel.deleteSavedDocument(doc) {
                                viewModel.selectMainTab(BottomNavTab.DOCUMENTS)
                            }
                        },
                        onBackClick = { viewModel.navigateBack() }
                    )
                }
            }

            // Branded Splash Screen Overlay on First Launch
            AnimatedVisibility(
                visible = splashVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ZScanSplashOverlay()
            }
        }
    }
}

@Composable
private fun AdaptiveMainScaffold(
    activeTab: BottomNavTab,
    onTabSelect: (BottomNavTab) -> Unit,
    onQuickScanClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    content: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (!isExpandedScreen && activeTab == BottomNavTab.DOCUMENTS) {
                    ExtendedFloatingActionButton(
                        onClick = onQuickScanClick,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                        text = { Text("Scan") },
                        modifier = Modifier.testTag("fab_scan_document")
                    )
                }
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        modifier = Modifier.testTag("main_bottom_navigation")
                    ) {
                        NavigationBarItem(
                            selected = activeTab == BottomNavTab.HOME,
                            onClick = { onTabSelect(BottomNavTab.HOME) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == BottomNavTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = stringResource(R.string.nav_home)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_home)) },
                            modifier = Modifier.testTag("nav_tab_home")
                        )
                        NavigationBarItem(
                            selected = activeTab == BottomNavTab.DOCUMENTS,
                            onClick = { onTabSelect(BottomNavTab.DOCUMENTS) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == BottomNavTab.DOCUMENTS) Icons.Filled.Folder else Icons.Outlined.Folder,
                                    contentDescription = stringResource(R.string.nav_documents)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_documents)) },
                            modifier = Modifier.testTag("nav_tab_documents")
                        )
                        NavigationBarItem(
                            selected = activeTab == BottomNavTab.SETTINGS,
                            onClick = { onTabSelect(BottomNavTab.SETTINGS) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == BottomNavTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.nav_settings)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_settings)) },
                            modifier = Modifier.testTag("nav_tab_settings")
                        )
                    }
                }
            }
        ) { innerPadding ->
            if (isExpandedScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        header = {
                            FloatingActionButton(
                                onClick = onQuickScanClick,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .testTag("rail_scan_fab")
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Scan Document")
                            }
                        },
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("main_navigation_rail")
                    ) {
                        NavigationRailItem(
                            selected = activeTab == BottomNavTab.HOME,
                            onClick = { onTabSelect(BottomNavTab.HOME) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == BottomNavTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = stringResource(R.string.nav_home)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_home)) },
                            modifier = Modifier.testTag("nav_tab_home")
                        )
                        NavigationRailItem(
                            selected = activeTab == BottomNavTab.DOCUMENTS,
                            onClick = { onTabSelect(BottomNavTab.DOCUMENTS) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == BottomNavTab.DOCUMENTS) Icons.Filled.Folder else Icons.Outlined.Folder,
                                    contentDescription = stringResource(R.string.nav_documents)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_documents)) },
                            modifier = Modifier.testTag("nav_tab_documents")
                        )
                        NavigationRailItem(
                            selected = activeTab == BottomNavTab.SETTINGS,
                            onClick = { onTabSelect(BottomNavTab.SETTINGS) },
                            icon = {
                                Icon(
                                    imageVector = if (activeTab == BottomNavTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = stringResource(R.string.nav_settings)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_settings)) },
                            modifier = Modifier.testTag("nav_tab_settings")
                        )
                    }
                    content(Modifier.weight(1f))
                }
            } else {
                content(Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
fun ZScanSplashOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(JetBlack)
            .testTag("splash_screen_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ZScanBrandLogo(
                size = 104.dp,
                animatedScanLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = NeonRose.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "VERSION ${stringResource(R.string.app_version)} • OFFLINE PDF STUDIO",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonRose,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
