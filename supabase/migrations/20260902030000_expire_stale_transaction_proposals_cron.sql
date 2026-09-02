-- بند من مراجعة نظام الاقتراحات البنكية: expires_at (افتراضي +7 أيام) كان بيتفحص
-- كسول بس — جوه private.zad_resolve_transaction_proposal_impl نفسها، لما حد يضغط
-- على اقتراح فات معاده بالظبط. من غير ضغطة، الصف يفضل status='needs_classification'
-- أو 'awaiting_confirmation' للأبد، وTransactionProposalCard في الأندرويد وتليجرام
-- الاتنين بيفلتروا على الـstatus مش expires_at — يعني اقتراح باين "فعّال" وأزرار
-- تأكيد/رفض شغالة عليه، رغم إنه فات عليه أسبوع وممكن يكون البنك بعت تحديث تاني
-- للمعاملة نفسها من ساعتها.
--
-- الحل زي zad_memory_prune_weekly تمامًا (0001_zad_brain.sql): دالة SQL بسيطة +
-- pg_cron، من غير أي نداء HTTP أو سيكريت — تنظيف بيانات داخلي بحت.
create or replace function public.zad_expire_stale_transaction_proposals()
returns integer
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_count integer;
begin
  update public.zad_transaction_proposals
     set status = 'expired',
         updated_at = now(),
         decided_at = coalesce(decided_at, now()),
         decision_channel = coalesce(decision_channel, 'system_expiry')
   where status in ('needs_classification', 'awaiting_confirmation')
     and expires_at <= now();
  get diagnostics v_count = row_count;
  return v_count;
end;
$function$;

-- مفيش حد المفروض ينده الدالة دي مباشرة غير الكرون — مش أداة عميل ولا أداة عقل.
revoke execute on function public.zad_expire_stale_transaction_proposals() from public, anon, authenticated;

do $$
begin
  if exists (select 1 from cron.job where jobname = 'zad-expire-stale-transaction-proposals') then
    perform cron.unschedule('zad-expire-stale-transaction-proposals');
  end if;
  perform cron.schedule(
    'zad-expire-stale-transaction-proposals',
    '30 3 * * *',
    $cron$select public.zad_expire_stale_transaction_proposals()$cron$
  );
end $$;
