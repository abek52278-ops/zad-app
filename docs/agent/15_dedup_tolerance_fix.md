# Task 15 — Transaction Deduplication Window and Tolerance Fix

## Problem

`TxDeduplicator` is too strict. It blocks two reports of the same bank transaction if:
1. **Window is 10 minutes** — many banks send both SMS + app notification minutes to hours apart
2. **Match is exact amount** — banks round differently across channels; a 100.5 SAR withdrawal may appear as 100 in SMS and 100.5 or 101 in the app notification

This causes real transactions to be incorrectly flagged as duplicates and dropped, corrupting the transaction ledger.

Example: User withdraws 1000 SAR. SMS arrives at 2:00 PM reporting "1000", app notification arrives at 2:03 PM reporting "1002" (rounding difference). The 10-minute window is still open, but exact-amount match fails → transaction is silently dropped.

## Solution

Change `TxDeduplicator` (`app/src/main/java/com/example/data/SaBankParser.kt`, lines 398-461):

1. **Window**: 10 minutes → **36 hours** — covers the full day-to-day variability of when a bank's SMS and app notification may be delivered
2. **Amount match**: exact string match → **5% relative tolerance** using symmetric formula `abs(a-b)/max(a,b) <= 0.05`
   - Relative (not absolute) so it scales to any transaction size
   - Symmetric so it works regardless of which channel reports first
   - 5% is conservative; legitimate transactions won't vary by more than rounding

## Implementation

### Changes in `SaBankParser.kt`

- **Line 402-403**: `WINDOW_MS = 36 * 60 * 60 * 1000L`, add `AMOUNT_TOLERANCE = 0.05`
- Change `WINDOW_MS` from `private` to `internal` so tests can assert the constant
- **After line 410**: add `isAmountMatch(a: Double, b: Double): Boolean` helper using the symmetric relative-difference formula
- **Line 452-457**: replace exact string match (`it[0] == amountKey`) with `isAmountMatch(storedAmount, amount)`, parsing the stored 2-decimal string back to `Double` first

### Test additions in `TxDeduplicatorTest.kt`

1. `amount within 5 percent tolerance is a duplicate` — e.g., 100.0 then 104.0 → rejected (4% < 5%)
2. `amount beyond 5 percent tolerance is NOT a duplicate` — e.g., 100.0 then 106.0 → accepted (6% > 5%)
3. `tolerance is symmetric` — order-independent, stored amount can be higher or lower
4. `WINDOW_MS is 36 hours` — direct constant assertion

### What is NOT changed

- **Exact match by external ref** (`isDuplicateByRef`, line 451) — unchanged, remains the strongest signal
- **Per-user scoping** (`userScopedPrefsName`, lines 409-410) — already correct since commit `dfef833`, do not touch
- **Server-side family-card dedup** (`populate_and_dedupe_family_transaction` in `supabase/migrations/20260725140000_family_transaction_scoping.sql:59`) — separate mechanism for cross-device joint cards, has its own 5-minute window, not modified

## Known limitation (non-goal, deferred)

Egyptian bank SMS regex accuracy (`SaBankParser.kt` lines 274-289) is explicitly flagged in existing code comments as "best-guess, not verified against real SMS" for CIB, QNB الأهلي, NBE, Banque Misr, AlexBank, HSBC مصر, Vodafone Cash, InstaPay, فورى. Fixing this requires real sample messages from users, which we don't have yet. This task does not attempt to fix bank detection — only to widen the dedup window and tolerance so that once a bank IS correctly detected, we don't incorrectly suppress its second report.

## Verification

Run the extended test suite:
```bash
./gradlew --no-daemon :app:testDebugUnitTest --tests "com.example.data.TxDeduplicatorTest"
./gradlew --no-daemon :app:testDebugUnitTest
```

Confirm all 12 tests pass (8 existing + 4 new).

## Impact

- **Reduced false duplicates**: legitimate same-event notifications now match across a 36-hour window with 5% tolerance
- **Unchanged false-negative rate**: a genuinely different transaction (different merchant) with the same amount still correctly fails the amount+direction+disambiguator check
- **Zero external API changes**: the public `isNewTransaction()` signature is unchanged; callers pass the same parameters
