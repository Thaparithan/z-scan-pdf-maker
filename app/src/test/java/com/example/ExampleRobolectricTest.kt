package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.DocumentRepository
import com.example.data.ThemePreferencesRepository
import com.example.data.ZScanDatabase
import com.example.data.zScanDataStore
import com.example.processing.DocumentFilterMode
import com.example.processing.ImageProcessor
import com.example.processing.NormalizedRect
import com.example.processing.PdfCreationConfig
import com.example.processing.PdfGenerator
import com.example.processing.PdfPageSizeOption
import com.example.processing.PdfQualityOption
import com.example.processing.PdfViewerSession
import com.example.ui.screens.createPrintableTestDocumentFile
import com.example.ui.theme.AppThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: ZScanDatabase
    private lateinit var repository: DocumentRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(context, ZScanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DocumentRepository(context, database.documentDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun verifyAppBrandingAndVersionStrings() {
        val appName = context.getString(R.string.app_name)
        val appVersion = context.getString(R.string.app_version)
        val feedbackUrl = context.getString(R.string.feedback_url)

        assertEquals("Z Scan: PDF Maker", appName)
        assertEquals("2.0.0", appVersion)
        assertEquals("https://zynor-view.web.app/", feedbackUrl)
    }

    @Test
    fun verifyThemePreferencesPersistence() = runBlocking {
        val prefsRepo = ThemePreferencesRepository(context.zScanDataStore)

        prefsRepo.setThemeMode(AppThemeMode.DARK)
        assertEquals(AppThemeMode.DARK, prefsRepo.userSettingsFlow.first().themeMode)

        prefsRepo.setThemeMode(AppThemeMode.LIGHT)
        assertEquals(AppThemeMode.LIGHT, prefsRepo.userSettingsFlow.first().themeMode)

        prefsRepo.setThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, prefsRepo.userSettingsFlow.first().themeMode)
    }

    @Test
    fun verifyMultiPagePdfCreationViewingRenameAndCacheSafety() = runBlocking {
        val workspace = repository.getWorkingScanCacheDirectory()

        // 1. Create two printable sample document pages
        val rawPage1 = createPrintableTestDocumentFile(context, workspace, 1)
        val rawPage2 = createPrintableTestDocumentFile(context, workspace, 2)

        val draft1 = ImageProcessor.createScanPageFromCameraFile(rawPage1, workspace, autoDetectEdges = true).getOrThrow()
        val draft2 = ImageProcessor.createScanPageFromCameraFile(rawPage2, workspace, autoDetectEdges = false).getOrThrow()

        // 2. Apply non-destructive edits (Grayscale + 90° rotation + crop) to Page 2
        val editedDraft2 = ImageProcessor.reprocessDraftPage(
            draft2.copy(
                filterMode = DocumentFilterMode.GRAYSCALE,
                brightness = 10f,
                contrast = 1.2f,
                rotationDegrees = 90,
                cropRect = NormalizedRect(0.05f, 0.05f, 0.95f, 0.95f)
            )
        ).getOrThrow()

        assertTrue(File(editedDraft2.processedFilePath).exists())
        assertTrue(File(editedDraft2.originalFilePath).exists())

        // 3. Generate a 2-page A4 PDF with special characters in filename
        val config = PdfCreationConfig(
            fileName = "Contract: Review / 2026 *Final*",
            pageSize = PdfPageSizeOption.A4,
            quality = PdfQualityOption.HIGH
        )
        val pdfResult = PdfGenerator.createPdfFromPages(
            pages = listOf(draft1, editedDraft2),
            config = config,
            repository = repository
        ).getOrThrow()

        assertTrue(pdfResult.pdfFile.exists())
        assertTrue(pdfResult.pdfFile.length() > 0L)
        assertEquals(2, pdfResult.document.pageCount)

        // 4. Open generated PDF inside PdfViewerSession and render both pages
        val viewerSession = PdfViewerSession.openFromFile(pdfResult.pdfFile).getOrThrow()
        assertEquals(2, viewerSession.pageCount)

        val page0Bitmap = viewerSession.renderPage(0, targetWidthPx = 600).getOrThrow()
        assertNotNull(page0Bitmap)
        assertTrue(page0Bitmap.width > 0 && page0Bitmap.height > 0)

        val page1Bitmap = viewerSession.renderPage(1, targetWidthPx = 600).getOrThrow()
        assertNotNull(page1Bitmap)
        viewerSession.close()

        // 5. Verify clearing temporary cache does NOT delete saved PDF documents
        repository.clearTemporaryCache().getOrThrow()
        assertTrue("Saved PDF must remain untouched after clearing cache", pdfResult.pdfFile.exists())

        // 6. Rename document and verify database + filesystem synchronization
        val renamedDoc = repository.renameDocument(pdfResult.document, "Renamed_Invoice_2026").getOrThrow()
        assertEquals("Renamed_Invoice_2026.pdf", renamedDoc.fileName)
        assertTrue(File(renamedDoc.filePath).exists())

        // 7. Delete document and verify cleanup
        repository.deleteDocument(renamedDoc).getOrThrow()
        assertFalse(File(renamedDoc.filePath).exists())
        assertTrue(repository.allDocuments.first().isEmpty())
    }

    @Test
    fun verifyInvalidAndMissingPdfErrorHandling(): Unit = runBlocking {
        val missingFile = File(context.cacheDir, "non_existent_document.pdf")
        val missingResult = PdfViewerSession.openFromFile(missingFile)
        assertTrue(missingResult.isFailure)

        val corruptedFile = File(context.cacheDir, "corrupted.pdf").apply {
            writeText("NOT_A_VALID_PDF_HEADER")
        }
        val corruptedResult = PdfViewerSession.openFromFile(corruptedFile)
        assertTrue(corruptedResult.isFailure)
        corruptedFile.delete()
        Unit
    }
}
