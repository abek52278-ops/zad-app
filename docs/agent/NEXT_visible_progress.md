<!--
INCOMPLETE — the source message hit a 50,000-char limit mid TASK 17.3 "Acceptance"
list (cuts off after item 5, "No pharmacy notification..."). Whatever came after
item 5 in TASK 17.3, plus any TASK 18+ that may have followed, is missing. Asked
the user to resend the tail. Do not treat this file as complete until that lands
and this comment is removed.
-->

# ZAD — NEXT: Visible Progress Reorder

> Supersedes the task order in `ZAD_MASTER.md`. The task **contents** are unchanged; only
> the sequence is.
>
> Reason: tasks 1–8 were all foundation and touched zero screens. The user has had no
> visible change in the app for eight tasks. That ordering optimised for risk and paid for
> it in morale. Correcting it now.

---

## Order from here

| # | Task | Needs a working API key? | Visible to the user? |
|---|---|---|---|
| 1 | **TASK 9 — ZadFacts** | **No** | **Yes — most of the dead cards** |
| 2 | **TASK 17 — Pharmacy correctness** (new, below) | No | Yes |
| 3 | Set `ZAD_API_KEY` + run `smokeTestTools()` | — | — |
| 4 | **TASK 10 — insights → Home, bell, router** | Yes | Yes |
| 5 | TASK 11 — question loop | Yes | Yes |
| 6 | TASK 14 — profile, avatar, floating buttons, drawer | No | Yes |
| 7 | TASK 12 — ZadIngest consolidation | No | No |
| 8 | TASK 13 — account deletion | No | Barely |

Do 1 and 2 back to back before touching anything that needs a key. Between them they fix
most of what the user originally complained about, and neither depends on the brain running.

---

## TASK 9 — start here, and read this first

`ZadFacts` is pure Kotlin: no network, no LLM, microseconds. The following cards were called
"shells" by the user and **every one of them needs only this** — no AI at all:

- قوة الصرف (spending velocity)
- الصحة المالية (financial health / threat level)
- اختبار الصمود المالي (resilience: how many days remaining covers at current rate)
- الإنفاق الشهري (monthly spending)
- مؤشر الاستهلاك (consumption index)
- كارت البدجت والمتبقي (budget card showing remaining — the user's oldest complaint)
- توزيع المصروفات (category breakdown)

Build the `StateFlow<ZadFacts>` per the Task 9 spec, then rewire those seven cards to read
from it in the **same** commit. A `ZadFacts` that nothing renders is invisible work — the
whole point of doing this task now is that the app looks different when it lands.

Two cards from the user's list must be **deleted**, not wired:
- **رادار الأسعار / أسعار اليوم** — needs a live price API that does not exist. Delete it.
- **رادار التضخم** — same, unless it is recomputed as *the user's own* month-over-month
  price change for repeated purchases, which **is** computable from `zad_transactions`. If
  you can compute that, keep it and relabel it honestly. If not, delete it.

Merge, do not duplicate:
- **توقعات زاد** and **توقيت الشراء الذكي** and **أنماط سلوكية** are three cards showing
  variations of the same derived data. Collapse into one card driven by `ZadFacts` +
  `zad_insights`. The user explicitly asked for widgets to merge rather than each standing
  alone.

Report at the end: for each card in the list above, state wired / merged / deleted, and
which file.

---

## TASK 17 — Pharmacy correctness (new)

This was in the user's very first message — "الصيدلية ما بتنبهش و بتدي أشياء غلط" — and no
task has ever diagnosed it. Two separate faults are being described: it does not alert, and
the numbers it shows are wrong.

### 17.1 Diagnose before changing anything

`zad_pharmacy_items` columns: `name, dosage, remaining_quantity, unit, daily_dose_count,
dose_times, price`.

Report, with file and line:

1. **What schedules the dose reminders?** Is there an `AlarmManager` / `WorkManager` entry
   for `dose_times` at all, or is nothing scheduled? "It doesn't alert" most likely means
   nothing is ever scheduled — check before assuming a notification bug.
2. **What format is `dose_times`?** A JSON array, a comma string, something else? How is it
   parsed, and what happens on a malformed value — silent failure?
3. **Is `remaining_quantity` ever decremented?** When a dose is taken, does anything reduce
   it? If not, "days remaining" is permanently wrong and never changes — a strong candidate
   for "بتدي أشياء غلط".
4. **How is "days left" computed?** `remaining_quantity / daily_dose_count` is only right if
   `remaining_quantity` counts *doses*. If it counts boxes, or millilitres, or tablets while
   a dose is two tablets, the number is wrong by a constant factor. Check what the unit
   actually means and whether `dosage` is being ignored.
5. **Timezone.** Are `dose_times` local or UTC? A dose at 08:00 firing at 06:00 or not at
   all is a timezone bug, and it presents exactly as "the pharmacy doesn't alert."
6. **Is `user_id` present and is RLS scoped to it?** Already confirmed present in Task 1 —
   re-confirm the policy is `auth.uid() = user_id` and not `true`.

Stop and report. Do not fix until the actual fault is identified — a wrong "days left" number
and a missing alarm need different fixes, and guessing wastes a cycle.

### 17.2 Then fix, in this order

1. **Make the number honest.** Whatever the unit confusion is, resolve it so
   "فاضل ٤ أيام" is arithmetically true. If the data is genuinely ambiguous for existing
   rows, show "الكمية محتاجة تأكيد" and let the user set it — do not display a computed
   number you know may be wrong.
2. **Decrement on dose taken.** A "أخدتها" action that reduces `remaining_quantity` by one
   dose. Without this, nothing else in the pharmacy can ever be right.
3. **Schedule the reminders properly.** Exact alarms for `dose_times` in the device's local
   timezone, rescheduled on boot (`BOOT_COMPLETED`) and on timezone change. Verify on a real
   device — this is not testable in CI.
4. **Feed the brain.** `days_left` per medication already goes into the snapshot. Once the
   number is honest, a low-supply alert becomes correct automatically. Do not add a separate
   pharmacy alert system — route through `zad_insights` like everything else.

### 17.3 Acceptance

1. "Days left" is arithmetically verifiable by hand for three real medications.
2. Taking a dose reduces `remaining_quantity` and the days-left figure changes.
3. A dose scheduled 5 minutes out fires on a real device, and still fires after a reboot.
4. A medication with 2 days of supply produces exactly one insight, not one per dose time.
5. No pharmacy notification <!-- TRUNCATED HERE — source message cut off mid-sentence -->
