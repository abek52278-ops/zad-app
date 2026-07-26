-- Task 19.2 — schema for wallet/txn_kind/transfer_to on zad_transactions.
--
-- Existing rows default to card/expense — the current behaviour — so nothing changes
-- until the client starts classifying new transactions (this same commit, in
-- BankTransactionApplier for the WITHDRAWAL case; every other construction site gets
-- a correct txn_kind for free via ZadTransaction's self-referencing Kotlin default).
--
-- Historical backfill (reclassifying existing ATM withdrawals to txn_kind='transfer')
-- is deliberately NOT part of this migration — it changes numbers users already see
-- and trust, and per Task 19.2's own instructions needs to be reported (rows changed,
-- budget correction per user) before it runs, not silently bundled into a schema change.
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
