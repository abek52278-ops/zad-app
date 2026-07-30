-- Task 25 (PRODUCT_PLAN.md) — salary cycle replaces calendar month as the budgeting boundary.
-- cycle_start_day is nullable on purpose: null means "not known yet", and every consumer
-- (client BudgetMath/CycleMath, zad-brain buildSnapshot) must fall back to a calendar month
-- when it's null — never default it to 1, that would assert a fact about every existing user.
alter table public.zad_users
  add column if not exists cycle_start_day int
    check (cycle_start_day between 1 and 31),
  add column if not exists cycle_anchor text not null default 'day_of_month'
    check (cycle_anchor in ('day_of_month', 'last_working_day'));

comment on column public.zad_users.cycle_start_day is
  'Day of month the salary cycle starts (1-31), detected from income transaction clustering and confirmed once via zad-brain''s ask_user. Null = calendar month fallback.';
comment on column public.zad_users.cycle_anchor is
  'day_of_month: cycle starts exactly on cycle_start_day. last_working_day: shifts payday off weekends to the last working day at/before cycle_start_day.';
