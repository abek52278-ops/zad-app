-- Every authenticated account gets its zad_users row, at signup, from the database.
--
-- Why this exists. On 2026-08-15 the live account 20a420a9-…cb1d had transactions, a
-- pharmacy, a shopping list — and **no row in zad_users at all**. Only 3 rows existed in
-- the whole table. The one place the app creates that row is SupabaseRepo.signUp(), and it
-- only runs when two things are both true: the customer typed a display name, and a
-- session already exists by the time signUp() returns (it does not, when email
-- confirmation is on). Miss either and the account is permanently row-less.
--
-- Row-less is not a cosmetic gap, because every write that configures an account is an
-- UPDATE filtered by id:
--
--   * syncMarketProfile()  → 0 rows changed, PostgREST still answers 200, currency/country
--                            stay null forever. All 3 existing rows show currency = null
--                            and country = null even though their owners picked a market.
--                            That is what makes the brain say "البلد والعملة غير معروفين"
--                            and what forced the Telegram bot to keep asking.
--   * setMonthlyLimit()    → 0 rows changed, same silent 200. zad_budget_state() then
--                            returns monthly_limit: null, remaining: null, available: null,
--                            threat: UNKNOWN, while the phone shows a budget the customer
--                            definitely entered.
--
-- Fixing the two callers to upsert is necessary but not sufficient: the row has to exist
-- for the rows-affected check to even be meaningful, and provisioning belongs at the point
-- the account is created, not scattered across whichever screen writes first. Hence a
-- trigger on auth.users, which cannot be skipped by a client that took a different path.

create or replace function public.zad_provision_user_row()
returns trigger
language plpgsql
security definer          -- auth.users triggers run outside any user's RLS context
set search_path = public
as $$
begin
  -- on conflict do nothing: signUp()'s own upsert may win the race, and a customer who
  -- signs up, deletes, and re-registers under the same id must not error out here.
  insert into public.zad_users (id, name)
  values (
    new.id,
    nullif(trim(coalesce(new.raw_user_meta_data ->> 'name',
                         new.raw_user_meta_data ->> 'full_name', '')), '')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created_provision_zad_users on auth.users;
create trigger on_auth_user_created_provision_zad_users
  after insert on auth.users
  for each row execute function public.zad_provision_user_row();

-- Backfill every account that predates the trigger. These are exactly the accounts whose
-- market and budget writes have been landing on nothing.
insert into public.zad_users (id, name)
select u.id,
       nullif(trim(coalesce(u.raw_user_meta_data ->> 'name',
                            u.raw_user_meta_data ->> 'full_name', '')), '')
from auth.users u
left join public.zad_users z on z.id = u.id
where z.id is null;
