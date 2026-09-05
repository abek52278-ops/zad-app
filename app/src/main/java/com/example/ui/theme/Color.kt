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
// Tokens Material has no slot for (success/info/textTertiary/coral/lilac/
// canvas*/surfaceContainer*) are theme-aware too, but via
// LocalZadExtendedColors — see ZadExtendedColors.kt.
//
// Deliberately left static: Kids Mode (CLAUDE.md — don't normalise it toward
// the adult palette), the category pastels, primaryFixed, and eight tokens
// with zero call sites outside this file (sand/sandLight/sandDark/onSand/
// carrotOrange/lilacLight/surfaceContainerLowest/surfaceContainerHighest),
// which are dead rather than deliberate and should be deleted once someone
// confirms nothing external expects them.
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

// No ColorScheme slot — carried by LocalZadExtendedColors instead (ZadExtendedColors.kt).
val primaryDark: Color @Composable get() = LocalZadExtendedColors.current.primaryDark
val primaryLight: Color @Composable get() = LocalZadExtendedColors.current.primaryLight
val secondaryDark: Color @Composable get() = LocalZadExtendedColors.current.secondaryDark
val secondaryLight: Color @Composable get() = LocalZadExtendedColors.current.secondaryLight

// Theme-invariant on purpose: `fixed` is Material's term for a colour that must not
// move between light and dark. Used by ZadKnowledgeMapScreen, whose palette is
// deliberately dark in both themes. See the note in ZadExtendedColors.kt about
// HomeScreen's four uses, which are the reason this needs a product decision.
val primaryFixed = ZadForestEmerald

// Surface Container Levels
val surfaceContainerLowest = Color(0xFFFFFFFF)
val surfaceContainerLow: Color @Composable get() = LocalZadExtendedColors.current.surfaceContainerLow
val surfaceContainer: Color @Composable get() = LocalZadExtendedColors.current.surfaceContainer
val surfaceContainerHigh: Color @Composable get() = LocalZadExtendedColors.current.surfaceContainerHigh
val surfaceContainerHighest = Color(0xFFE2E5DC)

// Neutral & Accent Tokens
val sand = ZadIosSurfaceVariant
val sandLight = ZadIosBackground
val sandDark = ZadIosOutline
val onSand = ZadNeutralDark

val coral: Color @Composable get() = LocalZadExtendedColors.current.coral
val coralLight: Color @Composable get() = LocalZadExtendedColors.current.coralLight
val lilac: Color @Composable get() = LocalZadExtendedColors.current.lilac
val lilacLight = Color(0xFFF2EFF7)
val carrotOrange = Color(0xFFF06A35)

val canvasTop: Color @Composable get() = LocalZadExtendedColors.current.canvasTop
val canvasMid: Color @Composable get() = LocalZadExtendedColors.current.canvasMid
val canvasBottom: Color @Composable get() = LocalZadExtendedColors.current.canvasBottom

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
val textTertiary: Color @Composable get() = LocalZadExtendedColors.current.textTertiary

// Semantic Colors
val successColor: Color @Composable get() = LocalZadExtendedColors.current.success
val dangerColor: Color @Composable get() = MaterialTheme.colorScheme.error
val warningColor: Color @Composable get() = MaterialTheme.colorScheme.secondary
val infoColor: Color @Composable get() = LocalZadExtendedColors.current.info

// Kids Mode Colors
val kidsPrimary = Color(0xFF6B46C1)
val kidsPrimaryDark = Color(0xFF442B82)
val kidsPrimaryLight = Color(0xFFB794F4)
val kidsAccentPink = Color(0xFFED64A6)
val kidsBackground = Color(0xFF0F0A2E)
val kidsSurface = Color(0xFF1E0A4A)

// --- 6. Dark theme palette (static raw values — ZadDarkColorScheme is built from these) ---
// Not a mechanical inversion. The brand triad is dark by design (ZadForestEmerald is
// tone ~20), so using it as `primary` on a dark canvas would be nearly invisible; the
// accents move to their light tones and the "on" colours flip dark to match. Surfaces
// are a near-black with a slight green cast rather than pure black, which is what keeps
// this reading as the same product in the dark — iOS-style elevated greys, not #000.
val ZadEmeraldOnDark = Color(0xFF74C69D)          // primary on dark
val ZadEmeraldContainerOnDark = Color(0xFF1E4534)
val ZadMustardOnDark = ZadMustardLight            // 0xFFE9A844 already reads well on dark
val ZadMustardContainerOnDark = Color(0xFF4A3712)
val ZadTerracottaOnDark = ZadTerracottaLight      // 0xFFEA764B
val ZadTerracottaContainerOnDark = Color(0xFF5A2A16)

val ZadIosBackgroundDark = Color(0xFF10130F)      // canvas
val ZadIosSurfaceDark = Color(0xFF191D17)         // cards
val ZadIosSurfaceVariantDark = Color(0xFF262B24)  // pills, icon backdrops
val ZadIosOutlineDark = Color(0xFF363C33)         // hairlines
val ZadNeutralOnDark = Color(0xFFE9ECE4)          // primary text on dark
val ZadNeutralMutedOnDark = Color(0xFFA6AB9C)     // secondary text on dark
val ZadOnAccentDark = Color(0xFF0B1710)           // text ON the light accents above

// Error Colors
val error: Color @Composable get() = MaterialTheme.colorScheme.error
val onError: Color @Composable get() = MaterialTheme.colorScheme.onError
val errorContainer: Color @Composable get() = MaterialTheme.colorScheme.errorContainer
val onErrorContainer: Color @Composable get() = MaterialTheme.colorScheme.onErrorContainer
