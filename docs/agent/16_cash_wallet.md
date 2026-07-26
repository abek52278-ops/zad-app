# Task 16 — Cash Wallet: Passive Tracking UX

**Status**: Specification written. Blocked on Task 15 (dedup fix) which is now complete (commit f5139a5). The dedup fix is a prerequisite because it ensures ATM withdrawals are correctly recognized and aren't silently dropped as duplicates.

## Problem

Users carry cash but have no place to track it in Zad. Without a cash balance, the ledger is incomplete, and the brain can't answer questions like "why was my spending so low last week?" (they spent more in cash than recorded). The app needs a lightweight, friction-free way to log cash transactions.

## Philosophy: "العقل يسأل، والعميل يرد"

Brain asks (via existing in-app home_card mechanism), user responds with minimal friction. No system notifications; all delivery stays on the in-app surface. Delivery uses the existing `ZadInsight`/`ZadQuestionCard` infrastructure (Task 9-11), not a new channel.

## Architecture

### Data Model (Supabase)

**New migration**: `supabase/migrations/20260726021200_cash_wallet.sql` (see plan file for full SQL)

Three tables:
1. **`zad_cash_wallet`** (one row per user) — persistent wallet state
   - `user_id` (PK, FK auth.users)
   - `balance` (numeric) — running total; debits allowed to go negative (reconciliation fixes drift)
   - `teaching_shown` (bool) — ATM withdrawal teaching moment delivered?
   - `last_reconcile_prompt_at` (timestamptz) — when did user last see the weekly question?
   - `decline_count` (int) — how many times has user dismissed the weekly question?
   - `suppress_cash_prompts` (bool) — set to true after 2 declines; permanent opt-out of weekly question only
   - RLS: `for all using (auth.uid() = user_id)`

2. **`zad_habits`** (v1: user/system-seeded list, not auto-learned)
   - `id`, `user_id`, `label`, `typical_amount`, `sort_order`, `created_at`
   - RLS: `for all using (auth.uid() = user_id)`
   - Index on `(user_id, sort_order)`

3. **Postgres functions** (security invoker, RLS-gated):
   - `zad_cash_wallet_credit(p_user, p_amount)` → `(new_balance, show_teaching)` — ATM withdrawal
   - `zad_cash_wallet_debit(p_user, p_amount)` → `new_balance` — cash spent
   - `zad_cash_wallet_decline(p_user)` → `suppressed_now` — weekly question declined

### Kotlin Models

Two `@Serializable` data classes in `app/src/main/java/com/example/data/Models.kt`:
- `ZadCashWallet(userId, balance, teachingShown, lastReconcilePromptAt, declineCount, suppressCashPrompts)`
- `ZadHabit(id, userId, label, typicalAmount, sortOrder)`

### Repository Layer (`SupabaseRepo.kt`)

Eight new methods:
- `getCashWallet(userId)` → `ZadCashWallet?`
- `creditCashWallet(userId, amount)` → `Pair<Double, Boolean>` (new_balance, show_teaching)
- `debitCashWallet(userId, amount)` → `Double` (new_balance)
- `declineCashPrompt(userId)` → `Boolean` (suppressed_now)
- `setCashBalance(userId, amount)` — reconciliation answer
- `markCashReconcilePrompted(userId)` — record timestamp
- `getHabits(userId)` → `List<ZadHabit>`
- `addHabit(userId, label, amount)` — create new quick-log chip
- `upsertInsight(insight)` — client-side equivalent of brain's `emit_insight`, used for teaching + weekly question

All use the existing `try/catch + Log.e` pattern. RPC calls via `client.postgrest.rpc()` for the three functions.

### UI Layer — CashWalletCard Composable

**File**: `app/src/main/java/com/example/ui/components/CashWalletCard.kt` (stateless)

Signature:
```kotlin
@Composable
fun CashWalletCard(
    wallet: ZadCashWallet?,
    habits: List<ZadHabit>,
    onSpendClick: () -> Unit,
    onHabitTap: (ZadHabit) -> Unit,
    onAddHabit: () -> Unit
)
```

