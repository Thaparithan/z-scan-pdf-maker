package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CeruleanBlue
import com.example.ui.theme.CeruleanPrimaryDark
import com.example.ui.theme.JetBlack
import com.example.ui.theme.NeonRose

/**
 * Official Z Scan: PDF Maker vector brand mark.
 * Combines a modern document silhouette, scanner corner frame,
 * integrated geometric 'Z', and Neon Rose scanning laser line.
 */
@Composable
fun ZScanBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    animatedScanLine: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "zscan_logo_anim")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line_y"
    )
    val laserYRatio = if (animatedScanLine) scanProgress else 0.52f

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Z Scan: PDF Maker Logo" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val w = this.size.width
            val h = this.size.height

            // 1. Rounded Squircle Badge Base (Jet Black -> Deep Cerulean gradient)
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(JetBlack, Color(0xFF072B3B), CeruleanBlue),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                cornerRadius = CornerRadius(w * 0.24f, h * 0.24f)
            )

            // 2. Scanner Corner Brackets (Bright Cyan-Cerulean)
            val bracketStroke = Stroke(width = w * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val bInset = w * 0.16f
            val bLen = w * 0.14f
            val bracketColor = CeruleanPrimaryDark

            // Top-Left Bracket
            val tlPath = Path().apply {
                moveTo(bInset, bInset + bLen)
                lineTo(bInset, bInset)
                lineTo(bInset + bLen, bInset)
            }
            drawPath(tlPath, color = bracketColor, style = bracketStroke)

            // Top-Right Bracket
            val trPath = Path().apply {
                moveTo(w - bInset - bLen, bInset)
                lineTo(w - bInset, bInset)
                lineTo(w - bInset, bInset + bLen)
            }
            drawPath(trPath, color = bracketColor, style = bracketStroke)

            // Bottom-Left Bracket
            val blPath = Path().apply {
                moveTo(bInset, h - bInset - bLen)
                lineTo(bInset, h - bInset)
                lineTo(bInset + bLen, h - bInset)
            }
            drawPath(blPath, color = bracketColor, style = bracketStroke)

            // Bottom-Right Bracket
            val brPath = Path().apply {
                moveTo(w - bInset - bLen, h - bInset)
                lineTo(w - bInset, h - bInset)
                lineTo(w - bInset, h - bInset - bLen)
            }
            drawPath(brPath, color = bracketColor, style = bracketStroke)

            // 3. Document Page with Folded Corner
            val docLeft = w * 0.27f
            val docTop = h * 0.23f
            val docRight = w * 0.73f
            val docBottom = h * 0.77f
            val foldSize = w * 0.13f

            val docPath = Path().apply {
                moveTo(docLeft, docTop)
                lineTo(docRight - foldSize, docTop)
                lineTo(docRight, docTop + foldSize)
                lineTo(docRight, docBottom)
                lineTo(docLeft, docBottom)
                close()
            }
            drawPath(docPath, color = Color.White)

            val foldPath = Path().apply {
                moveTo(docRight - foldSize, docTop)
                lineTo(docRight - foldSize, docTop + foldSize)
                lineTo(docRight, docTop + foldSize)
                close()
            }
            drawPath(foldPath, color = Color(0xFFBAE6FD))

            // 4. Geometric 'Z' Monogram inside the Document
            val zPath = Path().apply {
                moveTo(w * 0.37f, h * 0.39f)
                lineTo(w * 0.63f, h * 0.39f)
                lineTo(w * 0.39f, h * 0.63f)
                lineTo(w * 0.63f, h * 0.63f)
            }
            drawPath(
                path = zPath,
                color = CeruleanBlue,
                style = Stroke(
                    width = w * 0.075f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 5. Neon Rose Laser Scan Line
            val scanY = h * laserYRatio
            drawLine(
                color = NeonRose.copy(alpha = 0.35f),
                start = Offset(w * 0.13f, scanY),
                end = Offset(w * 0.87f, scanY),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = NeonRose,
                start = Offset(w * 0.14f, scanY),
                end = Offset(w * 0.86f, scanY),
                strokeWidth = w * 0.038f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Custom illustration for the Home Screen primary Scan Document hero card.
 */
@Composable
fun ScannerHeroIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 92.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_hero_beam")
    val beamRatio by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam_y"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Document scanner illustration" }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Ambient glow circle
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f),
                    NeonRose.copy(alpha = 0.12f),
                    Color.Transparent
                )
            ),
            radius = w * 0.48f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // Tilted back sheet
        drawRoundRect(
            color = Color.White.copy(alpha = 0.25f),
            topLeft = Offset(w * 0.26f, h * 0.14f),
            size = Size(w * 0.52f, h * 0.68f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )

        // Foreground crisp document page
        val docLeft = w * 0.20f
        val docTop = h * 0.18f
        val docW = w * 0.54f
        val docH = h * 0.66f
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(docLeft, docTop),
            size = Size(docW, docH),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )

        // Document text lines & PDF badge hint
        val lineLeft = docLeft + docW * 0.16f
        val lineRight = docLeft + docW * 0.84f
        drawLine(
            color = CeruleanBlue,
            start = Offset(lineLeft, docTop + docH * 0.22f),
            end = Offset(docLeft + docW * 0.55f, docTop + docH * 0.22f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )
        for (i in 1..3) {
            val y = docTop + docH * (0.28f + i * 0.14f)
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(lineLeft, y),
                end = Offset(if (i == 3) docLeft + docW * 0.65f else lineRight, y),
                strokeWidth = w * 0.032f,
                cap = StrokeCap.Round
            )
        }

        // Scanner Viewfinder Corner Brackets
        val bracketStroke = Stroke(width = w * 0.042f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val bLeft = w * 0.11f
        val bRight = w * 0.83f
        val bTop = h * 0.10f
        val bBot = h * 0.90f
        val bArm = w * 0.12f

        val brackets = Path().apply {
            moveTo(bLeft, bTop + bArm)
            lineTo(bLeft, bTop)
            lineTo(bLeft + bArm, bTop)

            moveTo(bRight - bArm, bTop)
            lineTo(bRight, bTop)
            lineTo(bRight, bTop + bArm)

            moveTo(bLeft, bBot - bArm)
            lineTo(bLeft, bBot)
            lineTo(bLeft + bArm, bBot)

            moveTo(bRight - bArm, bBot)
            lineTo(bRight, bBot)
            lineTo(bRight, bBot - bArm)
        }
        drawPath(brackets, color = Color(0xFF7DD3FC), style = bracketStroke)

        // Neon Rose laser scan line
        val scanY = h * beamRatio
        drawLine(
            color = NeonRose.copy(alpha = 0.35f),
            start = Offset(bLeft - w * 0.02f, scanY),
            end = Offset(bRight + w * 0.02f, scanY),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = NeonRose,
            start = Offset(bLeft, scanY),
            end = Offset(bRight, scanY),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Friendly, clean illustration for Empty Document states in Home and My Documents.
 */
@Composable
fun EmptyDocumentsIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 128.dp
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Empty documents illustration" }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Soft circular backdrop
        drawCircle(
            color = CeruleanBlue.copy(alpha = 0.10f),
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.52f)
        )
        drawCircle(
            color = NeonRose.copy(alpha = 0.08f),
            radius = w * 0.28f,
            center = Offset(w * 0.64f, h * 0.38f)
        )

        // Back Folder Tab
        drawRoundRect(
            color = CeruleanBlue.copy(alpha = 0.32f),
            topLeft = Offset(w * 0.20f, h * 0.34f),
            size = Size(w * 0.56f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )

        // Floating Document Card
        val pageLeft = w * 0.28f
        val pageTop = h * 0.20f
        val pageW = w * 0.44f
        val pageH = h * 0.56f

        drawRoundRect(
            color = Color.White,
            topLeft = Offset(pageLeft, pageTop),
            size = Size(pageW, pageH),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f)
        )
        drawRoundRect(
            color = CeruleanBlue.copy(alpha = 0.45f),
            topLeft = Offset(pageLeft, pageTop),
            size = Size(pageW, pageH),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
            style = Stroke(width = w * 0.022f)
        )

        // Document Content Lines
        val lineStart = pageLeft + pageW * 0.18f
        val lineEnd = pageLeft + pageW * 0.82f
        drawLine(
            color = CeruleanBlue,
            start = Offset(lineStart, pageTop + pageH * 0.24f),
            end = Offset(pageLeft + pageW * 0.58f, pageTop + pageH * 0.24f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFCBD5E1),
            start = Offset(lineStart, pageTop + pageH * 0.42f),
            end = Offset(lineEnd, pageTop + pageH * 0.42f),
            strokeWidth = w * 0.028f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFCBD5E1),
            start = Offset(lineStart, pageTop + pageH * 0.56f),
            end = Offset(lineEnd, pageTop + pageH * 0.56f),
            strokeWidth = w * 0.028f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFCBD5E1),
            start = Offset(lineStart, pageTop + pageH * 0.70f),
            end = Offset(pageLeft + pageW * 0.62f, pageTop + pageH * 0.70f),
            strokeWidth = w * 0.028f,
            cap = StrokeCap.Round
        )

        // Neon Rose Plus / Scan Badge at bottom-right
        val badgeCenter = Offset(w * 0.70f, h * 0.72f)
        val badgeRadius = w * 0.13f
        drawCircle(
            color = NeonRose,
            radius = badgeRadius,
            center = badgeCenter
        )
        val plusHalf = badgeRadius * 0.45f
        drawLine(
            color = Color.White,
            start = Offset(badgeCenter.x - plusHalf, badgeCenter.y),
            end = Offset(badgeCenter.x + plusHalf, badgeCenter.y),
            strokeWidth = w * 0.032f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White,
            start = Offset(badgeCenter.x, badgeCenter.y - plusHalf),
            end = Offset(badgeCenter.x, badgeCenter.y + plusHalf),
            strokeWidth = w * 0.032f,
            cap = StrokeCap.Round
        )
    }
}
