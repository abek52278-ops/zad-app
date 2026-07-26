-- Task 24 (full-app audit) — sent_budget_alerts had RLS off entirely (rule 6: every
-- zad_* table needs RLS scoped to auth.uid() = user_id; this table isn't zad_-prefixed
-- but holds the same per-user data shape and was missed). notify_parents_on_child_spend()
-- writes to it via SECURITY DEFINER (unaffected by RLS), but with RLS off, any
-- authenticated client could read or write arbitrary rows directly via Postgrest —
-- same class of gap as the affiliate_* tables fixed earlier this epic.
alter table public.sent_budget_alerts enable row level security;

create policy "user_own_sent_budget_alerts" on public.sent_budget_alerts
  for all using (auth.uid() = user_id);
