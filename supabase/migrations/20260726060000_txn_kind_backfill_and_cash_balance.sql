-- Task 19.3 — backfill + cash balance function.
--
-- Two independent corrections, in order:
--
-- 1) The 19.2 migration's `add column txn_kind ... default 'expense'` labeled every
--    existing row 'expense' unconditionally — wrong for every historical income row
--    (is_expense = false). This MUST run before any read-side code switches from
--    is_expense to txn_kind, or income rows would briefly count as spending.
update public.zad_transactions
set txn_kind = 'income'
where is_expense = false and txn_kind = 'expense';

-- 2) Historical ATM withdrawals -> transfer/cash. This is the actual Task 19 bug
-- (19.1's audit finding): a withdrawal was recorded as a plain expense, so
-- withdrawing then spending double-counted the same money. Keyword match mirrors
-- SaBankParser's WITHDRAWAL typeRule (app/src/main/java/com/example/data/SaBankParser.kt).
update public.zad_transactions
set txn_kind = 'transfer', transfer_to = 'cash'
where is_expense = true and txn_kind = 'expense'
  and (
    title ilike '%سحب%' or title ilike '%صراف%' or title ilike '%atm%'
    or title ilike '%withdrawal%' or title ilike '%çekme%'
  );

-- Cash-on-hand balance — sum of cash inflows (transfers to cash, e.g. ATM withdrawals)
-- minus cash outflows (expenses paid from the cash wallet). Nothing calls this yet;
-- Task 19.4 (the cash card UI) is what will consume it — added now because it's part
-- of 19.3's stated scope and depends on the txn_kind column this migration finishes
-- backfilling, not because anything reads it today.
create or replace function public.zad_cash_balance(p_user uuid)
returns numeric language sql stable set search_path = public as $$
  select round(coalesce(sum(
    case
      when txn_kind = 'transfer' and transfer_to = 'cash' then amount
      when txn_kind = 'expense'  and wallet = 'cash'      then -amount
      else 0
    end
  ), 0)::numeric, 2)
  from public.zad_transactions where user_id = p_user;
$$;
