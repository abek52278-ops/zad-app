package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// =====================================================
// ZAD Design System — Premium Emerald & Gold Identity
// =====================================================

// --- Primary Brand — Deep Emerald ---
val primary = Color(0xFF10B981)            // E500 - Emerald Green
val primaryDark = Color(0xFF047857)        // E700 - Deep Emerald
val primaryLight = Color(0xFF34D399)       // E400 - Light Emerald
val primaryContainer = Color(0xFF052E16)   // E900 - Very Deep Emerald
val primaryFixed = Color(0xFF6EE7B7)       // Fixed primary variant
val onPrimary = Color(0xFFFFFFFF)

// --- Secondary — Premium Gold ---
val secondary = Color(0xFFF59E0B)          // Gold
val secondaryDark = Color(0xFFD97706)      // Dark Gold
val secondaryLight = Color(0xFFFCD34D)     // Light Gold
val onSecondary = Color(0xFFFFFFFF)

// --- Sand — warm accent family, kept for card variants (no longer the canvas) ---
val sand = Color(0xFFF3E5D8)
val sandLight = Color(0xFFF8EFE6)
val sandDark = Color(0xFFE4D2BC)
val onSand = Color(0xFF3D2B1F)

// --- Coral & Lilac — pastel accent blur family (iOS-style ambient blobs) ---
val coral = Color(0xFFFF6F61)
val coralLight = Color(0xFFFFA69E)
val lilac = Color(0xFFC4B5FD)
val lilacLight = Color(0xFFE0D7FF)

// --- Backgrounds & Surfaces (Light Mode) ---
// Pure white canvas — Emerald/Gold/Coral/Lilac supply color only as soft,
// low-alpha blur blobs (see ZadCanvasBackground), never as a flat tinted fill.
val background = Color(0xFFFFFFFF)
val onBackground = Color(0xFF111827)       // Dark text
val surface = Color(0xFFFFFFFF)            // Opaque white for cards
val onSurface = Color(0xFF111827)          // Dark text on surface
val surfaceVariant = Color(0xFFF3F4F6)     // Light gray for elevated cards
val onSurfaceVariant = Color(0xFF4B5563)   // Medium gray text
val outline = Color(0xFFE5E7EB)            // Light border
val outlineVariant = Color(0xFFF3F4F6)     // Lighter border

// Material 3 Surface Containers
val surfaceContainerLowest = Color(0xFFFFFFFF)
val surfaceContainerLow = Color(0xFFFAFAFA)
val surfaceContainer = Color(0xFFF5F5F5)
val surfaceContainerHigh = Color(0xFFE5E7EB)
val surfaceContainerHighest = Color(0xFFD1D5DB)

// Secondary & Tertiary Containers
val secondaryContainer = Color(0xFF2D3748)
val onSecondaryContainer = Color(0xFFE2E8F0)
val tertiary = Color(0xFF3B82F6)
val tertiaryContainer = Color(0xFF1E3A8A)
val onTertiaryContainer = Color(0xFFDBEAFE)
val onPrimaryContainer = Color(0xFFD1FAE5)

// Category Colors
val catBillsBg = Color(0x33EF4444)
val catBillsIcon = Color(0xFFF87171)
val catBankingBg = Color(0x333B82F6)
val catBankingIcon = Color(0xFF60A5FA)
val catFoodBg = Color(0x33F59E0B)
val catFoodIcon = Color(0xFFFBBF24)
val catTransportBg = Color(0x338B5CF6)
val catTransportIcon = Color(0xFFA78BFA)
val catSavingsBg = Color(0x3310B981)
val catSavingsIcon = Color(0xFF34D399)
val catDailyBg = Color(0x33EC4899)
val catDailyIcon = Color(0xFFF472B6)
val catEntertainBg = Color(0x3306B6D4)
val catEntertainIcon = Color(0xFF22D3EE)
val catHealthBg = Color(0x3314B8A6)
val catHealthIcon = Color(0xFF2DD4BF)

// --- Text Colors ---
val textPrimary = Color(0xFF111827)        // Dark gray
val textSecondary = Color(0xFF4B5563)      // Medium gray
val textTertiary = Color(0xFF9CA3AF)       // Light gray

// --- Semantic Colors ---
val successColor = Color(0xFF34D399)       // E400
val dangerColor = Color(0xFFF87171)        // Red
val warningColor = Color(0xFFFBBF24)       // Amber
val infoColor = Color(0xFF60A5FA)          // Blue

// --- Kids Mode Colors ---
val kidsPrimary = Color(0xFF7C3AED)        // Purple
val kidsPrimaryDark = Color(0xFF4C1D95)
val kidsPrimaryLight = Color(0xFFC084FC)
val kidsBackground = Color(0xFF0F0A2E)
val kidsSurface = Color(0xFF1E0A4A)

// --- Error (Material compat) ---
val error = Color(0xFFF87171)
val onError = Color(0xFFFFFFFF)
val errorContainer = Color(0x1AF87171)
val onErrorContainer = Color(0xFFF87171)
