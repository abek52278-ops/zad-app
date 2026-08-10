-- Task 22 — habit chips. One SQL function, no graph/embeddings (see EPIC_1_4.md's
-- rejected-proposals section — a GROUP BY over zad_transactions gets the same
-- "قهوة ٢٥ on Saturday morning" quick-action chip a knowledge graph would).
--
-- The stddev filter is what makes this safe to surface without a human reviewing it:
-- a chip only appears when the amount is actually consistent ("قهوة ٢٥" qualifies,
-- "سوبرماركت" — wildly variable — does not). p_same_weekday narrows the 60-day window
-- to just today's weekday, for a "you usually do this on Saturdays" variant.
-- EPIC_1_4.md's spec groups by `merchant_name` — that column does not exist on
-- `zad_transactions` (confirmed live: only id/user_id/amount/title/category/is_expense/
-- created_at/wallet/txn_kind/transfer_to). `title` is the closest real free-text column
-- (it's what SaBankParser/manual-add both populate with the merchant or purchase label).
create or replace function public.zad_habit_chips(p_user uuid, p_same_weekday boolean default false)
returns table(label text, amount numeric, category text, hits int)
language sql stable set search_path = public as $$
  select
    coalesce(nullif(trim(title), ''), category) as label,
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
     and coalesce(stddev_samp(amount), 0) < avg(amount) * 0.25
  order by count(*) desc
  limit 6;
$$;

-- Consolidated from the former duplicate timestamp migration
-- 20260726110000_sent_budget_alerts_rls.sql. Keeping both operations under one
-- version gives fresh environments the complete schema while preserving the
-- already-normalized production history entry for this timestamp.
alter table public.sent_budget_alerts enable row level security;

create policy "user_own_sent_budget_alerts" on public.sent_budget_alerts
  for all using (auth.uid() = user_id);
