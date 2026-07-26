# ZAD — EPIC 1+4: Cash Ledger, Dedupe Tuning, Egypt SMS, and Consistency Audit

> Place at `docs/agent/EPIC_1_4.md` and commit before starting. Tasks numbered 19–23 to
> continue from `ZAD_MASTER.md`.
>
> Prerequisite: the brain must have completed one successful end-to-end run (STEP 1–4).
> Task 21 depends on the brain asking questions; do not start it before that works.

---

## 0. Rejected proposals and why — read before planning

These were proposed and are deliberately **not** in this epic. Do not implement them, and do
not partially implement them under another name.

### Knowledge graph (`graph_nodes`, `graph_edges`, `vector(1536)`, `get_contextual_predictions`)

**Rejected.** The stated goal is quick-action chips like `قهوة ٢٥` appearing on Saturday
mornings. That is a `GROUP BY` over a table that already exists — see Task 22. Two new tables,
an embedding column, an edge-weight pipeline, and an RPC traversal function to reach the same
output is permanent maintenance cost for zero gain.

Embeddings solve retrieval from thousands of items. This app has dozens, and sends them all.
Semantic search would only earn its place for free-text search across receipts and product
names — a different feature, not memory, not habits.

### Multi-agent split (`zad-chef`, `zad-finance`, `zad-chat` as separate endpoints)

**Rejected as architecture, accepted as context selection.** Three endpoints means three
deployments, three failure surfaces, and three system prompts that drift apart within a month.

What is actually needed: the chef reasoning needs inventory plus expiry dates; the financial
reasoning needs transactions plus history. That is **which slice of the snapshot to include**,
plus a persona paragraph appended to the existing `SYSTEM`. Implement as a `mode` parameter on
the existing function:

```ts
// zad-brain?mode=chef | finance | general
// mode selects: which snapshot sections to build, which tools to expose,
// and which persona paragraph to append. One function, one deployment.
```

### Deferred, not rejected

- **Server-side Arabic speech-to-text** — the only piece here with no adequate free option.
  Telegram voice notes need it; the in-app microphone does not (`SpeechRecognizer` is free
  and on-device). Ship Telegram with **text and buttons only**. Revisit voice later.
- **In-store budget guard** — needs live per-item prices. No price API. Defer.
- **Silent micro-savings** — depends on detecting "a cheaper alternative was chosen," which
  the data cannot establish. Defer until it can.

---

## TASK 19 — Cash wallet and the transfer bug

### 19.1 Audit first, no code

The likely current defect: an ATM withdrawal is recorded as an **expense**. If so, withdrawing
1000 and then spending it produces 2000 of recorded spending, and the budget is wrong by the
full withdrawal every time.

Report, with file and line:

1. Every query that sums spending. What does each include or exclude?
2. How is an ATM withdrawal currently classified by `SaBankParser`? Expense, income, or
   unhandled?
3. Is there any concept of a wallet, account, or payment method on a transaction today?
4. Does the budget card's number come from `ZadFacts` (Task 9) or from its own query?

Stop and report. If withdrawals are being counted as expenses, say so explicitly — it changes
the priority of everything below.

### 19.2 Schema: kind and wallet

```sql
alter table public.zad_transactions
  add column if not exists wallet text not null default 'card'
    check (wallet in ('cash','card','bank')),
  add column if not exists txn_kind text not null default 'expense'
    check (txn_kind in ('expense','income','transfer')),
  add column if not exists transfer_to text
    check (transfer_to is null or transfer_to in ('cash','card','bank'));

-- A transfer must state its destination; nothing else may.
alter table public.zad_transactions
  add constraint transfer_needs_target check (
    (txn_kind = 'transfer' and transfer_to is not null) or
    (txn_kind <> 'transfer' and transfer_to is null)
  );

create index if not exists zad_tx_wallet
  on public.zad_transactions (user_id, txn_kind, wallet, created_at desc);
```

**Backfill carefully.** Existing rows default to `card` / `expense`, which is the current
behaviour — so nothing changes until the parser starts classifying. Then, in a separate step,
reclassify historical ATM withdrawals (identifiable by parser keywords: `سحب`, `ATM`,
`withdrawal`) to `txn_kind = 'transfer'`, `transfer_to = 'cash'`. Report how many rows change
and what the total budget correction is per affected user — this will visibly change their
historical numbers and they need to be told, not surprised.

### 19.3 Spending is not "all transactions"

Every spending sum must become:

```sql
where txn_kind = 'expense'      -- transfers and income never count as spending
```

Cash balance:

