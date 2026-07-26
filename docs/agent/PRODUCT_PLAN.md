# ZAD — Product Plan (synthesis)

> Place at `docs/agent/PRODUCT_PLAN.md`. This is the *why* and the *order*. The *how* lives in
> `ZAD_MASTER.md`, `EPIC_1_4.md`, `15_family_alerts.md`, `16_validation_and_recovery.md`,
> `17_2_pharmacy_fix.md`.
>
> Tasks 25–28 below are new and are specified here in full.

---

## 1. What the diagnosis actually was

Reviewing every complaint raised about this app, not one of them was caused by the model being
insufficiently capable. Every single one traced to one of two things:

**Sensing** — data never entering the system:
- No Egyptian bank rules in the parser, while the primary user is in Egypt
- `RECEIVE_SMS` is a restricted Play permission the app cannot legitimately hold
- ATM withdrawals classified as expenses, so cash spending is counted twice
- Cash has no representation at all
- No manual way to log a medication dose, so the count freezes permanently
- Camera and voice capture existing but landing in inconsistent places

**Honesty** — the system asserting things it cannot know:
- `days_left` for medication ignoring `dosage`, wrong by a factor for any multi-unit dose
- Cards rendering data with no source behind them
- Alerts phrased as fact ("لم يأخذ العلاج") when the app only knows a log is missing
- Precise-looking figures derived from unconfirmed inputs

**The governing rule for everything that follows: invest in sensing and correctness, not in
model capability.** A cheaper model with correct inputs beats a stronger model reasoning over
wrong numbers, every time.

---

## 2. Two numbers that are wrong for almost every user

Neither was raised anywhere in the design so far. Both are cheap to fix and both change the
meaning of every financial figure in the app.

### 2.1 The month does not start on the 1st

Every calculation — `days_left_in_month`, `velocity`, `daily_allowance_left`, the whole threat
model — assumes a calendar month. Households in this market live on the **salary cycle**: the
25th, the 28th, the last working day.

For a user paid on the 28th, on the 3rd of the month the app says "27 days left, you're doing
fine" when they actually have 25 days of money left and already spent five days' worth. Every
warning fires at the wrong time, and `warning_accuracy` will keep scoring `false_alarm` without
revealing why.

**This is why fixing it matters more than it looks: it is silently corrupting the brain's own
self-assessment.**

### 2.2 "Remaining" is a misleading number; "available" is the real one

`remaining = budget - spent` ignores everything already committed. A user with 420 remaining and
rent of 300 due in four days does not have 420. They have 120.

Two numbers, clearly separated:

- **متبقي** — what the current arithmetic gives
- **متاح** — remaining minus obligations falling due before the next income date

The second is the number the user actually needs and the one every alert should be based on. It
is also the number that makes the app feel like it understands their life rather than counting
their receipts.

---

## 3. Order of work

Each phase must be finished before the next. Within a phase, order matters less.

### Phase A — Correctness. Nothing else matters until these are true.

| # | Task | Where specified |
|---|---|---|
| A1 | ATM withdrawal / transfer bug; wallets; spending sums | `EPIC_1_4.md` 19.1–19.3 |
| A2 | **Salary cycle replaces calendar month** | Task 25 below |
| A3 | **Committed obligations → "available" number** | Task 26 below |
| A4 | Pharmacy: dosage units + manual dose logging | `17_2_pharmacy_fix.md` |
| A5 | Egyptian bank rules + wallets (Vodafone Cash, InstaPay, Fawry) | `EPIC_1_4.md` 21 |
| A6 | Drop `RECEIVE_SMS`; rely on `NotificationListenerService` | see §5 |

### Phase B — Effortless capture. The user will not type; design for that.

| # | Task | Where |
|---|---|---|
| B1 | Habit chips via SQL | `EPIC_1_4.md` 22 |
| B2 | Cash card on Home, disappears at zero | `EPIC_1_4.md` 19.4 |
| B3 | Weekly cash reconciliation question | `EPIC_1_4.md` 19.5 |
| B4 | Telegram bot — **text and buttons only**, no voice | after Phase C |

### Phase C — Trust. This is what converts a working app into one that gets believed.

| # | Task | Where |
|---|---|---|
| C1 | **Confidence-visible numbers (`≈`)** | Task 27 below |
| C2 | **"Why did this number change?" trail** | Task 27 below |
| C3 | **Informative dismissal** | Task 28 below |

### Phase D — Learning

| # | Task | Where |
|---|---|---|
| D1 | Question loop → `zad_memory` (STEP 3 must pass) | `ZAD_MASTER.md` 11 |
| D2 | Self-review of past warnings | already in brain v3 |
| D3 | Inventory stagnation | `EPIC_1_4.md` 23 |
| D4 | Family alerts with consent and escalation | `15_family_alerts.md` |