Layout (per CLAUDE.md mobile UI rules: 4/8dp spacing, existing theme colors/typography):
- Header: icon + "الكاش عندك" title + balance (red if negative)
- Habits row: `LazyRow` of `AssistChip`s (one per habit + "+" add chip)
- "صرفت منه" button (40.dp height, 44dp tap target)

Roborazzi test in `PreviewTest.kt` with sample wallet + habits.

### Hooks

**Hook 1: ATM withdrawal teaching** (`BankTransactionApplier.kt`)
```kotlin
if (txType == TxType.WITHDRAWAL) {
    val (_, showTeaching) = SupabaseRepo.creditCashWallet(userId, amount)
    if (showTeaching) {
        SupabaseRepo.upsertInsight(ZadInsight(
            kind = "insight", surface = "home_card",
            title = "كاش في إيدك",
            body = "سحبت كاش من الصراف — زاد بدأ يتابعه في كارت الكاش الجديد تحت",
            dedupeKey = "cash_teaching_v1"
        ))
    }
}
```
One-time per user (function returns `show_teaching=true` only once); scoped to regex/BankRulesEngine path (AI fallback gap noted but deferred).

**Hook 2: Weekly reconciliation** (`ZadViewModel`)
```kotlin
fun checkCashReconcilePrompt() {
    // Run from LaunchedEffect(Unit) alongside loadZadInsights()
    // Gate: suppress_cash_prompts, last_reconcile_prompt_at >= 7 days old
    // Emit: ZadInsight with per-ISO-week dedupe_key ("cash_reconcile_2026-W31")
    // actionType = "number", title = "فاضل كام كاش؟"
    // Answer: setCashBalance (direct set, no itemization), mark "acted"
    // Decline: declineCashPrompt (increments counter, auto-suppress at 2)
}
```

**Hook 3: Persistent Cash card** (`HomeScreen.kt`)
- Add `CashWalletCard` to the existing card row layout, after `ZadFacts`
- Wire `ZadViewModel` `cashWallet`/`habits` `StateFlow`s
- `onSpendClick` → opens minimal amount-entry (or quick-log hardcoded chips), then `debitCashWallet` + writes `zad_transactions` (source_type="cash_wallet") so it shows in totals
- `onHabitTap` → immediately `debitCashWallet(amount)` + same transaction write

## Implementation Order

1. Supabase migration (+ `mcp__supabase__apply_migration` to dev/staging branch first, confirm no RLS-missing warnings)
2. Models (`ZadCashWallet`, `ZadHabit`) → `Models.kt`
3. `SupabaseRepo` methods (8 total)
4. `CashWalletCard` composable + Roborazzi test
5. `BankTransactionApplier` Hook 1 (ATM teaching)
6. `ZadViewModel` Hook 2 (weekly reconciliation) + special-case render in `HomeScreen`
7. `HomeScreen` Hook 3 (persistent card + habits)
8. Full build + test

## Verification

- **Unit tests**: cash wallet RPC mocking (low priority; methods are thin pass-throughs)
- **Roborazzi**: `captureCashWalletCard` (per PreviewTest pattern, already outlined)
- **Build gate**: `./gradlew --no-daemon :app:testDebugUnitTest` (73 + 1 new = 74 tests)
- **Manual flow** (requires a running Supabase project):
  - User gets ATM withdrawal notification → teaching insight appears once
  - Weekly on day 7: "فاضل كام كاش؟" appears
  - User answers, balance updates
  - User dismisses twice, question stops appearing; card/habits remain active

## Known Limitations

- **No auto-learning** of habits — v1 is user/system-seeded manual list. Auto-learn from repeated manual entries is a documented future task.
- **AI fallback ATM withdrawal** (`ZadAiRepository.analyzeBankNotification`) doesn't return `txType` today, so teaching doesn't fire for those. Documented as a gap; fixing requires the AI fallback to return typed output.
- **No budget-override for negative balance** — reconciliation can set balance to 0 or any user-entered value, even if it was negative. This is intentional (minimize friction), but known if auditing is needed later.

## Blocked By

Task 15 (dedup fix) — ensures ATM withdrawals aren't silently dropped before `creditCashWallet` can even be called.

## Unblocks

Future Telegram bot integration (channel-agnostic; cash balance is stored server-side); future knowledge graph (wallet balance as a graph node).
