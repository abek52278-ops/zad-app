-- A transaction in another currency was summed as if it were local money.
--
-- BudgetMath now converts before summing on the client, but zad_budget_state still added
-- `amount` raw, so the phone and the authority would disagree the moment a foreign row
-- appeared — and the Telegram bot, which reads only the authority, would simply be wrong.
--
-- The obvious fix is a rate table plus conversion inside zad_budget_state. It was not
-- taken, for two reasons. It puts a second copy of the FX rates in the system, free to
-- drift from CurrencyExchange.kt. And it means re-emitting the whole budget authority by
-- hand to wrap a few sums, which is a transcription risk on the one function that decides
-- what a household is told it can spend.
--
-- Normalising at rest is smaller and fixes more. The row is converted once, on the way
-- in, and every reader — this SQL, the Kotlin mirror, the bot, the brain — is correct
-- afterwards without knowing anything about currencies. zad_budget_state is not touched
-- at all.
--
-- The original values are kept. `amount` becomes the account's currency, and
-- original_amount/original_currency preserve exactly what the bank said, so a receipt can
-- still be shown as "$100" while the budget counts it correctly, and so a wrong rate can
-- be recomputed later instead of having destroyed the source.
--
-- Double conversion is prevented by the trigger also rewriting `currency` to the account's
-- currency. A converted row therefore reads as same-currency to BudgetMath.normalizedToCurrency,
-- which leaves it alone, and to this trigger on any later UPDATE.

create table if not exists public.zad_fx_rates (
  code       text primary key,
  usd_rate   numeric not null check (usd_rate > 0),
  updated_at timestamptz not null default now()
);

comment on table public.zad_fx_rates is
  'How many USD one unit of this currency is worth. Mirrors CurrencyExchange.kt — the app keeps its own copy so it can convert offline, and these are the authoritative values. Approximate and hand-maintained; update both sides together.';

alter table public.zad_fx_rates enable row level security;

-- Rates are not user data and every client benefits from reading them; writes stay with
-- service_role, which is the only role without a policy standing in its way.
drop policy if exists zad_fx_rates_readable on public.zad_fx_rates;
create policy zad_fx_rates_readable on public.zad_fx_rates for select to authenticated, anon using (true);

insert into public.zad_fx_rates (code, usd_rate) values
  ('USD', 1.0),      ('SAR', 0.2667),   ('EGP', 0.0204),   ('AED', 0.2723),
  ('KWD', 3.2500),   ('QAR', 0.2747),   ('BHD', 2.6525),   ('OMR', 2.5974),
  ('JOD', 1.4104),   ('LBP', 0.0000112),('IQD', 0.000763), ('SYP', 0.0000769),
  ('YER', 0.0040),   ('ILS', 0.2740),   ('LYD', 0.2058),   ('SDG', 0.0017),
  ('MAD', 0.1002),   ('TND', 0.3210),   ('DZD', 0.0074),   ('TRY', 0.0295)
on conflict (code) do nothing;

alter table public.zad_transactions
  add column if not exists original_amount   numeric,
  add column if not exists original_currency text;

comment on column public.zad_transactions.original_amount is
  'Set only when the row arrived in a currency other than the account''s. `amount` is always the account''s currency.';

/**
 * Converts between two currencies using zad_fx_rates. Returns the amount unchanged when
 * either side is unknown — refusing to guess a rate is the same rule the client follows,
 * and a wrong rate on a money row is worse than an unconverted one, which at least stays
 * traceable to what the bank actually said.
 */
create or replace function public.zad_convert_currency(
  p_amount numeric, p_from text, p_to text
) returns numeric language plpgsql stable set search_path = public as $$
declare
  v_from numeric;
  v_to numeric;
begin
  if p_amount is null then return null; end if;
  if p_from is null or p_to is null or upper(p_from) = upper(p_to) then return p_amount; end if;
  select usd_rate into v_from from public.zad_fx_rates where code = upper(p_from);
  select usd_rate into v_to   from public.zad_fx_rates where code = upper(p_to);
  if v_from is null or v_to is null or v_to = 0 then return p_amount; end if;
  return round(p_amount * (v_from / v_to), 2);
end;
$$;

create or replace function public.zad_normalize_transaction_currency()
returns trigger language plpgsql security definer set search_path = public as $$
declare
  v_home text;
  v_row_cur text;
begin
  v_row_cur := nullif(btrim(coalesce(new.currency, '')), '');
  -- No currency on the row means "the account's own", which is what the client assumes
  -- too. Nothing to convert, and nothing to record as an original.
  if v_row_cur is null then return new; end if;

  select nullif(btrim(coalesce(currency, '')), '') into v_home
    from public.zad_users where id = new.user_id;
  -- Account currency unknown: leave the row exactly as it arrived rather than convert
  -- toward a guess. It stays correct as soon as the profile is filled in.
  if v_home is null or upper(v_home) = upper(v_row_cur) then return new; end if;

  if not exists (select 1 from public.zad_fx_rates where code = upper(v_row_cur))
     or not exists (select 1 from public.zad_fx_rates where code = upper(v_home)) then
    return new;
  end if;

  new.original_amount   := new.amount;
  new.original_currency := upper(v_row_cur);
  new.amount            := public.zad_convert_currency(new.amount, v_row_cur, v_home);
  new.currency          := upper(v_home);
  return new;
end;
$$;

drop trigger if exists zad_transactions_normalize_currency on public.zad_transactions;
create trigger zad_transactions_normalize_currency
  before insert or update of amount, currency on public.zad_transactions
  for each row execute function public.zad_normalize_transaction_currency();

grant execute on function public.zad_convert_currency(numeric, text, text)
  to authenticated, anon, service_role;

-- No backfill: every existing row has currency null, which already means "the account's
-- currency". There is nothing to convert, and inventing an original_currency for rows
-- whose currency was never recorded would be fabricating history.