---

## TASK 25 — Salary cycle instead of calendar month

```sql
alter table public.zad_users
  add column if not exists cycle_start_day int
    check (cycle_start_day between 1 and 31),
  add column if not exists cycle_anchor text not null default 'day_of_month'
    check (cycle_anchor in ('day_of_month','last_working_day'));
```

`cycle_start_day` is **nullable**. Null means "not known yet" and the app falls back to calendar
months — do not default it to 1 and thereby assert a fact about every existing user.

### Detecting it rather than asking

Most users will not configure this. Detect it and confirm:

- Find income transactions (`txn_kind = 'income'`) over the last 4 months.
- If they cluster on a day of month within ±3 days, that is the cycle start.
- Have the brain confirm it once via `ask_user`: "راتبك بيجي حوالي يوم ٢٨ من كل شهر — أظبط
  الشهر عندك على كده؟" One question, and the answer settles it permanently.
- `last_working_day` handles the common case where payday shifts off weekends.

### The maths that must change

Every one of these currently uses calendar boundaries and must use the cycle:

```
cycleStart, cycleEnd, daysElapsed, daysLeft
spent            = expenses within the current cycle
velocity         = spent / (budget * daysElapsed / cycleLength)
dailyAllowance   = available / daysLeft
```

Also change in the brain's `buildSnapshot`, and in `warningAccuracy` — historical warnings must
be evaluated against the cycle in force at the time, not the calendar month, or the accuracy
figure stays meaningless.

**Report the impact:** for three real users, print the old and new values of `daysLeft` and
`velocity`. If they differ materially, every historical warning was mistimed and that is worth
knowing explicitly.

---

## TASK 26 — Committed obligations and the "available" number

```sql
create table if not exists public.zad_obligations (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null references auth.users(id) on delete cascade,
  title        text not null,
  amount       numeric not null check (amount > 0),
  kind         text not null check (kind in
                 ('rent','installment','debt','tuition','utility','other')),
  due_day      int check (due_day between 1 and 31),
  due_date     date,                      -- for one-off obligations
  recurrence   text not null default 'monthly'
               check (recurrence in ('monthly','quarterly','yearly','once')),
  auto_detected boolean not null default false,
  confirmed    boolean not null default false,
  active       boolean not null default true,
  created_at   timestamptz not null default now()
);
```

Subscriptions already exist in `zad_subscriptions`; **do not duplicate them here**. The
"available" calculation reads both tables.

### The calculation

```kotlin
// Obligations falling due between now and the next income date
val committed = obligations
    .filter { it.active && it.confirmed && it.nextDueDate <= cycleEnd }
    .sumOf { it.amount } +
  subscriptions.filter { it.nextChargeDate <= cycleEnd }.sumOf { it.amount }

val available = remaining - committed
```

`available` may be negative. **Show it negative.** A user who is 180 short before rent needs to
know now, not on the due date. Hiding it behind a floor of zero is the single most harmful thing
this feature could do.

### Auto-detection

Recurring same-amount transactions to the same merchant, three months running, are almost
certainly an obligation. Detect them, insert with `auto_detected = true, confirmed = false`, and
have the brain confirm once: "بشوف ٣٥٠٠ بيتدفعوا كل شهر يوم ٥ — ده إيجار؟" Nothing enters the
`available` calculation until confirmed — an unconfirmed guess must never silently reduce a
user's spending power.

### Display

The budget card shows both, with `available` as the primary figure:

```
متاح: ١٢٠
متبقي ٤٢٠ · محجوز ٣٠٠ (إيجار بعد ٤ أيام)
```

And every budget-related insight from the brain must reason on `available`. Add it to the
snapshot with the obligation breakdown, and update the `SYSTEM` rule: warnings cite `available`,
not `remaining`.

---

## TASK 27 — Visible confidence and a "why" trail

The app has been wrong often enough that accuracy claims will not restore trust. Transparency
will.

### 27.1 One glyph for uncertainty

Any figure derived from unconfirmed data renders with a leading `≈` and is tappable:

- A transaction with `is_verified = false` in the sum → `≈`
- Medication days-left with `units_per_dose = null` → not a number at all, "محتاجة تأكيد"
- Stock days-left with `samples < 3` → `≈`
- Cash balance not reconciled in over 14 days → `≈`

Tapping explains in one line what is uncertain and offers the fix. This converts every gap from
a bug the user discovers into information the app volunteered — which is the entire difference
in how the app feels.

Implement as a field on `ZadFacts`, not per-screen logic:

