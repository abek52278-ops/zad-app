package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// =========================================================================
// ZAD Design System — "Zad Culinary & Wealth Heritage" (Apple iOS Cupertino HIG)
// Ultra-premium palette blending deep forest emerald, warm mustard ochre,
// and terracotta rust with crisp iOS light surfaces and frosted glass.
// =========================================================================

// --- 1. Core Heritage Palette ---
val ZadForestEmerald = Color(0xFF1B4332)   // Primary: High-trust, household health, financial wealth
val ZadForestEmeraldDark = Color(0xFF143225)
val ZadForestEmeraldLight = Color(0xFF2D6A4F)
val ZadEmeraldContainer = Color(0xFFE8F0EC)

val ZadMustardOchre = Color(0xFFC68216)    // Secondary: Due dates, budget warnings, pending actions
val ZadMustardDark = Color(0xFFA5680E)
val ZadMustardLight = Color(0xFFE9A844)
val ZadMustardContainer = Color(0xFFFBF4E7)

val ZadTerracottaRust = Color(0xFFD95726)  // Tertiary: Urgent pharmacy doses, debt deadlines, critical stock
val ZadTerracottaDark = Color(0xFFB84218)
val ZadTerracottaLight = Color(0xFFEA764B)
val ZadTerracottaContainer = Color(0xFFFDF0EA)

// --- 2. Surfaces & iOS Canvas ---
val ZadIosBackground = Color(0xFFF8F9FA)   // Soft iOS Light canvas
val ZadIosSurface = Color(0xFFFFFFFF)      // Pure elevated glass cards
val ZadIosSurfaceVariant = Color(0xFFEDEFE9) // Rounded container pills, icon backdrops
val ZadIosOutline = Color(0xFFE0E3DA)      // Thin hairline borders (0.5.dp)
val ZadNeutralDark = Color(0xFF1F1F14)     // High-contrast, pure text legibility
val ZadNeutralMuted = Color(0xFF6E7166)    // Secondary muted labels

// --- 3. Material 3 / App Theme Aliases (Backwards Compatibility) ---
val primary = ZadForestEmerald
val primaryDark = ZadForestEmeraldDark
val primaryLight = ZadForestEmeraldLight
val primaryContainer = ZadEmeraldContainer
val primaryFixed = ZadForestEmerald
val onPrimary = Color(0xFFFFFFFF)
val onPrimaryContainer = ZadForestEmerald

val secondary = ZadMustardOchre
val secondaryDark = ZadMustardDark
val secondaryLight = ZadMustardLight
val secondaryContainer = ZadMustardContainer
val onSecondary = Color(0xFFFFFFFF)
val onSecondaryContainer = ZadMustardDark

val tertiary = ZadTerracottaRust
val tertiaryContainer = ZadTerracottaContainer
val onTertiaryContainer = ZadTerracottaDark

val background = ZadIosBackground
val onBackground = ZadNeutralDark
val surface = ZadIosSurface
val onSurface = ZadNeutralDark
val surfaceVariant = ZadIosSurfaceVariant
val onSurfaceVariant = ZadNeutralMuted
val outline = ZadIosOutline
val outlineVariant = Color(0xFFE8EBE2)

// Surface Container Levels
val surfaceContainerLowest = Color(0xFFFFFFFF)
val surfaceContainerLow = Color(0xFFFBFBFA)
val surfaceContainer = ZadIosBackground
val surfaceContainerHigh = ZadIosSurfaceVariant
val surfaceContainerHighest = Color(0xFFE2E5DC)

// Neutral & Accent Tokens
val sand = ZadIosSurfaceVariant
val sandLight = ZadIosBackground
val sandDark = ZadIosOutline
val onSand = ZadNeutralDark

val coral = ZadTerracottaRust
val coralLight = ZadTerracottaContainer
val lilac = Color(0xFF7C6F93)
val lilacLight = Color(0xFFF2EFF7)
val carrotOrange = Color(0xFFF06A35)

val canvasTop = Color(0xFFF8F9FA)
val canvasMid = Color(0xFFF4F6F2)
val canvasBottom = Color(0xFFEDEFE9)

// Category Colors (Soft pastels with heritage accents)
val catBillsBg = Color(0x1AD95726)
val catBillsIcon = ZadTerracottaRust
val catBankingBg = Color(0x1A1B4332)
val catBankingIcon = ZadForestEmerald
val catFoodBg = Color(0x1AC68216)
val catFoodIcon = ZadMustardOchre
val catTransportBg = Color(0x1A5B7065)
val catTransportIcon = Color(0xFF3F554A)
val catSavingsBg = Color(0x1A2D6A4F)
val catSavingsIcon = ZadForestEmeraldLight
val catDailyBg = Color(0x1AE2847A)
val catDailyIcon = Color(0xFFC0584E)
val catEntertainBg = Color(0x1A4F777E)
val catEntertainIcon = Color(0xFF2C5961)
val catHealthBg = Color(0x1A1F6E54)
val catHealthIcon = Color(0xFF135841)

// Typography Text Colors
val textPrimary = ZadNeutralDark
val textSecondary = ZadNeutralMuted
val textTertiary = Color(0xFF9EA197)

// Semantic Colors
val successColor = Color(0xFF238652)
val dangerColor = ZadTerracottaRust
val warningColor = ZadMustardOchre
val infoColor = Color(0xFF2B6CB0)

// Kids Mode Colors
val kidsPrimary = Color(0xFF6B46C1)
val kidsPrimaryDark = Color(0xFF442B82)
val kidsPrimaryLight = Color(0xFFB794F4)
val kidsAccentPink = Color(0xFFED64A6)
val kidsBackground = Color(0xFF0F0A2E)
val kidsSurface = Color(0xFF1E0A4A)

// Error Colors
val error = ZadTerracottaRust
val onError = Color(0xFFFFFFFF)
val errorContainer = ZadTerracottaContainer
val onErrorContainer = ZadTerracottaDark
