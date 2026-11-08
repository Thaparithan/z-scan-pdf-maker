package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.processing.DocumentFilterMode
import com.example.processing.ImageProcessor
import com.example.processing.NormalizedRect
import com.example.processing.ScanDraftPage
import com.example.ui.theme.CeruleanBlue
import com.example.ui.theme.CeruleanPrimaryDark
import com.example.ui.theme.JetBlack
import com.example.ui.theme.NeonRose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PageEditorScreen(
    page: ScanDraftPage,
    isSaving: Boolean,
    onSaveEdits: (ScanDraftPage) -> Unit,
    onResetToOriginal: () -> Unit,
    onBackClick: () -> Unit
) {
    BackHandler(onBack = onBackClick)

    var filterMode by remember(page.id) { mutableStateOf(page.filterMode) }
    var brightness by remember(page.id) { mutableFloatStateOf(page.brightness) }
    var contrast by remember(page.id) { mutableFloatStateOf(page.contrast) }
    var rotationDegrees by remember(page.id) { mutableIntStateOf(page.rotationDegrees) }
    var cropRect by remember(page.id) { mutableStateOf(page.cropRect) }
    var isCropModeActive by remember { mutableStateOf(false) }
    var showOriginalComparison by remember { mutableStateOf(false) }

    var originalBitmap by remember(page.originalFilePath) { mutableStateOf<Bitmap?>(null) }
    var livePreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Load moderately sized working bitmap for responsive live preview
    LaunchedEffect(page.originalFilePath) {
        withContext(Dispatchers.IO) {
            originalBitmap = ImageProcessor.decodeSampledBitmapFromFile(
                File(page.originalFilePath),
                maxDimension = 1024
            )
        }
    }

    // Update live preview whenever filter, brightness, contrast, rotation, or crop changes
    LaunchedEffect(originalBitmap, filterMode, brightness, contrast, rotationDegrees, cropRect, isCropModeActive) {
        val src = originalBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val previewDraft = page.copy(
                filterMode = filterMode,
                brightness = brightness,
                contrast = contrast,
                rotationDegrees = if (isCropModeActive) 0 else rotationDegrees,
                cropRect = if (isCropModeActive) NormalizedRect() else cropRect
            )
            livePreviewBitmap = ImageProcessor.renderEnhancedBitmap(src, previewDraft)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit & Enhance Page") },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            filterMode = DocumentFilterMode.ORIGINAL
                            brightness = 0f
                            contrast = 1.0f
                            rotationDegrees = 0
                            cropRect = NormalizedRect()
                            isCropModeActive = false
                            onResetToOriginal()
                        },
                        modifier = Modifier.testTag("editor_reset_button")
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Live Image Preview Canvas with Before/After & Interactive Crop Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(JetBlack)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val displayBmp = if (showOriginalComparison) originalBitmap else livePreviewBitmap
                    if (displayBmp != null) {
                        if (isCropModeActive && originalBitmap != null) {
                            InteractiveCropCanvas(
                                bitmap = originalBitmap!!,
                                cropRect = cropRect,
                                onCropRectChange = { cropRect = it },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        } else {
                            Image(
                                bitmap = displayBmp.asImageBitmap(),
                                contentDescription = "Edited document preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                            )
                        }
                    } else {
                        CircularProgressIndicator(color = CeruleanPrimaryDark)
                    }

                    // Before / After Comparison Pill
                    if (!isCropModeActive) {
                        Surface(
                            onClick = { showOriginalComparison = !showOriginalComparison },
                            color = if (showOriginalComparison) NeonRose else JetBlack.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .testTag("before_after_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Compare,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (showOriginalComparison) "Showing: Original" else "Compare Original",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // 2. Quick Geometry Tools: Crop Mode, Auto-Detect Bounds, Rotate 90°
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { isCropModeActive = !isCropModeActive },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isCropModeActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toggle_crop_mode_button")
                    ) {
                        Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isCropModeActive) "Done Cropping" else "Crop Page")
                    }

                    if (isCropModeActive) {
                        OutlinedButton(
                            onClick = {
                                originalBitmap?.let { bmp ->
                                    cropRect = ImageProcessor.detectDocumentBounds(bmp)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto Detect Paper")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("editor_rotate_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Rotate (${rotationDegrees}°)")
                        }
                    }
                }

                // 3. Document Enhancement Filter Modes
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Document Filter",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DocumentFilterMode.entries.forEach { mode ->
                            FilterChip(
                                selected = filterMode == mode,
                                onClick = {
                                    filterMode = mode
                                    showOriginalComparison = false
                                },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("filter_chip_${mode.name}")
                            )
                        }
                    }
                    Text(
                        text = filterMode.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 4. Brightness & Contrast Sliders
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Brightness",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "${if (brightness >= 0) "+" else ""}${brightness.roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = brightness,
                        onValueChange = {
                            brightness = it
                            showOriginalComparison = false
                        },
                        valueRange = -80f..80f,
                        modifier = Modifier.testTag("brightness_slider")
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Contrast",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "${(contrast * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = contrast,
                        onValueChange = {
                            contrast = it
                            showOriginalComparison = false
                        },
                        valueRange = 0.6f..1.8f,
                        modifier = Modifier.testTag("contrast_slider")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 5. Save Changes Button
                Button(
                    onClick = {
                        val updated = page.copy(
                            filterMode = filterMode,
                            brightness = brightness,
                            contrast = contrast,
                            rotationDegrees = rotationDegrees,
                            cropRect = cropRect.coerced()
                        )
                        onSaveEdits(updated)
                    },
                    enabled = !isSaving,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("save_page_edits_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Apply Changes to Page",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveCropCanvas(
    bitmap: Bitmap,
    cropRect: NormalizedRect,
    onCropRectChange: (NormalizedRect) -> Unit,
    modifier: Modifier = Modifier
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var currentRect by remember(cropRect) { mutableStateOf(cropRect) }

    Box(
        modifier = modifier.onSizeChanged { boxSize = it },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Crop document image",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        if (boxSize.width > 0 && boxSize.height > 0) {
            val bmpRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
            val boxRatio = boxSize.width.toFloat() / boxSize.height.toFloat().coerceAtLeast(1f)

            val drawnW: Float
            val drawnH: Float
            if (bmpRatio > boxRatio) {
                drawnW = boxSize.width.toFloat()
                drawnH = drawnW / bmpRatio
            } else {
                drawnH = boxSize.height.toFloat()
                drawnW = drawnH * bmpRatio
            }
            val offsetX = (boxSize.width - drawnW) / 2f
            val offsetY = (boxSize.height - drawnH) / 2f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(drawnW, drawnH, offsetX, offsetY) {
                        var activeCorner = -1
                        detectDragGestures(
                            onDragStart = { touch ->
                                val tl = Offset(offsetX + currentRect.left * drawnW, offsetY + currentRect.top * drawnH)
                                val tr = Offset(offsetX + currentRect.right * drawnW, offsetY + currentRect.top * drawnH)
                                val bl = Offset(offsetX + currentRect.left * drawnW, offsetY + currentRect.bottom * drawnH)
                                val br = Offset(offsetX + currentRect.right * drawnW, offsetY + currentRect.bottom * drawnH)

                                val distances = listOf(
                                    0 to hypot(touch.x - tl.x, touch.y - tl.y),
                                    1 to hypot(touch.x - tr.x, touch.y - tr.y),
                                    2 to hypot(touch.x - bl.x, touch.y - bl.y),
                                    3 to hypot(touch.x - br.x, touch.y - br.y)
                                )
                                activeCorner = distances.minByOrNull { it.second }?.first ?: 0
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val dx = dragAmount.x / drawnW.coerceAtLeast(1f)
                                val dy = dragAmount.y / drawnH.coerceAtLeast(1f)

                                val next = when (activeCorner) {
                                    0 -> currentRect.copy(left = currentRect.left + dx, top = currentRect.top + dy)
                                    1 -> currentRect.copy(right = currentRect.right + dx, top = currentRect.top + dy)
                                    2 -> currentRect.copy(left = currentRect.left + dx, bottom = currentRect.bottom + dy)
                                    else -> currentRect.copy(right = currentRect.right + dx, bottom = currentRect.bottom + dy)
                                }.coerced()
                                currentRect = next
                                onCropRectChange(next)
                            }
                        )
                    }
            ) {
                val leftPx = offsetX + currentRect.left * drawnW
                val topPx = offsetY + currentRect.top * drawnH
                val rightPx = offsetX + currentRect.right * drawnW
                val bottomPx = offsetY + currentRect.bottom * drawnH

                // Crop rectangle border
                drawRect(
                    color = CeruleanPrimaryDark,
                    topLeft = Offset(leftPx, topPx),
                    size = Size(rightPx - leftPx, bottomPx - topPx),
                    style = Stroke(width = 3.dp.toPx())
                )

                // Rule of thirds guide lines
                val thirdW = (rightPx - leftPx) / 3f
                val thirdH = (bottomPx - topPx) / 3f
                for (i in 1..2) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.45f),
                        start = Offset(leftPx + thirdW * i, topPx),
                        end = Offset(leftPx + thirdW * i, bottomPx),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.45f),
                        start = Offset(leftPx, topPx + thirdH * i),
                        end = Offset(rightPx, topPx + thirdH * i),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 4 Corner Handles
                val handleRadius = 10.dp.toPx()
                val corners = listOf(
                    Offset(leftPx, topPx),
                    Offset(rightPx, topPx),
                    Offset(leftPx, bottomPx),
                    Offset(rightPx, bottomPx)
                )
                corners.forEach { pt ->
                    drawCircle(color = Color.White, radius = handleRadius, center = pt)
                    drawCircle(color = NeonRose, radius = handleRadius * 0.65f, center = pt)
                }
            }
        }
    }
}
