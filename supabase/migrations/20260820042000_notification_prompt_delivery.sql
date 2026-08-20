-- Track delivery of the confirmation prompt independently from notification ingestion.
-- A notification can be accepted by zad-brain before Telegram answers; retries must resume
-- that delivery without creating a second proposal or repeatedly messaging the customer.

alter table public.zad_notification_ingest_events
  add column if not exists confirmation_prompt_claimed_at timestamptz,
  add column if not exists confirmation_prompt_delivered_at timestamptz;

comment on column public.zad_notification_ingest_events.confirmation_prompt_claimed_at is
  'Short delivery lease used to prevent concurrent retries from sending the same Telegram prompt.';

comment on column public.zad_notification_ingest_events.confirmation_prompt_delivered_at is
  'Set only after Telegram accepts the confirmation/review prompt; null means delivery may be retried.';

-- Older zad-brain builds marked a transient proposal insert failure as terminal. Re-open
-- those audit rows so the next identical notification can finish the existing ingest.
update public.zad_notification_ingest_events
   set status = 'received', updated_at = now()
 where status = 'rejected' and rejection_reason = 'proposal_insert_failed';

create or replace function private.zad_claim_notification_prompt_impl(
  p_user uuid,
  p_event uuid
) returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_claimed boolean := false;
begin
  update public.zad_notification_ingest_events
     set confirmation_prompt_claimed_at = now(), updated_at = now()
   where id = p_event
     and user_id = p_user
     and confirmation_prompt_delivered_at is null
     and (
       confirmation_prompt_claimed_at is null
       or confirmation_prompt_claimed_at < now() - interval '2 minutes'
     )
  returning true into v_claimed;

  return coalesce(v_claimed, false);
end;
$$;

create or replace function public.zad_claim_notification_prompt_service(
  p_user uuid,
  p_event uuid
) returns boolean
language sql
security definer
set search_path = ''
as $$
  select private.zad_claim_notification_prompt_impl(p_user, p_event);
$$;

revoke all on function private.zad_claim_notification_prompt_impl(uuid, uuid)
  from public, anon, authenticated;
revoke all on function public.zad_claim_notification_prompt_service(uuid, uuid)
  from public, anon, authenticated;
grant execute on function public.zad_claim_notification_prompt_service(uuid, uuid)
  to service_role;

create or replace function private.zad_finish_notification_prompt_impl(
  p_user uuid,
  p_event uuid,
  p_delivered boolean
) returns void
language sql
security definer
set search_path = ''
as $$
  update public.zad_notification_ingest_events
     set confirmation_prompt_claimed_at = null,
         confirmation_prompt_delivered_at = case
           when p_delivered then coalesce(confirmation_prompt_delivered_at, now())
           else confirmation_prompt_delivered_at
         end,
         updated_at = now()
   where id = p_event and user_id = p_user;
$$;

create or replace function public.zad_finish_notification_prompt_service(
  p_user uuid,
  p_event uuid,
  p_delivered boolean
) returns void
language sql
security definer
set search_path = ''
as $$
  select private.zad_finish_notification_prompt_impl(p_user, p_event, p_delivered);
$$;

revoke all on function private.zad_finish_notification_prompt_impl(uuid, uuid, boolean)
  from public, anon, authenticated;
revoke all on function public.zad_finish_notification_prompt_service(uuid, uuid, boolean)
  from public, anon, authenticated;
grant execute on function public.zad_finish_notification_prompt_service(uuid, uuid, boolean)
  to service_role;

notify pgrst, 'reload schema';
