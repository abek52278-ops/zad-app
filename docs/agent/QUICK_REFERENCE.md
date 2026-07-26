# Quick Reference: Family Budget & Alerts System

## One-Liner Overview
Fixed budget corruption. Unified all transaction paths. Scoped budgets by family + user. Real spending dashboard with live parent alerts.

## Commit Map
| Commit | Phase | What |
|--------|-------|------|
| `dfef833` | 0 | Budget corruption fix + user scoping |
| (prior) | 1 | BankTransactionApplier unify |
| (prior) | 2 | family_id + server dedup + RLS |
| (prior) | 3 | Part of dfef833 |
| `50167d3` | 4 | Real dashboard + Realtime |
| `68ed9e8` | 5 | Parent alerts trigger |

## Critical Bug Fixed (Phase 0)
**`zad_users.budget` corruption:** Server trigger was decrementing the ceiling like it's "remaining", not ceiling.
- **Fix:** Drop trigger. Flip `loadBudget()` to push local→server, not pull server→local.
- **Why:** Local SharedPreferences is source of truth.

## Key Files by Concern

### Budget & Spending Tracking
- `BudgetTracker.kt` — Local balance tracking (all keys user-scoped since Phase 3)
- `TxDeduplicator` in `SaBankParser.kt` — 10-min duplicate prevention (user-scoped prefs since Phase 3)
- `CurrentUser.kt` — **NEW**, user ID cache (no SupabaseRepo dependency)

### Family Dashboard
- `FamilyViewModel.kt` — `ChildSpending` state + `loadChildrenSpending()` + `startRealtimeFamilySpending()`
- `RealtimeFamilySpendingRepo.kt` — **NEW**, Realtime subscription to family transactions
- `FamilyScreen.kt` → `KidsSpendingTab` — Renders real monthly spend bar per child

### Server (Supabase)
- `20260725140000_family_transaction_scoping.sql` — `family_id` column + dedup trigger + RLS read policy
- `20260726000000_family_admin_read_child_budget.sql` — Admin can read child's budget ceiling
- `20260726100000_parent_alerts_child_spending.sql` — **NEW**, alerts trigger fires on threshold cross

## API Additions (SupabaseRepo)
```kotlin
// Get all family's transactions (RLS-filtered by role)
suspend fun getFamilyMemberTransactions(familyId: String): List<ZadTransaction>

// Batch fetch budget ceilings
suspend fun getUsersBudgets(userIds: List<String>): Map<String, Double>
```

## Data Models (FamilyViewModel)
```kotlin
data class ChildSpending(
    val monthlyTotal: Double,
    val budgetCeiling: Double,
    val categoryBreakdown: Map<String, Double>
)
```

## Design Decisions & Trade-offs

| Decision | Why | Trade-off |
|----------|-----|-----------|
| Local budget = source of truth | Corruption bug proved server unreliable | Multi-device sync needs manual cache clear |
| CurrentUser cache (no SupabaseRepo) | Avoid Auth init crash in tests | Must sync CurrentUser in MainActivity + AuthViewModel |
| Server-only v1 dedup | Fast, correct at DB layer | Each phone's BudgetTracker may deduct twice on shared device |
| Same thresholds (75/90/100%) | Simpler UX, parents control via budget ceiling | Not flexible per-child per-category |
| Realtime admin-only | Parents need live updates, children don't | Non-admin sees stale data until refresh |

## Testing
```bash
# Run all unit tests
./gradlew --no-daemon :app:testDebugUnitTest
# → BUILD SUCCESSFUL, 69 tests pass

# Build debug APK
./gradlew --no-daemon :app:assembleDebug
# → BUILD SUCCESSFUL, ready for device/emulator
```

## Deployment Status
- ✅ All phases built & tested
- ✅ Both new migrations applied to live Supabase
- ✅ Code pushed to GitHub
- ✅ Ready for production

## Rollback (if needed)
- Budget corruption reappears → revert `loadBudget()` to pull mode (temp fix)
- Dashboard breaks → DROP POLICY `family_admin_read_child_budget` (read-only, safe)
- Alerts break → DROP TRIGGER `trigger_notify_parents_on_child_spend` (safe)
- Dedup breaks → DROP TRIGGER `trigger_populate_and_dedupe_family_transaction` (scoped to admins, unlikely to break)

No data loss from any migration.

## Future Work
- Phase 6: Cross-device budget sync
- Phase 7: Child purchase requests with approval flow
- Phase 8: Server-side monthly budget reset
- Phase 9: Spending forecasts + end-of-month warnings
- Phase 10: Category-level child budgets

## Architecture Snapshot
```
Budget: SharedPreferences (local) ←push→ Supabase (backup)
Dedup: TxDeduplicator (10min, device-local) + Server trigger (5min, family-joint-card)
Dashboard: FamilyViewModel.ChildSpending ←realtime← zad_transactions INSERT
Alerts: notify_parents_on_child_spend() trigger ← zad_transactions INSERT
```

## Gotchas
- CurrentUser must be synced after auth success (or CurrentUser.get() returns null)
- BudgetTracker uses legacy key fallback, so old unscoped keys still readable but not written
- RLS policies OR together (multiple policies = additive read access, not restrictive)
- Threshold alerts fire on first transaction crossing the threshold each month (sent_budget_alerts idempotency)
- Server dedup scoped to `role='admin'` only — children never deduplicated

---

**Last updated:** 2026-07-26  
**Session:** Family Dashboard & Parent Alerts Implementation  
**Status:** Production-ready
