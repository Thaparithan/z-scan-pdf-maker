package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// =========================================================================
// Z-SCAN — PREMIUM THEME DESIGN SYSTEM (BLACK + ROYAL PURPLE & AURORA)
// =========================================================================

// 1. DARK MODE — BLACK + PURPLE
val DarkPrimaryBackground = Color(0xFF090611)
val DarkSecondaryBackground = Color(0xFF100D19)
val DarkCardBackground = Color(0xFF171321)
val DarkElevatedSurface = Color(0xFF211B30)
val DarkPrimaryPurple = Color(0xFF7040B8)
val DarkElectricViolet = Color(0xFFA477FF)
val DarkSoftLavender = Color(0xFFD4C4FF)
val DarkPrimaryText = Color(0xFFF8F7FC)
val DarkSecondaryText = Color(0xFFAFA8BE)
val DarkMutedText = Color(0xFF777185)
val DarkBorder = Color(0xFF30283D)

// 2. LIGHT MODE — PEARL WHITE + ROYAL PURPLE
val LightPrimaryBackground = Color(0xFFF8F7FC)
val LightSecondaryBackground = Color(0xFFF0EDF6)
val LightCardBackground = Color(0xFFFFFFFF)
val LightElevatedSurface = Color(0xFFF5F1FA)
val LightPrimaryPurple = Color(0xFF7040B8)
val LightDeepPurple = Color(0xFF57318F)
val LightAccentViolet = Color(0xFF8B63D9)
val LightSoftLavender = Color(0xFFE8DDFB)
val LightPrimaryText = Color(0xFF171321)
val LightSecondaryText = Color(0xFF6F687B)
val LightMutedText = Color(0xFF9992A4)
val LightBorder = Color(0xFFE5DFEE)

// Core Named Aliases for Cross-Component Consistency
val PrimaryPurple = Color(0xFF7040B8)
val ElectricViolet = Color(0xFFA477FF)
val SoftLavender = Color(0xFFD4C4FF)
val DeepPurple = Color(0xFF57318F)
val AccentViolet = Color(0xFF8B63D9)

// Backwards-compatible aliases mapping to new luxury palette (No Red!)
val JetBlack = DarkPrimaryBackground
val CeruleanBlue = ElectricViolet
val NeonRose = ElectricViolet
val CeruleanPrimaryDark = ElectricViolet
val CeruleanPrimaryLight = PrimaryPurple
val CeruleanContainerDark = DarkElevatedSurface
val CeruleanContainerLight = LightSoftLavender

// Semantic Status Colors
val SuccessGreen = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRedLight = Color(0xFFE11D48)
val ErrorRedDark = Color(0xFFFB7185)

// 3. AURORA EFFECTS — SIGNATURE VISUAL IDENTITY
val AuroraDarkColors = listOf(
    Color(0xFF21103D),
    Color(0xFF482276),
    Color(0xFF7040B8),
    Color(0xFFA477FF)
)

val AuroraLightColors = listOf(
    Color(0xFFE8DDFB),
    Color(0xFFD6C5FA),
    Color(0xFFB99AF2),
    Color(0xFF8B63D9)
)

fun auroraLinearGradient(isDark: Boolean): Brush = Brush.linearGradient(
    colors = if (isDark) AuroraDarkColors else AuroraLightColors
)

fun auroraRadialGradient(isDark: Boolean, radius: Float = 600f): Brush = Brush.radialGradient(
    colors = if (isDark) listOf(Color(0xFF7040B8).copy(alpha = 0.45f), Color(0xFF482276).copy(alpha = 0.20f), Color.Transparent)
             else listOf(Color(0xFFB99AF2).copy(alpha = 0.55f), Color(0xFFE8DDFB).copy(alpha = 0.25f), Color.Transparent),
    radius = radius
)