```sql
create or replace function public.zad_cash_balance(p_user uuid)
returns numeric language sql stable as $$
  select round(coalesce(sum(
    case
      when txn_kind = 'transfer' and transfer_to = 'cash' then amount
      when txn_kind = 'expense'  and wallet = 'cash'      then -amount
      else 0
    end
  ), 0), 2)
  from public.zad_transactions where user_id = p_user;
$$;
```

Update `ZadFacts` to carry `cashOnHand` from this. Every card reading spending must be
re-verified after this change — this is the highest-risk part of the epic because it changes
numbers users already trust.

### 19.4 The cash card — the whole feature lives or dies here

A card on Home: **"كاش معاك: ٧٠٠"** with a `صرفت منهم` button and habit chips (Task 22).

- It appears when `cashOnHand > 0` and **disappears on its own** when cash reaches zero.
- Its presence *is* the reminder. **No notification may exist whose only purpose is to remind
  the user to log cash.** If the card is not enough, the feature has failed; nagging will not
  rescue it, it will only get all notifications muted — and then the medication alert does not
  arrive either.
- On the first ATM withdrawal, one educational message, once ever: "سحبت ١٠٠٠. دي مش محسوبة
  كمصروف لسه — لما تصرف منها قوللي. ولو نسيت، هسألك آخر الأسبوع."

That last clause is the most important string in this epic. A user who thinks the feature will
hold them to account avoids it. A user told upfront that forgetting is handled uses it freely.

### 19.5 Weekly reconciliation

One question via the brain's existing `ask_user` tool: **"فاضل معاك كام كاش تقريباً؟"** The
answer sets the balance directly by inserting a correcting `transfer` row — do not require an
itemised breakdown.

Full logging gives detail; no logging gives a total; there is no state where the system breaks.
That is what makes it safe to ask rarely instead of insistently.

**Respect refusal absolutely.** If the reconciliation question is dismissed twice, stop asking
permanently, write `"مش بيرد على أسئلة الكاش — اكتفي بالمجموع من السحب"` to `zad_memory`, and
rely on withdrawals alone.

---

## TASK 20 — Dedupe parameters as per-country data

Two current constants are wrong, and hardcoding the corrected values repeats the mistake.

```sql
create table if not exists public.zad_locale_config (
  country              text primary key,
  currency             text not null,
  dedupe_window_hours  int not null default 36,
  amount_tolerance_pct numeric not null default 5,
  updated_at           timestamptz not null default now()
);

insert into public.zad_locale_config (country, currency) values
  ('EG','EGP'), ('SA','SAR'), ('TR','TRY')
on conflict (country) do nothing;
```

- **Window: 30 minutes → 36 hours.** A user says "صرفت النهاردة قهوة وسينما" at night about a
  morning purchase, and bank SMS themselves arrive late. At 30 minutes the announcement never
  matches the card transaction and both are counted.
- **Amount: exact → tolerance.** People round when speaking: "دفعت ١٠٠" against a 97.50 card
  charge. Exact matching guarantees a duplicate.
- Both loaded from config at runtime, keyed by the user's active country. Tuning a market
  becomes a row update, not a release.

Extend `TxDeduplicator` accordingly, keeping its existing test and adding cases at the window
and tolerance boundaries. Note the interaction with Task 6: the CSV importer still needs
database-query matching, because 36 hours does not help rows that are weeks old.

**Do not raise tolerance above 5%.** Two genuinely different small purchases (25 and 26) must
stay separate.

---

## TASK 21 — Egypt SMS tuning, country-keyed

Your own observation is correct: Egyptian tuning will not transfer to Saudi or Turkish formats.
That is precisely why `bank_rules.json` (Task 5) carries a `country` field. Load only the active
country's rules plus a small generic fallback set.

For Egypt specifically:

- Banks: CIB, QNB Alahli, NBE, Banque Misr, AlexBank, Bank of Alexandria, HSBC Egypt.
- Wallets: Vodafone Cash, InstaPay, Fawry — these are how most Egyptian users actually
  transact and are more important than several of the banks.
- Amount formats: thousands separators (`27,226.55`), Arabic-Indic digits (`٠١٢٣٤٥٦٧٨٩`),
  `EGP` / `ج.م` / `جنيه`.
- **Transaction types, in this rejection order:** OTP and declined first (never touch the
  budget), then ATM withdrawal → `txn_kind = 'transfer'`, then salary/deposit → `income`, then
  refund → reversal, then debit → `expense`. Getting this order wrong is how an OTP becomes an
  expense.

Every unparsed bank-looking message goes to the capped `unparsed_messages` table. After a week
of real use, that table tells you exactly which rules to add — far more reliable than guessing
formats from documentation.

---

## TASK 22 — Habit chips: one SQL function, no graph

