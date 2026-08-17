# Zad App Developer Guidelines

Zad (زاد) is an intelligent family pantry, pharmacy, budget, and task management system composed of an Android Jetpack Compose client, Supabase Deno Edge Functions, and a Telegram Bot interface.

---

## 🏗️ Architecture Overview

1. **Android Client (`app/src/main/java/com/example/`)**:
   - **Framework**: Jetpack Compose, Material 3, Kotlin Coroutines, StateFlow, Navigation Compose.
   - **Design System (`ui/components/`)**:
     - `ZadShell.kt`: `ZadTopHeader`, `ZadBottomBar`, `ZadSegmentedTabs`, `ZadListCard`.
     - `ZadSprings.kt`: Unified spring physics animations and `pressableScale()`.
     - `QrCodeUtils.kt`: QR code generation & bitmap decoding (`ZXing`).
   - **Localization**: All UI text must use `stringResource(R.string.*)` across all 5 supported locales in `res/values*/strings.xml`. Avoid hardcoded Arabic or English text.

2. **Backend & AI Brain (`supabase/functions/`)**:
   - **Runtime**: Deno 2.x (TypeScript).
   - **`zad-brain/`**: Core multi-turn agent with model failover (Gemini primary pool + Groq fallback), fast-path deterministic layer, tool validators, and memory note extraction.
   - **`zad-core-intelligence/`**: Dish query parser, Pexels food image search, and log redaction.
   - **`zad-telegram-bot/`**: Telegram bot webhooks, inline keyboard actions, entitlement checks, and conversational agent interface.

3. **Database & Migrations (`supabase/migrations/`)**:
   - PostgreSQL with strict Row Level Security (RLS) and stored procedures (RPCs).

---

## 🧪 Verification & Testing Commands

### Edge Functions (Deno)
Run locally in the dev container:
```bash
# Test zad-brain (160+ unit tests)
cd supabase/functions/zad-brain && deno check index.ts callModel.ts validators.ts shared.ts audit.ts && deno test --allow-all

# Test zad-core-intelligence
cd supabase/functions/zad-core-intelligence && deno check index.ts redact.ts && deno test --allow-all

# Test zad-telegram-bot
cd supabase/functions/zad-telegram-bot && deno test --allow-all
```

### Android Client (Kotlin)
- Full builds (`testDebugUnitTest`, `lintDebug`, `assembleDebug`) run automatically via GitHub Actions CI workflow [`.github/workflows/build-debug-apk.yml`](.github/workflows/build-debug-apk.yml).

---

## 🛡️ Coding & Safety Invariants

1. **Privacy & Redaction**: Never log unredacted base64 image strings, raw tokens, or sensitive health data. Always use `redactForLog()` from `redact.ts`.
2. **Deterministic Fallbacks**: Tool mutations must strictly validate inputs (bounds, non-negative balances, valid 24h dose times).
3. **Deep Links & Ingestion**: Normalize invite codes via `extractInviteCode()` to handle both `zad://` scheme, `https://zad.app/invite?code=...`, and raw text.
