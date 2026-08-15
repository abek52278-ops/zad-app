-- `is_expense` and `txn_kind` can no longer contradict each other, and the three rows
-- where they already did are corrected.
--
-- What went wrong. ZadTransaction declares
--
--     @SerialName("txn_kind") val txnKind: String = if (isExpense) "expense" else "income"
--
-- which computes the right value — and is then dropped on the floor, because it is a
-- *default parameter value* and kotlinx.serialization omits any property still equal to its
-- default (encodeDefaults = false). The column never arrives, so Postgres applies its own
-- `default 'expense'`. Expenses survived by coincidence (both defaults say "expense");
-- income did not. On 2026-08-15 this account's three income rows read:
--
--     راتب        10000  is_expense=false  txn_kind='expense'
--     بدون وصف    10000  is_expense=false  txn_kind='expense'
--     بدون وصف    10000  is_expense=false  txn_kind='expense'
--
-- Every money figure in the product filters on txn_kind (BudgetMath and zad_budget_state
-- both do, deliberately, since 19.3). So 30,000 of *income* was counted as *spending*:
-- zad_budget_state returned spent: 30000, income: 0, by_category {"دخل": 30000},
-- remaining -20,000 against a 10,000 budget, threat OVER, and the monthly report told the
-- customer they had overspent by twenty thousand pounds they had actually earned.
--
-- The Kotlin fix (@EncodeDefault on the column) ships alongside this. This trigger is the
-- half that does not depend on remembering: any client, any edge function, any hand-written
-- INSERT is now unable to store the contradiction, so the next serializer surprise, on any
-- platform, fails safe instead of silently inverting someone's salary.

create or replace function public.zad_enforce_txn_kind()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  -- 'transfer' is deliberately untouched. An ATM withdrawal is is_expense = true AND
  -- txn_kind = 'transfer' on purpose (19.1: cash out and the later spend must not both
  -- count), so it is a valid pairing, not a contradiction to repair.
  if new.txn_kind = 'transfer' then
    return new;
  end if;

  if new.is_expense is false and new.txn_kind = 'expense' then
    new.txn_kind := 'income';
  elsif new.is_expense is true and new.txn_kind = 'income' then
    new.txn_kind := 'expense';
  end if;

  return new;
end;
$$;

drop trigger if exists zad_transactions_enforce_txn_kind on public.zad_transactions;
create trigger zad_transactions_enforce_txn_kind
  before insert or update on public.zad_transactions
  for each row execute function public.zad_enforce_txn_kind();

-- Repair the rows already stored wrong. Scoped to the exact impossible pairings, so it
-- cannot touch a legitimate transfer or an ordinary expense.
update public.zad_transactions
   set txn_kind = 'income'
 where is_expense is false and txn_kind = 'expense';

update public.zad_transactions
   set txn_kind = 'expense'
 where is_expense is true and txn_kind = 'income';
