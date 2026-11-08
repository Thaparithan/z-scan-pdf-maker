package com.example.processing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ImageProcessor {

    private const val MAX_WORKING_DIMENSION = 2048

    /**
     * Imports an external image URI into the local working scan workspace,
     * normalizing EXIF orientation so the saved original JPEG is upright and memory-safe.
     */
    suspend fun importUriToScanPage(
        context: Context,
        uri: Uri,
        workspaceDir: File,
        autoDetectEdges: Boolean = false
    ): Result<ScanDraftPage> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeSampledBitmapFromUri(context, uri, MAX_WORKING_DIMENSION)
                ?: throw IllegalArgumentException("Unable to decode the selected image.")

            val pageId = UUID.randomUUID().toString()
            val originalFile = File(workspaceDir, "orig_$pageId.jpg")
            val processedFile = File(workspaceDir, "proc_$pageId.jpg")

            saveBitmapAsJpeg(bitmap, originalFile, quality = 95)

            val initialCrop = if (autoDetectEdges) {
                detectDocumentBounds(bitmap)
            } else {
                NormalizedRect()
            }

            if (!initialCrop.isFullFrame) {
                val cropped = applyCrop(bitmap, initialCrop)
                saveBitmapAsJpeg(cropped, processedFile, quality = 92)
                if (cropped !== bitmap) cropped.recycle()
            } else {
                originalFile.copyTo(processedFile, overwrite = true)
            }

            bitmap.recycle()

            ScanDraftPage(
                id = pageId,
                originalFilePath = originalFile.absolutePath,
                processedFilePath = processedFile.absolutePath,
                cropRect = initialCrop
            )
        }
    }

    /**
     * Creates a ScanDraftPage from a newly captured camera image file.
     */
    suspend fun createScanPageFromCameraFile(
        rawCameraFile: File,
        workspaceDir: File,
        autoDetectEdges: Boolean = true
    ): Result<ScanDraftPage> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeSampledBitmapFromFile(rawCameraFile, MAX_WORKING_DIMENSION)
                ?: throw IllegalArgumentException("Captured camera image could not be decoded.")

            val pageId = UUID.randomUUID().toString()
            val originalFile = File(workspaceDir, "orig_$pageId.jpg")
            val processedFile = File(workspaceDir, "proc_$pageId.jpg")

            saveBitmapAsJpeg(bitmap, originalFile, quality = 95)
            if (rawCameraFile.exists() && rawCameraFile.absolutePath != originalFile.absolutePath) {
                rawCameraFile.delete()
            }

            val detectedBounds = if (autoDetectEdges) {
                detectDocumentBounds(bitmap)
            } else {
                NormalizedRect()
            }

            val draft = ScanDraftPage(
                id = pageId,
                originalFilePath = originalFile.absolutePath,
                processedFilePath = processedFile.absolutePath,
                filterMode = DocumentFilterMode.AUTO_ENHANCE,
                cropRect = detectedBounds
            )

            val enhanced = renderEnhancedBitmap(bitmap, draft)
            saveBitmapAsJpeg(enhanced, processedFile, quality = 92)
            if (enhanced !== bitmap) enhanced.recycle()
            bitmap.recycle()

            draft
        }
    }

    /**
     * Re-renders a ScanDraftPage from its untouched originalFilePath using the current
     * crop, rotation, filter mode, brightness, and contrast settings.
     */
    suspend fun reprocessDraftPage(page: ScanDraftPage): Result<ScanDraftPage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val origFile = File(page.originalFilePath)
                if (!origFile.exists()) {
                    throw IllegalStateException("Original source image not found.")
                }
                val sourceBitmap = decodeSampledBitmapFromFile(origFile, MAX_WORKING_DIMENSION)
                    ?: throw IllegalStateException("Could not decode source image.")

                val rendered = renderEnhancedBitmap(sourceBitmap, page)
                val targetFile = File(page.processedFilePath)
                saveBitmapAsJpeg(rendered, targetFile, quality = 92)

                if (rendered !== sourceBitmap) rendered.recycle()
                sourceBitmap.recycle()

                page.copy(revision = page.revision + 1)
            }
        }

    /**
     * Pure bitmap enhancement pipeline (crop -> rotate -> filter/brightness/contrast).
     */
    fun renderEnhancedBitmap(source: Bitmap, page: ScanDraftPage): Bitmap {
        // 1. Crop
        val cropped = applyCrop(source, page.cropRect)

        // 2. Rotate
        val rotated = if (page.rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate((page.rotationDegrees % 360).toFloat()) }
            val rotBmp = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            if (cropped !== source && rotBmp !== cropped) {
                cropped.recycle()
            }
            rotBmp
        } else {
            cropped
        }

        // 3. Filter + Brightness + Contrast
        val needsColorProcessing = page.filterMode != DocumentFilterMode.ORIGINAL ||
            abs(page.brightness) > 0.5f ||
            abs(page.contrast - 1.0f) > 0.02f

        if (!needsColorProcessing) {
            return rotated
        }

        val result = if (page.filterMode == DocumentFilterMode.BLACK_AND_WHITE) {
            applyAdaptiveBlackAndWhite(rotated, page.brightness, page.contrast)
        } else {
            val output = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            paint.colorFilter = ColorMatrixColorFilter(
                buildColorMatrix(
                    filterMode = page.filterMode,
                    brightness = page.brightness,
                    contrast = page.contrast
                )
            )
            canvas.drawBitmap(rotated, 0f, 0f, paint)
            output
        }

        if (rotated !== source && rotated !== result) {
            rotated.recycle()
        }
        return result
    }

    private fun applyCrop(source: Bitmap, rect: NormalizedRect): Bitmap {
        val safe = rect.coerced()
        if (safe.isFullFrame) return source

        val x = (safe.left * source.width).roundToInt().coerceIn(0, source.width - 1)
        val y = (safe.top * source.height).roundToInt().coerceIn(0, source.height - 1)
        val w = ((safe.right - safe.left) * source.width).roundToInt().coerceIn(1, source.width - x)
        val h = ((safe.bottom - safe.top) * source.height).roundToInt().coerceIn(1, source.height - y)

        return Bitmap.createBitmap(source, x, y, w, h)
    }

    private fun buildColorMatrix(
        filterMode: DocumentFilterMode,
        brightness: Float,
        contrast: Float
    ): ColorMatrix {
        val combined = ColorMatrix()

        var effectiveContrast = contrast
        var effectiveBrightness = brightness

        when (filterMode) {
            DocumentFilterMode.ORIGINAL -> Unit
            DocumentFilterMode.AUTO_ENHANCE -> {
                // Document enhancement: boost contrast and slightly lift paper whites
                effectiveContrast *= 1.28f
                effectiveBrightness += 14f
            }
            DocumentFilterMode.GRAYSCALE -> {
                val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
                combined.postConcat(grayMatrix)
                effectiveContrast *= 1.12f
            }
            DocumentFilterMode.BLACK_AND_WHITE -> {
                val grayMatrix = ColorMatrix().apply { setSaturation(0f) }
                combined.postConcat(grayMatrix)
            }
        }

        val translate = (-0.5f * effectiveContrast + 0.5f) * 255f + effectiveBrightness
        val bcMatrix = ColorMatrix(
            floatArrayOf(
                effectiveContrast, 0f, 0f, 0f, translate,
                0f, effectiveContrast, 0f, 0f, translate,
                0f, 0f, effectiveContrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        combined.postConcat(bcMatrix)
        return combined
    }

    /**
     * High-contrast adaptive document thresholding for Black & White mode.
     */
    private fun applyAdaptiveBlackAndWhite(
        source: Bitmap,
        brightnessOffset: Float,
        contrastMultiplier: Float
    ): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        // Compute average luminance to establish an adaptive threshold
        var lumSum = 0L
        val sampleStep = max(1, (w * h) / 20000)
        var sampleCount = 0
        var idx = 0
        while (idx < pixels.size) {
            val c = pixels[idx]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lumSum += (r * 77 + g * 150 + b * 29) shr 8
            sampleCount++
            idx += sampleStep
        }
        val avgLum = if (sampleCount > 0) (lumSum / sampleCount).toInt() else 140
        val baseThreshold = (avgLum * 0.92f - brightnessOffset * 0.6f).roundToInt().coerceIn(70, 210)
        val softness = (28f / contrastMultiplier.coerceIn(0.6f, 2.0f)).roundToInt().coerceAtLeast(6)

        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 77 + g * 150 + b * 29) shr 8

            val outVal = when {
                lum >= baseThreshold + softness -> 255
                lum <= baseThreshold - softness -> 12
                else -> {
                    val ratio = (lum - (baseThreshold - softness)).toFloat() / (softness * 2f)
                    (12 + ratio * 243f).roundToInt().coerceIn(12, 255)
                }
            }
            pixels[i] = (0xFF shl 24) or (outVal shl 16) or (outVal shl 8) or outVal
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Advanced heuristic document boundary detector.
     * Identifies document paper framed against backgrounds (desks, tables, carpets, surfaces).
     * Computes multi-row/col luminance profiles and edge gradient transitions.
     * When distinct paper borders are identified, returns the tight normalized rectangle
     * surrounding the paper with a small margin padding (so content is never truncated).
     * If the document already fills the frame (>95%) or no clear border is present,
     * safely returns the full frame NormalizedRect().
     */
    fun detectDocumentBounds(bitmap: Bitmap): NormalizedRect {
        val gridW = 96
        val gridH = 96
        if (bitmap.width < 32 || bitmap.height < 32) return NormalizedRect()

        val stepX = bitmap.width.toFloat() / gridW
        val stepY = bitmap.height.toFloat() / gridH
        val lumGrid = IntArray(gridW * gridH)

        for (gy in 0 until gridH) {
            val srcY = (gy * stepY).toInt().coerceIn(0, bitmap.height - 1)
            for (gx in 0 until gridW) {
                val srcX = (gx * stepX).toInt().coerceIn(0, bitmap.width - 1)
                val c = bitmap.getPixel(srcX, srcY)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                lumGrid[gy * gridW + gx] = (r * 77 + g * 150 + b * 29) shr 8
            }
        }

        fun lumAt(x: Int, y: Int): Int {
            val cx = x.coerceIn(0, gridW - 1)
            val cy = y.coerceIn(0, gridH - 1)
            return lumGrid[cy * gridW + cx]
        }

        // 1. Analyze central area vs border perimeter
        var centerSum = 0L
        var centerCount = 0
        val cXStart = (gridW * 0.28f).toInt()
        val cXEnd = (gridW * 0.72f).toInt()
        val cYStart = (gridH * 0.28f).toInt()
        val cYEnd = (gridH * 0.72f).toInt()

        for (y in cYStart..cYEnd) {
            for (x in cXStart..cXEnd) {
                centerSum += lumAt(x, y)
                centerCount++
            }
        }
        val centerAvg = if (centerCount > 0) (centerSum / centerCount).toInt() else 160

        var borderSum = 0L
        var borderCount = 0
        // Sample outermost rows and columns
        for (y in 0 until gridH) {
            borderSum += lumAt(0, y) + lumAt(1, y) + lumAt(gridW - 2, y) + lumAt(gridW - 1, y)
            borderCount += 4
        }
        for (x in 2 until gridW - 2) {
            borderSum += lumAt(x, 0) + lumAt(x, 1) + lumAt(x, gridH - 2) + lumAt(x, gridH - 1)
            borderCount += 4
        }
        val borderAvg = if (borderCount > 0) (borderSum / borderCount).toInt() else 140

        val lumDiff = centerAvg - borderAvg
        // If there's negligible contrast between center and perimeter (e.g. document already covers frame),
        // keep full frame.
        if (abs(lumDiff) < 18) {
            return NormalizedRect()
        }

        val paperIsBrighter = lumDiff > 0
        val threshold = (borderAvg + lumDiff * 0.38f).roundToInt()

        fun isPaperPixel(x: Int, y: Int): Boolean {
            val l = lumAt(x, y)
            return if (paperIsBrighter) l >= threshold else l <= threshold
        }

        val maxScanInwardX = (gridW * 0.45f).toInt()
        val maxScanInwardY = (gridH * 0.45f).toInt()
        val sampleMinX = (gridW * 0.18f).toInt()
        val sampleMaxX = (gridW * 0.82f).toInt()
        val sampleSpanX = (sampleMaxX - sampleMinX + 1).coerceAtLeast(1)

        val sampleMinY = (gridH * 0.18f).toInt()
        val sampleMaxY = (gridH * 0.82f).toInt()
        val sampleSpanY = (sampleMaxY - sampleMinY + 1).coerceAtLeast(1)

        // Find top edge (scan downwards from y=0 to maxScanInwardY)
        var topEdge = 0
        for (y in 0..maxScanInwardY) {
            var paperCount = 0
            for (x in sampleMinX..sampleMaxX) {
                if (isPaperPixel(x, y)) paperCount++
            }
            if (paperCount.toFloat() / sampleSpanX >= 0.55f) {
                topEdge = y
                break
            }
        }

        // Find bottom edge (scan upwards from y=gridH-1 down to gridH - maxScanInwardY)
        var bottomEdge = gridH - 1
        for (y in (gridH - 1) downTo (gridH - maxScanInwardY)) {
            var paperCount = 0
            for (x in sampleMinX..sampleMaxX) {
                if (isPaperPixel(x, y)) paperCount++
            }
            if (paperCount.toFloat() / sampleSpanX >= 0.55f) {
                bottomEdge = y
                break
            }
        }

        // Find left edge (scan rightwards from x=0 to maxScanInwardX)
        var leftEdge = 0
        for (x in 0..maxScanInwardX) {
            var paperCount = 0
            for (y in sampleMinY..sampleMaxY) {
                if (isPaperPixel(x, y)) paperCount++
            }
            if (paperCount.toFloat() / sampleSpanY >= 0.55f) {
                leftEdge = x
                break
            }
        }

        // Find right edge (scan leftwards from x=gridW-1 down to gridW - maxScanInwardX)
        var rightEdge = gridW - 1
        for (x in (gridW - 1) downTo (gridW - maxScanInwardX)) {
            var paperCount = 0
            for (y in sampleMinY..sampleMaxY) {
                if (isPaperPixel(x, y)) paperCount++
            }
            if (paperCount.toFloat() / sampleSpanY >= 0.55f) {
                rightEdge = x
                break
            }
        }

        // If detected boundaries collapsed or inverted, return full frame
        if (rightEdge <= leftEdge + (gridW * 0.2f) || bottomEdge <= topEdge + (gridH * 0.2f)) {
            return NormalizedRect()
        }

        // Convert to normalized coordinates (0f .. 1f)
        val rawLeft = leftEdge.toFloat() / gridW
        val rawTop = topEdge.toFloat() / gridH
        val rawRight = (rightEdge + 1).toFloat() / gridW
        val rawBottom = (bottomEdge + 1).toFloat() / gridH

        // If document already fills > 96% of the screen horizontally and vertically, no crop needed
        if (rawLeft <= 0.02f && rawTop <= 0.02f && rawRight >= 0.98f && rawBottom >= 0.98f) {
            return NormalizedRect()
        }

        // Add 1.2% safety margin padding so text right next to page edge isn't cut off
        val padX = 0.012f
        val padY = 0.012f
        val finalLeft = (rawLeft - padX).coerceIn(0f, 0.45f)
        val finalTop = (rawTop - padY).coerceIn(0f, 0.45f)
        val finalRight = (rawRight + padX).coerceIn(0.55f, 1f)
        val finalBottom = (rawBottom + padY).coerceIn(0.55f, 1f)

        return NormalizedRect(
            left = finalLeft,
            top = finalTop,
            right = finalRight,
            bottom = finalBottom
        ).coerced()
    }

    fun decodeSampledBitmapFromFile(file: File, maxDimension: Int = MAX_WORKING_DIMENSION): Bitmap? {
        if (!file.exists()) return null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(options.outWidth, options.outHeight, maxDimension)
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888

        val rawBitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val exifRotation = runCatching {
            val exif = ExifInterface(file.absolutePath)
            exifToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
        }.getOrDefault(0)

        return rotateIfRequired(rawBitmap, exifRotation)
    }

    fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_WORKING_DIMENSION
    ): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(boundsOptions.outWidth, boundsOptions.outHeight, maxDimension)
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val exifRotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                exifToDegrees(exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
            } ?: 0
        }.getOrDefault(0)

        return rotateIfRequired(rawBitmap, exifRotation)
    }

    fun saveBitmapAsJpeg(bitmap: Bitmap, destFile: File, quality: Int = 92) {
        destFile.parentFile?.mkdirs()
        FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(40, 100), out)
            out.flush()
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val largest = max(width, height)
        while (largest / inSampleSize > maxDim) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun exifToDegrees(exifOrientation: Int): Int = when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
        ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
        else -> 0
    }

    private fun rotateIfRequired(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
