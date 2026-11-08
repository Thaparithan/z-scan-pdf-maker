package com.example.processing

import java.util.UUID

data class NormalizedRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    fun coerced(): NormalizedRect {
        val l = left.coerceIn(0f, 0.9f)
        val t = top.coerceIn(0f, 0.9f)
        val r = right.coerceIn(l + 0.1f, 1f)
        val b = bottom.coerceIn(t + 0.1f, 1f)
        return NormalizedRect(l, t, r, b)
    }

    val isFullFrame: Boolean
        get() = left <= 0.01f && top <= 0.01f && right >= 0.99f && bottom >= 0.99f
}

enum class DocumentFilterMode(val label: String, val subtitle: String) {
    ORIGINAL("Original", "Natural camera colours"),
    AUTO_ENHANCE("Enhance", "Sharpen & brighten document"),
    GRAYSCALE("Grayscale", "Balanced grey tones"),
    BLACK_AND_WHITE("B & W", "High-contrast crisp ink")
}

data class ScanDraftPage(
    val id: String = UUID.randomUUID().toString(),
    val originalFilePath: String,
    val processedFilePath: String,
    val filterMode: DocumentFilterMode = DocumentFilterMode.ORIGINAL,
    val brightness: Float = 0f,     // Range: -80f .. +80f
    val contrast: Float = 1.0f,     // Range: 0.6f .. 1.8f
    val rotationDegrees: Int = 0,   // 0, 90, 180, 270
    val cropRect: NormalizedRect = NormalizedRect(),
    val revision: Int = 0           // Incremented on edit to bust image cache
) {
    val hasEdits: Boolean
        get() = filterMode != DocumentFilterMode.ORIGINAL ||
            brightness != 0f ||
            contrast != 1.0f ||
            rotationDegrees != 0 ||
            !cropRect.isFullFrame
}

enum class PdfPageSizeOption(
    val label: String,
    val description: String,
    val baseWidthPt: Int,
    val baseHeightPt: Int
) {
    A4("A4", "Standard International (210 × 297 mm)", 595, 842),
    LETTER("Letter", "US Letter (8.5 × 11 in)", 612, 792),
    FIT_IMAGE("Fit Image", "Match each page's image proportions", 0, 0)
}

enum class PdfOrientationOption(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class PdfQualityOption(
    val label: String,
    val description: String,
    val jpegQuality: Int,
    val pageScaleMultiplier: Float
) {
    HIGH("High Quality", "Best clarity for printing & archiving", 94, 2.2f),
    BALANCED("Balanced", "Sharp text with medium file size", 82, 1.75f),
    COMPACT("Compact", "Smaller file size for fast sharing", 68, 1.3f)
}

data class PdfCreationConfig(
    val fileName: String,
    val pageSize: PdfPageSizeOption = PdfPageSizeOption.A4,
    val orientation: PdfOrientationOption = PdfOrientationOption.AUTO,
    val quality: PdfQualityOption = PdfQualityOption.HIGH,
    val includeMargins: Boolean = true
)
