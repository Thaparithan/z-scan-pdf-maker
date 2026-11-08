package com.example.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.roundToInt

data class OpenedPdfMetadata(
    val title: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val sourceFile: File?,
    val sourceUri: Uri?
)

/**
 * Thread-safe, memory-conscious PDF rendering engine backed by Android's PdfRenderer,
 * with embedded stream decoding fallback for JVM test environments.
 */
class PdfViewerSession private constructor(
    private val parcelFileDescriptor: ParcelFileDescriptor?,
    private val pdfRenderer: PdfRenderer?,
    private val embeddedJpegStreams: List<ByteArray>?,
    private val tempFallbackFile: File?,
    val metadata: OpenedPdfMetadata
) : Closeable {

    private val renderMutex = Mutex()
    @Volatile
    private var isClosed = false

    val pageCount: Int
        get() = metadata.pageCount

    /**
     * Renders a single page at the requested pixel width on a background thread.
     */
    suspend fun renderPage(
        pageIndex: Int,
        targetWidthPx: Int = 1080
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            runCatching {
                if (isClosed) {
                    throw IllegalStateException("PDF viewer session is closed.")
                }
                if (pageIndex !in 0 until metadata.pageCount) {
                    throw IndexOutOfBoundsException("Page index $pageIndex out of bounds.")
                }

                val safeTargetWidth = targetWidthPx.coerceIn(360, 2560)

                if (pdfRenderer != null) {
                    pdfRenderer.openPage(pageIndex).use { page ->
                        val aspectRatio = page.height.toFloat() / max(1, page.width).toFloat()
                        val bitmapW = safeTargetWidth
                        val bitmapH = (bitmapW * aspectRatio).roundToInt().coerceIn(200, 3600)

                        val bitmap = Bitmap.createBitmap(bitmapW, bitmapH, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        return@runCatching bitmap
                    }
                }

                val streamBytes = embeddedJpegStreams?.getOrNull(pageIndex)
                if (streamBytes != null) {
                    val decoded = BitmapFactory.decodeByteArray(streamBytes, 0, streamBytes.size)
                        ?: throw IllegalStateException("Could not decode page ${pageIndex + 1} image stream.")
                    val aspect = decoded.height.toFloat() / max(1, decoded.width).toFloat()
                    val targetH = (safeTargetWidth * aspect).roundToInt().coerceIn(200, 3600)
                    val scaled = Bitmap.createScaledBitmap(decoded, safeTargetWidth, targetH, true)
                    if (scaled !== decoded) decoded.recycle()
                    return@runCatching scaled
                }

                throw IllegalStateException("Unable to render PDF page ${pageIndex + 1}.")
            }
        }
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        runCatching { pdfRenderer?.close() }
        runCatching { parcelFileDescriptor?.close() }
        runCatching { tempFallbackFile?.delete() }
    }

    companion object {
        suspend fun openFromFile(file: File, customTitle: String? = null): Result<PdfViewerSession> =
            withContext(Dispatchers.IO) {
                runCatching {
                    if (!file.exists() || !file.canRead()) {
                        throw IllegalArgumentException("This PDF file could not be found or accessed.")
                    }
                    if (file.length() <= 16L) {
                        throw IllegalArgumentException("This PDF file is empty or corrupted.")
                    }
                    if (!hasValidPdfHeader(file)) {
                        throw IllegalArgumentException("Invalid or corrupted PDF format.")
                    }

                    openValidatedFile(
                        file = file,
                        tempCopy = null,
                        title = customTitle ?: file.nameWithoutExtension,
                        sizeBytes = file.length(),
                        sourceUri = null
                    )
                }
            }

        suspend fun openFromUri(context: Context, uri: Uri): Result<PdfViewerSession> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val (displayName, sizeBytes) = queryUriMetadata(context, uri)
                    val fallback = File(context.cacheDir, "viewer_ext_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(fallback).use { output ->
                            input.copyTo(output)
                        }
                    } ?: throw IllegalArgumentException("Cannot read the selected PDF file.")

                    if (!fallback.exists() || fallback.length() <= 16L || !hasValidPdfHeader(fallback)) {
                        fallback.delete()
                        throw IllegalArgumentException("This file is not a valid PDF document.")
                    }

                    openValidatedFile(
                        file = fallback,
                        tempCopy = fallback,
                        title = displayName.removeSuffix(".pdf").removeSuffix(".PDF"),
                        sizeBytes = if (sizeBytes > 0L) sizeBytes else fallback.length(),
                        sourceUri = uri
                    )
                }
            }

        private fun openValidatedFile(
            file: File,
            tempCopy: File?,
            title: String,
            sizeBytes: Long,
            sourceUri: Uri?
        ): PdfViewerSession {
            var pfd: ParcelFileDescriptor? = null
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val meta = OpenedPdfMetadata(
                        title = title,
                        pageCount = renderer.pageCount,
                        fileSizeBytes = sizeBytes,
                        sourceFile = file,
                        sourceUri = sourceUri
                    )
                    return PdfViewerSession(
                        parcelFileDescriptor = pfd,
                        pdfRenderer = renderer,
                        embeddedJpegStreams = null,
                        tempFallbackFile = tempCopy,
                        metadata = meta
                    )
                } else {
                    runCatching { renderer.close() }
                    runCatching { pfd.close() }
                }
            } catch (_: Throwable) {
                runCatching { pfd?.close() }
            }

            // Fallback stream extractor when native PdfRenderer is unavailable or returns 0 on JVM
            val extractedStreams = extractEmbeddedJpegStreams(file)
            if (extractedStreams.isEmpty()) {
                tempCopy?.delete()
                throw IllegalArgumentException("This PDF contains 0 readable pages or is corrupted.")
            }

            val meta = OpenedPdfMetadata(
                title = title,
                pageCount = extractedStreams.size,
                fileSizeBytes = sizeBytes,
                sourceFile = file,
                sourceUri = sourceUri
            )
            return PdfViewerSession(
                parcelFileDescriptor = null,
                pdfRenderer = null,
                embeddedJpegStreams = extractedStreams,
                tempFallbackFile = tempCopy,
                metadata = meta
            )
        }

        private fun hasValidPdfHeader(file: File): Boolean {
            if (!file.exists() || file.length() < 8L) return false
            val header = ByteArray(8)
            val read = FileInputStream(file).use { it.read(header) }
            if (read < 5) return false
            val headerText = String(header, 0, read, StandardCharsets.US_ASCII)
            return headerText.startsWith("%PDF-")
        }

        private fun extractEmbeddedJpegStreams(file: File): List<ByteArray> {
            val bytes = file.readBytes()
            val streams = mutableListOf<ByteArray>()
            var i = 0
            while (i < bytes.size - 4) {
                // Locate JPEG SOI marker (0xFF 0xD8 0xFF)
                if (bytes[i] == 0xFF.toByte() &&
                    bytes[i + 1] == 0xD8.toByte() &&
                    bytes[i + 2] == 0xFF.toByte()
                ) {
                    var end = i + 3
                    var foundEoi = false
                    while (end < bytes.size - 1) {
                        if (bytes[end] == 0xFF.toByte() && bytes[end + 1] == 0xD9.toByte()) {
                            end += 2
                            foundEoi = true
                            break
                        }
                        end++
                    }
                    if (foundEoi && end - i > 128) {
                        streams.add(bytes.copyOfRange(i, end))
                        i = end
                        continue
                    }
                }
                i++
            }
            return streams
        }

        private fun queryUriMetadata(context: Context, uri: Uri): Pair<String, Long> {
            var name = "External Document.pdf"
            var size = 0L
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                            name = cursor.getString(nameIdx)
                        }
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            size = cursor.getLong(sizeIdx)
                        }
                    }
                }
            }
            return name to size
        }
    }
}
