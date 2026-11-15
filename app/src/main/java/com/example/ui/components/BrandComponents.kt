package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.SoftLavender

/**
 * Official Z Scan: PDF Maker brand mark.
 * Renders the circular luxury emblem with subtle animated electric violet laser sweep.
 */
@Composable
fun ZScanBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    animatedScanLine: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "zscan_logo_anim")
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_line_y"
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Z Scan: PDF Maker Logo" },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.zscan_brand_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.28f))
        )

        if (animatedScanLine) {
            Canvas(modifier = Modifier.size(size)) {
                val w = this.size.width
                val h = this.size.height
                val scanY = h * scanProgress

                drawLine(
                    color = ElectricViolet.copy(alpha = 0.35f),
                    start = Offset(w * 0.12f, scanY),
                    end = Offset(w * 0.88f, scanY),
                    strokeWidth = w * 0.08f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = ElectricViolet,
                    start = Offset(w * 0.14f, scanY),
                    end = Offset(w * 0.86f, scanY),
                    strokeWidth = w * 0.035f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Custom illustration for the Home Screen primary Scan Document hero card
 * featuring the signature Purple Aurora ambient glow.
 */
@Composable
fun ScannerHeroIllustration(
    modifier: Modifier = Modifier,
    size: Dp = 92.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_hero_beam")
    val beamRatio by infiniteTransition.animateFloat(
        initialValue = 0.26f,
        targetValue = 0.74f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
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

        // Aurora Ambient glow circle
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ElectricViolet.copy(alpha = 0.28f),
                    PrimaryPurple.copy(alpha = 0.18f),
                    Color.Transparent
                )
            ),
            radius = w * 0.50f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // Tilted back sheet
        drawRoundRect(
            color = SoftLavender.copy(alpha = 0.22f),
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

        // Document text lines
        val lineLeft = docLeft + docW * 0.16f
        val lineRight = docLeft + docW * 0.84f
        drawLine(
            color = PrimaryPurple,
            start = Offset(lineLeft, docTop + docH * 0.22f),
            end = Offset(docLeft + docW * 0.55f, docTop + docH * 0.22f),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round
        )
        for (i in 1..3) {
            val y = docTop + docH * (0.28f + i * 0.14f)
            drawLine(
                color = Color(0xFFCBD5E1),
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
        drawPath(brackets, color = SoftLavender, style = bracketStroke)

        // Electric Violet laser scan line
        val scanY = h * beamRatio
        drawLine(
            color = ElectricViolet.copy(alpha = 0.35f),
            start = Offset(bLeft - w * 0.02f, scanY),
            end = Offset(bRight + w * 0.02f, scanY),
            strokeWidth = w * 0.08f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = ElectricViolet,
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

        // Soft circular backdrop in Aurora violet
        drawCircle(
            color = PrimaryPurple.copy(alpha = 0.12f),
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.52f)
        )
        drawCircle(
            color = ElectricViolet.copy(alpha = 0.10f),
            radius = w * 0.28f,
            center = Offset(w * 0.64f, h * 0.38f)
        )

        // Back Folder Tab
        drawRoundRect(
            color = PrimaryPurple.copy(alpha = 0.32f),
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
            color = PrimaryPurple.copy(alpha = 0.45f),
            topLeft = Offset(pageLeft, pageTop),
            size = Size(pageW, pageH),
            cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
            style = Stroke(width = w * 0.022f)
        )

        // Document Content Lines
        val lineStart = pageLeft + pageW * 0.18f
        val lineEnd = pageLeft + pageW * 0.82f
        drawLine(
            color = PrimaryPurple,
            start = Offset(lineStart, pageTop + pageH * 0.24f),
            end = Offset(pageLeft + pageW * 0.58f, pageTop + pageH * 0.24f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
        for (i in 1..3) {
            val y = pageTop + pageH * (0.38f + i * 0.14f)
            drawLine(
                color = Color(0xFFCBD5E1),
                start = Offset(lineStart, y),
                end = Offset(if (i == 3) pageLeft + pageW * 0.62f else lineEnd, y),
                strokeWidth = w * 0.028f,
                cap = StrokeCap.Round
            )
        }

        // Electric Violet Scan Badge at bottom-right
        val badgeCenter = Offset(w * 0.70f, h * 0.72f)
        val badgeRadius = w * 0.13f
        drawCircle(
            color = ElectricViolet,
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
