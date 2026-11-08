package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

data class StorageUsageStats(
    val savedDocumentsBytes: Long = 0L,
    val savedDocumentsCount: Int = 0,
    val temporaryCacheBytes: Long = 0L
)

class DocumentRepository(
    private val context: Context,
    private val documentDao: DocumentDao
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()

    fun getPdfsDirectory(): File {
        val dir = File(context.filesDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getThumbnailsDirectory(): File {
        val dir = File(context.filesDir, "thumbnails")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getWorkingScanCacheDirectory(): File {
        val dir = File(context.cacheDir, "scan_workspace")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun insertDocument(document: DocumentEntity): Long = withContext(Dispatchers.IO) {
        documentDao.insertDocument(document)
    }

    suspend fun getDocumentById(id: Long): DocumentEntity? = withContext(Dispatchers.IO) {
        documentDao.getDocumentById(id)
    }

    /**
     * Renames both the underlying local PDF file on disk and the Room database record
     * so storage and metadata remain strictly consistent.
     */
    suspend fun renameDocument(document: DocumentEntity, rawNewName: String): Result<DocumentEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitizedTitle = sanitizeFilenameBase(rawNewName)
                if (sanitizedTitle.isBlank()) {
                    throw IllegalArgumentException("Document name cannot be empty.")
                }

                val currentFile = File(document.filePath)
                val pdfsDir = getPdfsDirectory()
                val targetFile = resolveUniquePdfFile(pdfsDir, sanitizedTitle, excludeFile = currentFile)

                val finalFile = if (currentFile.exists() && currentFile.absolutePath != targetFile.absolutePath) {
                    val renamed = currentFile.renameTo(targetFile)
                    if (renamed) {
                        targetFile
                    } else {
                        currentFile.copyTo(targetFile, overwrite = true)
                        currentFile.delete()
                        targetFile
                    }
                } else {
                    currentFile
                }

                val updatedEntity = document.copy(
                    title = finalFile.nameWithoutExtension,
                    fileName = finalFile.name,
                    filePath = finalFile.absolutePath,
                    updatedAt = System.currentTimeMillis()
                )
                documentDao.updateDocument(updatedEntity)
                updatedEntity
            }
        }

    /**
     * Deletes both the PDF file, its thumbnail file, and the database record.
     */
    suspend fun deleteDocument(document: DocumentEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val pdfFile = File(document.filePath)
            if (pdfFile.exists()) {
                pdfFile.delete()
            }
            document.thumbnailPath?.let { thumbPath ->
                val thumbFile = File(thumbPath)
                if (thumbFile.exists()) {
                    thumbFile.delete()
                }
            }
            documentDao.deleteDocumentById(document.id)
        }
    }

    /**
     * Calculates storage used by saved PDFs vs temporary cache files.
     */
    suspend fun calculateStorageUsage(): StorageUsageStats = withContext(Dispatchers.IO) {
        val pdfsDir = getPdfsDirectory()
        val thumbsDir = getThumbnailsDirectory()
        val pdfFiles = pdfsDir.listFiles()?.filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }.orEmpty()

        val savedBytes = directorySize(pdfsDir) + directorySize(thumbsDir)
        val cacheBytes = directorySize(context.cacheDir)

        StorageUsageStats(
            savedDocumentsBytes = savedBytes,
            savedDocumentsCount = pdfFiles.size,
            temporaryCacheBytes = cacheBytes
        )
    }

    /**
     * Clears only temporary cache files (such as temporary camera/edit intermediates)
     * without ever deleting saved PDF documents or their thumbnails.
     */
    suspend fun clearTemporaryCache(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val beforeBytes = directorySize(context.cacheDir)
            context.cacheDir.listFiles()?.forEach { child ->
                child.deleteRecursively()
            }
            getWorkingScanCacheDirectory()
            beforeBytes
        }
    }

    private fun directorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                total += file.length()
            }
        }
        return total
    }

    companion object {
        fun sanitizeFilenameBase(rawName: String): String {
            val trimmed = rawName.trim()
                .removeSuffix(".pdf")
                .removeSuffix(".PDF")
                .trim()
            // Replace characters prohibited in Android/FAT/ext4 filenames with underscores
            val cleaned = trimmed.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .trim('.')
            return cleaned.take(80)
        }

        fun resolveUniquePdfFile(directory: File, desiredBaseName: String, excludeFile: File? = null): File {
            val base = sanitizeFilenameBase(desiredBaseName).ifBlank { "ZScan_Document" }
            var candidate = File(directory, "$base.pdf")
            var counter = 1
            while (candidate.exists() && (excludeFile == null || candidate.absolutePath != excludeFile.absolutePath)) {
                candidate = File(directory, "$base ($counter).pdf")
                counter++
            }
            return candidate
        }
    }
}