```sql
create or replace function public.zad_habit_chips(p_user uuid, p_same_weekday boolean default false)
returns table(label text, amount numeric, category text, hits int)
language sql stable as $$
  select
    coalesce(nullif(trim(merchant_name), ''), category) as label,
    round(avg(amount))                                  as amount,
    category,
    count(*)::int                                       as hits
  from public.zad_transactions
  where user_id = p_user
    and txn_kind = 'expense'
    and created_at > now() - interval '60 days'
    and (not p_same_weekday
         or extract(dow from created_at) = extract(dow from now()))
  group by 1, 3
  having count(*) >= 4
     -- only surface a chip when the amount is actually consistent:
     -- "قهوة ٢٥" qualifies, "سوبرماركت" (wildly variable) does not
     and coalesce(stddev_samp(amount), 0) < avg(amount) * 0.25
  order by count(*) desc
  limit 6;
$$;
```

The standard-deviation filter is what makes this work and is why no graph is needed. A chip is
only useful when the amount is predictable; that condition is one line of SQL.

Render as chips on the cash card and in the quick-add sheet. One tap inserts through
`ZadIngest` with `wallet = 'cash'`, `confidence = 1.0` (the user tapped it explicitly).

**These chips will produce most of the actual logging.** Nobody opens a microphone for 25 EGP;
they will tap a button. This is not polish — it is the difference between a feature that gets
used and one that sits in the codebase.

### Free bonus from the same query — habit lifetime cost

`amount × annualised frequency` gives "عادتك دي بتكلفك ٧٣٠٠ في السنة". No new data, no new
model call. Surface it as an insight, capped at one per habit per quarter — it is persuasive
once and nagging thereafter.

---

## TASK 23 — Inventory stagnation

Cheap, and it prevents real waste.

- An item with no quantity decrease in 30 days and no consumption samples → `stagnant`.
- Stagnant items are **blocked** from shopping-list suggestions until stock drops.
- Stagnant items are passed to chef-mode context first: "عندك ٥ علب تونة من الشهر اللي فات —
  نعمل بيها إيه النهاردة؟"
- Items with `expiry_date` within 5 days already reach the brain via the snapshot; make sure
  chef mode prioritises them.

No new tables. A computed status plus a filter on the shopping suggestion query.

---

## TASK 24 — Full-app consistency audit

The request "make sure everything works correctly everywhere" needs to be a checklist, or it
produces a vague reassurance instead of findings.

Produce one table covering **every screen** in the app:

| Screen | Numbers come from | Currency source | LLM call on open? | Notification path | Write path |
|---|---|---|---|---|---|

Fill it by reading code, not by assuming. Then flag every row that violates one of these:

1. Numbers must come from `ZadFacts` or `zad_insights` — never a screen-local query, never a
   literal.
2. Currency must come from `CurrencyFormatter` — zero literals in any Composable.
3. Zero LLM calls on screen open. **Verify by launching with the network disabled**: if any
   screen shows a spinner or an empty state, it is calling out on open.
4. Every notification goes through `ZadAlertRouter`. Any other path is a duplicate system.
5. Every write goes through `ZadIngest` (once Task 12 lands) — list the exceptions now.
6. Every `zad_*` table has RLS scoped to `auth.uid() = user_id`.

Also check specifically, because it was reported broken and never confirmed fixed:

- **Where does the price radar's `groq` call originate?** If it runs from the Android app, the
  API key is inside the APK and extractable, and it is an on-open network call. Both must move
  to an Edge Function with the result cached.
- The three previously-missing imports: confirm the build runs clean **after** the changes, not
  before.

Deliver the table as `docs/agent/AUDIT.md` in the repo, not as a chat message. It is a living
document that gets re-run each epic.

---

## Order

1. **19.1** — audit (no code). If withdrawals are counted as expenses, this reorders everything.
2. **19.2, 19.3** — schema and correct spending sums. Highest risk; changes existing numbers.
3. **20** — dedupe config. Small, and 19.3 makes it meaningful.
4. **21** — Egypt rules. Without these the bank reader does nothing for the primary user.
5. **22** — chips. First visible payoff of the epic.
6. **19.4, 19.5** — cash card and reconciliation. Only after the ledger is correct: showing a
   cash figure before 19.3 lands means showing a wrong number in the middle of the home screen.
7. **23** — stagnation.
8. **24** — audit, and keep it updated.

Telegram (`zad-telegram-bot`, one-time binding code, inline buttons) comes **after** all of the
above. It opens a new channel to user data outside the app, and it should not be built on a
ledger that is still wrong. When it is built: the binding code is the only identity proof —
**a `chat_id` is never an identity**, or anyone who learns a chat id can read and write another
person's finances.
