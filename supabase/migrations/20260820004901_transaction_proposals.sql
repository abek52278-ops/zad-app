-- One durable proposal for every detected bank transaction.
--
-- Previously Telegram parked structured data in telegram_pending_writes while Android only
-- received a prose zad_insights question. Answering in Android sent that prose back through
-- the LLM, which could reinterpret the amount or direction. This table is now the shared
-- contract: both channels resolve the same row and the database posts it exactly once.

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table if not exists public.zad_transaction_proposals (
  id                uuid primary key default gen_random_uuid(),
  user_id           uuid not null references auth.users(id) on delete cascade,
  source_event_id   uuid references public.zad_notification_ingest_events(id) on delete set null,
  idempotency_key   text not null,
  source_type       text not null default 'notification_listener',
  status            text not null default 'awaiting_confirmation'
    check (status in ('needs_classification', 'awaiting_confirmation', 'posted', 'rejected', 'expired')),
  txn_kind          text check (txn_kind in ('expense', 'income', 'transfer')),
  amount            numeric(14, 2) not null check (amount > 0),
  title             text not null,
  category          text,
  currency          text,
  wallet            text not null default 'card' check (wallet in ('cash', 'card', 'bank')),
  transfer_to       text check (transfer_to is null or transfer_to in ('cash', 'card', 'bank')),
  merchant_name     text,
  bank_name         text,
  confidence        numeric(4, 3) check (confidence is null or confidence between 0 and 1),
  transaction_id    uuid unique references public.zad_transactions(id) on delete set null,
  decision_channel  text,
  decided_at        timestamptz,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz not null default now(),
  expires_at        timestamptz not null default now() + interval '7 days',
  unique (user_id, idempotency_key),
  check (
    (txn_kind = 'transfer' and transfer_to is not null) or
    (txn_kind is distinct from 'transfer' and transfer_to is null)
  )
);

create index if not exists idx_zad_transaction_proposals_pending
  on public.zad_transaction_proposals (user_id, status, created_at desc)
  where status in ('needs_classification', 'awaiting_confirmation');

alter table public.zad_transaction_proposals enable row level security;

drop policy if exists user_reads_own_transaction_proposals on public.zad_transaction_proposals;
create policy user_reads_own_transaction_proposals
  on public.zad_transaction_proposals
  for select
  to authenticated
  using ((select auth.uid()) = user_id);

revoke all on table public.zad_transaction_proposals from public, anon, authenticated;
grant select on table public.zad_transaction_proposals to authenticated;
grant all on table public.zad_transaction_proposals to service_role;

comment on table public.zad_transaction_proposals is
  'Structured, idempotent bank transaction proposals shared by Android and Telegram. A proposal reaches zad_transactions only through the resolve RPC after an explicit user decision.';