```kotlin
data class Figure(val value: Double, val confident: Boolean, val reason: String? = null)
```

### 27.2 Why did this number change?

`zad_brain_runs.mutations` already records every brain-made change with old and new values, and
every insight has a `dedupe_key`. Surface it:

- Long-press any number → a sheet listing what changed it, when, and why.
- Entries come from `mutations`, from `ZadIngest` writes with their `source`, and from user
  edits.
- Cover the obvious question directly: "الميزانية نقصت ٢٥٠ — رسالة من CIB الساعة ٣:١٢، تاجر
  [X]. غلط؟ [تصحيح]"

An agent that changes numbers without showing its work will not be trusted no matter how correct
it is. This is the mechanism that makes autonomous editing acceptable.

---

## TASK 28 — Informative dismissal

Dismissal currently only suppresses. The *reason* is far more valuable than the fact.

Replace the dismiss action with three taps:

| Tap | Meaning | Effect |
|---|---|---|
| مش مهم | Relevance | suppress this `dedupe_key`; memory note about the category |
| الرقم غلط | Correctness | suppress **and** flag the underlying data for review |
| عرفت خلاص | Timing | suppress this instance only; the same key may recur later |

Each writes a `zad_memory` note with the reason, and each appears in the snapshot's
`already_sent` with its reason attached so the brain can distinguish "he doesn't care about
subscriptions" from "my subscription figures are wrong."

**"الرقم غلط" is the important one.** It is a free bug report from the person best placed to
notice, and right now that signal is being thrown away.

---

## 4. Product decisions worth stating so they don't drift back

**Chat is not the main interface.** This started as "one text field like Gemini." That was the
wrong shape and the design has correctly moved away from it. Cards and chips are the primary
surface; chat is the fallback for input that doesn't fit a button. Do not rebuild the app around
a chat box.

**The brain asks; the user taps.** Any design requiring the user to acquire a new habit will
fail with most people. Every capture path must work when the user says nothing at all — chips
for one tap, weekly reconciliation for the total, withdrawal detection for the baseline.

**Delete rather than fake.** Any card without a real data source gets removed. This has already
been applied and must stay applied.

**No notification whose only purpose is a reminder to log something.** Presence of a card is the
reminder. Nagging gets all notifications muted, and then the medication alert does not arrive.

---

## 5. Play Store compliance — do not defer this

`RECEIVE_SMS` and `READ_SMS` are restricted permissions requiring the app to be the default SMS
handler, and "notification enhancement and alerts" is explicitly listed as a non-permitted use
case. A budgeting app declaring them risks rejection or removal.

The app already has `UnifiedBankListener` (`NotificationListenerService`), and bank SMS surface
as notifications from the messaging app — so the same data is available without the restricted
permission.

**Action:** remove `UnifiedSmsReceiver` and `SmsBackfillScanner`, drop the SMS permissions from
the manifest, and route everything through the notification listener. Verify no regression in
parsing coverage first, on a real device.

Separately: Egypt's data protection regime has a compliance deadline of **1 November 2026**,
requires authorisation for most processing, and requires a licence plus explicit consent for
cross-border transfer — which includes Supabase and any model API hosted abroad. Not a
deferrable item, and not something to resolve from a chat conversation; it needs an Egyptian
lawyer.

---

## 6. Explicitly not building

- Knowledge graph, `graph_nodes`/`graph_edges`, embeddings for memory — the habit-chip outcome
  is one SQL query (`EPIC_1_4.md` 22)
- Separate agent endpoints per persona — one function, a `mode` parameter
- LangGraph, CrewAI, AutoGen, Mem0, LiteLLM, Ollama — Python frameworks with no server to run
  them on, solving problems this app does not have
- Real supermarket pricing, in-store budget guard — no price API exists
- Silent micro-savings — depends on inferring a choice the data cannot show
- Server-side Arabic speech-to-text — the only piece with no adequate free option; Telegram
  ships text-and-buttons first
- Financial challenges, savings pots — new features, not fixes, and the fixes are not done

---

## 7. How to know it worked

Not "does it build." These:

1. A user who logs nothing manually still sees a correct budget, because withdrawals, bank
   notifications, and the weekly reconciliation cover it.
2. `متاح` on the home card is the number the user would arrive at with a pen and paper.
3. Days-left figures are verifiable by hand for medication, stock, and budget.
4. A healthy household produces zero insights on the daily run.
5. Every number on screen can be traced to its source in two taps.
6. Launching with the network off renders every screen fully.
7. `warning_accuracy` shows the brain's false-alarm rate falling over three months.

Number 7 is the real test of whether this is an agent that learns or an app that reports.
