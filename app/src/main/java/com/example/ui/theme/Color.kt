package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// =========================================================================
// Static palette vs theme-aware aliases — read this before adding a token.
//
// The `Zad*` values below are the raw palette: fixed hex, the source the
// ColorScheme is built from. They never vary by theme, and ZadColorScheme
// (ZadTheme.kt) must be built from *these* — scheme construction is not a
// composable context, so it cannot read the aliases further down.
//
// The lowercase Material-alias tokens are `@Composable get()` instead of
// plain vals, so they resolve through MaterialTheme.colorScheme at use site
// and follow the active theme. That indirection is what lets dark mode reach
// the ~900 call sites that name these tokens directly instead of going
// through MaterialTheme themselves. A token may only be converted if
// ZadColorScheme sets that exact slot to the same value — otherwise it would
// silently fall back to a Material default and change colour.
//
// Deliberately left static: Kids Mode (CLAUDE.md — don't normalise it toward
// the adult palette), the category pastels, and the tokens with no
// ColorScheme slot to read from (successColor/infoColor/textTertiary/
// sand/coral/lilac/canvas*/surfaceContainer*). Those need an extended-colour
// mechanism before they can follow a theme; they are light-only for now.
// =========================================================================

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

// --- 3. Raw values the ColorScheme is built from (never theme-aware) ---
// ZadTheme.kt builds ZadColorScheme outside any composable, so it must use these,
// not the aliases below.
val ZadOnAccent = Color(0xFFFFFFFF)        // onPrimary / onSecondary / onTertiary / onError
val ZadOutlineVariant = Color(0xFFE8EBE2)

// --- 3b. Material 3 aliases — theme-aware, resolve via MaterialTheme.colorScheme ---
val primary: Color @Composable get() = MaterialTheme.colorScheme.primary
val primaryContainer: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val onPrimary: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
val onPrimaryContainer: Color @Composable get() = MaterialTheme.colorScheme.onPrimaryContainer

val secondary: Color @Composable get() = MaterialTheme.colorScheme.secondary
val secondaryContainer: Color @Composable get() = MaterialTheme.colorScheme.secondaryContainer
val onSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSecondary
val onSecondaryContainer: Color @Composable get() = MaterialTheme.colorScheme.onSecondaryContainer

val tertiary: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val tertiaryContainer: Color @Composable get() = MaterialTheme.colorScheme.tertiaryContainer
val onTertiaryContainer: Color @Composable get() = MaterialTheme.colorScheme.onTertiaryContainer

val background: Color @Composable get() = MaterialTheme.colorScheme.background
val onBackground: Color @Composable get() = MaterialTheme.colorScheme.onBackground
val surface: Color @Composable get() = MaterialTheme.colorScheme.surface
val onSurface: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val surfaceVariant: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val onSurfaceVariant: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val outline: Color @Composable get() = MaterialTheme.colorScheme.outline
val outlineVariant: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant

// No ColorScheme slot — static until an extended-colour mechanism exists.
val primaryDark = ZadForestEmeraldDark
val primaryLight = ZadForestEmeraldLight
val primaryFixed = ZadForestEmerald
val secondaryDark = ZadMustardDark
val secondaryLight = ZadMustardLight

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

// Typography Text Colors — same values the scheme carries, so they can follow it.
val textPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val textSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val textTertiary = Color(0xFF9EA197)   // no slot — static

// Semantic Colors
val successColor = Color(0xFF238652)   // no slot — static
val dangerColor: Color @Composable get() = MaterialTheme.colorScheme.error
val warningColor: Color @Composable get() = MaterialTheme.colorScheme.secondary
val infoColor = Color(0xFF2B6CB0)      // no slot — static

// Kids Mode Colors
val kidsPrimary = Color(0xFF6B46C1)
val kidsPrimaryDark = Color(0xFF442B82)
val kidsPrimaryLight = Color(0xFFB794F4)
val kidsAccentPink = Color(0xFFED64A6)
val kidsBackground = Color(0xFF0F0A2E)
val kidsSurface = Color(0xFF1E0A4A)

// Error Colors
val error: Color @Composable get() = MaterialTheme.colorScheme.error
val onError: Color @Composable get() = MaterialTheme.colorScheme.onError
val errorContainer: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
val onErrorContainer: Color @Composable get() = MaterialTheme.colorScheme.onErrorContainer
