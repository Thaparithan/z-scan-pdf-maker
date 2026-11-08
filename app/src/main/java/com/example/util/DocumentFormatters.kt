package com.example.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.DocumentEntity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentFormatters {

    fun formatDate(timestampMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.getDefault())
        return sdf.format(Date(timestampMillis))
    }

    fun formatShortDate(timestampMillis: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(timestampMillis))
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            String.format(Locale.getDefault(), "%.1f KB", kb)
        } else {
            val mb = kb / 1024.0
            String.format(Locale.getDefault(), "%.2f MB", mb)
        }
    }

    fun defaultScanDocumentName(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        return "ZScan_${sdf.format(Date())}"
    }

    fun getFileProviderUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    fun sharePdfDocument(
        context: Context,
        document: DocumentEntity?,
        externalUri: Uri?,
        title: String,
        onError: (String) -> Unit
    ) {
        try {
            val shareUri: Uri = when {
                document != null -> {
                    val file = File(document.filePath)
                    if (!file.exists()) {
                        onError("The PDF file could not be found on device storage.")
                        return
                    }
                    getFileProviderUri(context, file)
                }
                externalUri != null -> externalUri
                else -> {
                    onError("No PDF file is available to share.")
                    return
                }
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share PDF Document").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            onError("Unable to share PDF: ${e.localizedMessage ?: "No compatible app found."}")
        }
    }

    /**
     * Shares multiple PDF documents together using Intent.ACTION_SEND_MULTIPLE.
     */
    fun shareMultiplePdfDocuments(
        context: Context,
        documents: List<DocumentEntity>,
        onError: (String) -> Unit
    ) {
        try {
            val uris = ArrayList<Uri>()
            for (doc in documents) {
                val file = File(doc.filePath)
                if (file.exists()) {
                    uris.add(getFileProviderUri(context, file))
                }
            }
            if (uris.isEmpty()) {
                onError("No PDF files available to share.")
                return
            }
            val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/pdf"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share ${documents.size} PDF Document(s)").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            onError("Unable to share documents: ${e.localizedMessage ?: "No compatible app found."}")
        }
    }

    fun openPdfInExternalApp(
        context: Context,
        document: DocumentEntity?,
        externalUri: Uri?,
        onError: (String) -> Unit
    ) {
        try {
            val viewUri: Uri = when {
                document != null -> {
                    val file = File(document.filePath)
                    if (!file.exists()) {
                        onError("The PDF file could not be found on device storage.")
                        return
                    }
                    getFileProviderUri(context, file)
                }
                externalUri != null -> externalUri
                else -> {
                    onError("No PDF file is available to open.")
                    return
                }
            }

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(viewUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(viewIntent, "Open PDF with").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            onError("No other compatible PDF application is installed on this device.")
        } catch (e: Exception) {
            onError("Could not open external app: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Prints a local or external PDF document using Android's native PrintManager and PrintDocumentAdapter.
     * Supports Wi-Fi printers, Bluetooth printers, and system Print-to-PDF / Save to Drive.
     */
    fun printPdfDocument(
        context: Context,
        document: DocumentEntity?,
        externalUri: Uri?,
        title: String,
        onError: (String) -> Unit
    ) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: throw IllegalStateException("Print service is not available on this device.")

            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder("$title.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    try {
                        if (destination == null) {
                            callback?.onWriteFailed("No print destination provided.")
                            return
                        }
                        val output = FileOutputStream(destination.fileDescriptor)
                        when {
                            document != null -> {
                                val file = File(document.filePath)
                                if (!file.exists()) {
                                    callback?.onWriteFailed("Document file does not exist.")
                                    return
                                }
                                FileInputStream(file).use { input ->
                                    input.copyTo(output)
                                }
                            }
                            externalUri != null -> {
                                context.contentResolver.openInputStream(externalUri)?.use { input ->
                                    input.copyTo(output)
                                } ?: run {
                                    callback?.onWriteFailed("Cannot read PDF from external source.")
                                    return
                                }
                            }
                            else -> {
                                callback?.onWriteFailed("No document available to print.")
                                return
                            }
                        }
                        output.flush()
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.localizedMessage ?: "Failed writing print document.")
                    }
                }
            }

            printManager.print(title, printAdapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            onError("Unable to print PDF: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    /**
     * Opens the official feedback URL (https://zynor-view.web.app/) using Android Custom Tabs
     * or the default browser without embedding a WebView or transmitting any user document data.
     */
    fun openFeedbackWebsite(
        context: Context,
        url: String = "https://zynor-view.web.app/",
        onError: (String) -> Unit
    ) {
        try {
            val parsedUri = Uri.parse(url)
            val customTabIntent = Intent(Intent.ACTION_VIEW, parsedUri).apply {
                val extras = Bundle()
                extras.putBinder("android.support.customtabs.extra.SESSION", null)
                putExtras(extras)
                putExtra("android.support.customtabs.extra.TITLE_VISIBILITY", 1)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(customTabIntent)
        } catch (_: ActivityNotFoundException) {
            onError("Unable to open the feedback website. Please check that a web browser is installed and you have an internet connection.")
        } catch (e: Exception) {
            onError("Could not open feedback link: ${e.localizedMessage ?: "Please check your internet connection."}")
        }
    }
}
