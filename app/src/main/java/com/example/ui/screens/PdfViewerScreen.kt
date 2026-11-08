package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DocumentEntity
import com.example.processing.PdfViewerSession
import com.example.ui.theme.NeonRose
import com.example.util.DocumentFormatters
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    savedDocument: DocumentEntity?,
    externalUri: Uri?,
    fallbackTitle: String,
    onSharePdf: (DocumentEntity?, Uri?, String) -> Unit,
    onOpenInExternalApp: (DocumentEntity?, Uri?) -> Unit,
    onPrintPdf: (DocumentEntity?, Uri?, String) -> Unit = { _, _, _ -> },
    onExportPdfToFolder: (DocumentEntity) -> Unit,
    onRenameDocument: (DocumentEntity, String) -> Unit,
    onDeleteDocument: (DocumentEntity) -> Unit,
    onBackClick: () -> Unit
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var viewerSession by remember { mutableStateOf<PdfViewerSession?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    val currentPageNumber by remember {
        derivedStateOf {
            (lazyListState.firstVisibleItemIndex + 1).coerceAtLeast(1)
        }
    }

    // Open PDF session safely and release native PdfRenderer when screen closes
    LaunchedEffect(savedDocument?.filePath, externalUri) {
        isLoading = true
        errorMessage = null
        viewerSession?.close()
        viewerSession = null

        val result = when {
            savedDocument != null -> {
                PdfViewerSession.openFromFile(
                    file = File(savedDocument.filePath),
                    customTitle = savedDocument.title
                )
            }
            externalUri != null -> {
                PdfViewerSession.openFromUri(context, externalUri)
            }
            else -> {
                Result.failure(IllegalArgumentException("No PDF document was specified."))
            }
        }

        result.onSuccess { session ->
            viewerSession = session
            isLoading = false
        }.onFailure { err ->
            errorMessage = err.localizedMessage ?: "Unable to open this PDF document."
            isLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewerSession?.close()
        }
    }

    val displayTitle = savedDocument?.title
        ?: viewerSession?.metadata?.title
        ?: fallbackTitle

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        viewerSession?.let { session ->
                            Text(
                                text = "${session.pageCount} ${if (session.pageCount == 1) "Page" else "Pages"} • ${
                                    DocumentFormatters.formatFileSize(session.metadata.fileSizeBytes)
                                }",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("pdf_viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onSharePdf(savedDocument, externalUri, displayTitle) },
                        enabled = viewerSession != null,
                        modifier = Modifier.testTag("viewer_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share PDF"
                        )
                    }

                    IconButton(
                        onClick = { onOpenInExternalApp(savedDocument, externalUri) },
                        enabled = viewerSession != null,
                        modifier = Modifier.testTag("viewer_open_external_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open with another app"
                        )
                    }

                    if (savedDocument != null || externalUri != null) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("viewer_more_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More document options"
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Print Document") },
                                    leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onPrintPdf(savedDocument, externalUri, displayTitle)
                                    }
                                )
                                if (savedDocument != null) {
                                    DropdownMenuItem(
                                        text = { Text("Save to Device Folder") },
                                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            onExportPdfToFolder(savedDocument)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rename PDF") },
                                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                                        onClick = {
                                            menuExpanded = false
                                            showRenameDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete PDF", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            menuExpanded = false
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(innerPadding)
                .testTag("pdf_viewer_screen")
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Opening PDF document...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage != null || viewerSession == null -> {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .widthIn(max = 420.dp)
                            .testTag("pdf_viewer_error_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Unable to Open PDF",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = errorMessage ?: "This PDF file may be missing, corrupted, or inaccessible.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = onBackClick,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Go Back")
                            }
                        }
                    }
                }

                else -> {
                    val session = viewerSession!!
                    val totalPages = session.pageCount

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, _, zoomChange, _ ->
                                    if (zoomChange != 1f) {
                                        zoomScale = (zoomScale * zoomChange).coerceIn(1.0f, 3.0f)
                                    }
                                }
                            }
                    ) {
                        val baseWidthDp = maxWidth.coerceAtMost(760.dp) - 24.dp
                        val zoomedWidthDp = baseWidthDp * zoomScale
                        val horizontalScrollState = rememberScrollState()

                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(
                                    state = horizontalScrollState,
                                    enabled = zoomScale > 1.02f
                                )
                                .testTag("pdf_pages_lazy_column"),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 14.dp,
                                bottom = 92.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            items(
                                count = totalPages,
                                key = { pageIdx -> pageIdx }
                            ) { pageIndex ->
                                PdfRenderedPageCard(
                                    session = session,
                                    pageIndex = pageIndex,
                                    totalPages = totalPages,
                                    zoomScale = zoomScale,
                                    modifier = Modifier.width(zoomedWidthDp)
                                )
                            }
                        }

                        // Floating Bottom Control Bar: Page Navigation + Zoom Controls
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            tonalElevation = 6.dp,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(50)
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Previous Page
                                IconButton(
                                    onClick = {
                                        if (currentPageNumber > 1) {
                                            coroutineScope.launch {
                                                lazyListState.animateScrollToItem(currentPageNumber - 2)
                                            }
                                        }
                                    },
                                    enabled = currentPageNumber > 1,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("pdf_prev_page_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Previous page"
                                    )
                                }

                                // Page X of Y Indicator
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.testTag("pdf_page_indicator")
                                ) {
                                    Text(
                                        text = "Page $currentPageNumber of $totalPages",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }

                                // Next Page
                                IconButton(
                                    onClick = {
                                        if (currentPageNumber < totalPages) {
                                            coroutineScope.launch {
                                                lazyListState.animateScrollToItem(currentPageNumber)
                                            }
                                        }
                                    },
                                    enabled = currentPageNumber < totalPages,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("pdf_next_page_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Next page"
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(22.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )

                                // Zoom Out
                                IconButton(
                                    onClick = {
                                        zoomScale = (zoomScale - 0.25f).coerceAtLeast(1.0f)
                                    },
                                    enabled = zoomScale > 1.0f,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("pdf_zoom_out_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomOut,
                                        contentDescription = "Zoom out"
                                    )
                                }

                                Text(
                                    text = "${(zoomScale * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (zoomScale > 1.01f) NeonRose else MaterialTheme.colorScheme.onSurface
                                )

                                // Zoom In
                                IconButton(
                                    onClick = {
                                        zoomScale = (zoomScale + 0.5f).coerceAtMost(3.0f)
                                    },
                                    enabled = zoomScale < 3.0f,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("pdf_zoom_in_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "Zoom in"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog && savedDocument != null) {
        RenameDocumentDialog(
            initialName = savedDocument.title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                onRenameDocument(savedDocument, newName)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog && savedDocument != null) {
        DeleteDocumentConfirmDialog(
            documentTitle = savedDocument.fileName,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDeleteDocument(savedDocument)
            }
        )
    }
}

@Composable
private fun PdfRenderedPageCard(
    session: PdfViewerSession,
    pageIndex: Int,
    totalPages: Int,
    zoomScale: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var pageBitmap by remember(session, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var pageAspectRatio by remember(session, pageIndex) { mutableFloatStateOf(0.707f) }
    var renderError by remember(session, pageIndex) { mutableStateOf(false) }

    val renderTargetWidthPx = remember(zoomScale, density) {
        with(density) {
            (380.dp.toPx() * zoomScale.coerceIn(1f, 2.2f)).roundToInt().coerceIn(720, 2200)
        }
    }

    LaunchedEffect(session, pageIndex, renderTargetWidthPx) {
        val result = session.renderPage(
            pageIndex = pageIndex,
            targetWidthPx = renderTargetWidthPx
        )
        result.onSuccess { bmp ->
            pageAspectRatio = (bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f))
                .coerceIn(0.35f, 2.5f)
            pageBitmap = bmp
            renderError = false
        }.onFailure {
            renderError = true
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .testTag("rendered_pdf_page_${pageIndex + 1}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(pageAspectRatio)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            when {
                pageBitmap != null -> {
                    Image(
                        bitmap = pageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${pageIndex + 1} of $totalPages",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                renderError -> {
                    Text(
                        text = "Unable to render Page ${pageIndex + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.DarkGray
                    )
                }
                else -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }

            // Corner Page Number Badge
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
            ) {
                Text(
                    text = "${pageIndex + 1} / $totalPages",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