-- The implementation lives outside the exposed API schema. Public wrappers below are the
-- only entry points and each one supplies an already-authorized user id.
create or replace function private.zad_resolve_transaction_proposal_impl(
  p_user uuid,
  p_proposal uuid,
  p_decision text,
  p_channel text
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_proposal public.zad_transaction_proposals%rowtype;
  v_kind text;
  v_transaction_id uuid;
begin
  if p_user is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  if p_decision not in ('confirm', 'reject', 'expense', 'income', 'transfer') then
    raise exception 'invalid proposal decision' using errcode = '22023';
  end if;

  select * into v_proposal
    from public.zad_transaction_proposals
   where id = p_proposal and user_id = p_user
   for update;

  if not found then
    raise exception 'proposal not found' using errcode = 'P0002';
  end if;

  -- Idempotent terminal results: a second app tap or Telegram callback never posts again.
  if v_proposal.status = 'posted' then
    return jsonb_build_object(
      'ok', true, 'status', 'posted', 'proposal_id', v_proposal.id,
      'transaction_id', v_proposal.transaction_id, 'already_resolved', true
    );
  end if;
  if v_proposal.status = 'rejected' then
    return jsonb_build_object(
      'ok', true, 'status', 'rejected', 'proposal_id', v_proposal.id,
      'transaction_id', null, 'already_resolved', true
    );
  end if;
  if v_proposal.status = 'expired' or v_proposal.expires_at <= now() then
    update public.zad_transaction_proposals
       set status = 'expired', updated_at = now(), decided_at = coalesce(decided_at, now()),
           decision_channel = coalesce(decision_channel, p_channel)
     where id = v_proposal.id;
    return jsonb_build_object('ok', false, 'status', 'expired', 'proposal_id', v_proposal.id);
  end if;

  if p_decision = 'reject' then
    update public.zad_transaction_proposals
       set status = 'rejected', decision_channel = p_channel,
           decided_at = now(), updated_at = now()
     where id = v_proposal.id;

    if v_proposal.source_event_id is not null then
      update public.zad_notification_ingest_events
         set status = 'rejected', rejection_reason = 'user_rejected', updated_at = now()
       where id = v_proposal.source_event_id and user_id = p_user;
    end if;

    return jsonb_build_object(
      'ok', true, 'status', 'rejected', 'proposal_id', v_proposal.id,
      'transaction_id', null, 'already_resolved', false
    );
  end if;

  v_kind := case
    when p_decision in ('expense', 'income', 'transfer') then p_decision
    else v_proposal.txn_kind
  end;

  if v_kind is null then
    raise exception 'proposal direction must be classified before confirmation' using errcode = '22023';
  end if;

  -- A classification correction can turn an uncertain row into a transfer. The default
  -- destination is cash because notification_parser only emits transfer for ATM withdrawal.
  insert into public.zad_transactions (
    user_id, amount, title, category, is_expense, bank_name, merchant_name,
    source_type, is_verified, wallet, txn_kind, transfer_to, currency
  ) values (
    p_user,
    v_proposal.amount,
    v_proposal.title,
    coalesce(v_proposal.category, case when v_kind = 'income' then 'دخل' when v_kind = 'transfer' then 'تحويل' else 'أخرى' end),
    v_kind <> 'income',
    v_proposal.bank_name,
    v_proposal.merchant_name,
    v_proposal.source_type,
    true,
    v_proposal.wallet,
    v_kind,
    case when v_kind = 'transfer' then coalesce(v_proposal.transfer_to, 'cash') else null end,
    v_proposal.currency
  ) returning id into v_transaction_id;

  update public.zad_transaction_proposals
     set status = 'posted', txn_kind = v_kind,
         transfer_to = case when v_kind = 'transfer' then coalesce(transfer_to, 'cash') else null end,
         transaction_id = v_transaction_id, decision_channel = p_channel,
         decided_at = now(), updated_at = now()
   where id = v_proposal.id;

  if v_proposal.source_event_id is not null then
    update public.zad_notification_ingest_events
       set status = 'logged', rejection_reason = null,
           transaction_id = v_transaction_id, updated_at = now()
     where id = v_proposal.source_event_id and user_id = p_user;
  end if;

  return jsonb_build_object(
    'ok', true, 'status', 'posted', 'proposal_id', v_proposal.id,
    'transaction_id', v_transaction_id, 'txn_kind', v_kind,
    'already_resolved', false
  );
end;
$$;

revoke all on function private.zad_resolve_transaction_proposal_impl(uuid, uuid, text, text)
  from public, anon, authenticated;

create or replace function public.zad_resolve_transaction_proposal(
  p_proposal uuid,
  p_decision text,
  p_channel text default 'app'
) returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
begin
  if v_user is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  return private.zad_resolve_transaction_proposal_impl(v_user, p_proposal, p_decision, p_channel);
end;
$$;

revoke all on function public.zad_resolve_transaction_proposal(uuid, text, text)
  from public, anon, authenticated;
grant execute on function public.zad_resolve_transaction_proposal(uuid, text, text)
  to authenticated;

create or replace function public.zad_resolve_transaction_proposal_service(
  p_user uuid,
  p_proposal uuid,
  p_decision text,
  p_channel text default 'telegram'
) returns jsonb
language sql
security definer
set search_path = ''
as $$
  select private.zad_resolve_transaction_proposal_impl(p_user, p_proposal, p_decision, p_channel);
$$;

revoke all on function public.zad_resolve_transaction_proposal_service(uuid, uuid, text, text)
  from public, anon, authenticated;
grant execute on function public.zad_resolve_transaction_proposal_service(uuid, uuid, text, text)
  to service_role;

-- Realtime lets an approval made on Telegram disappear from Android immediately and lets the
-- green card refresh without waiting for the next periodic sync.
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
     and not exists (
       select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'zad_transaction_proposals'
     ) then
    alter publication supabase_realtime add table public.zad_transaction_proposals;
  end if;
end;
$$;

notify pgrst, 'reload schema';
