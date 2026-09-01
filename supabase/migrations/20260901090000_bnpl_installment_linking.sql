-- تجهيز بنية تحتية لتتبع خطط تابي/تمارة/فاليو — مش بناء UI جديد كامل في الميجريشن دي.
-- القرار (بعد مراجعة llm-council): إشعار بنكي واحد بيقول "دفعت X" بس، مش بيقول كام قسط
-- باقي ولا قيمة القسط الشهري — أي محاولة نخترع الأرقام دي هتبقى نفس غلطة subscription
-- price change (رقم واثق بس مش حقيقي). فالتصميم هنا اتنين مسارين بس، صفريهم بيخترع بيانات:
--
-- 1) "ده قسط لخطة موجودة فعلاً" — العميل قال قبل كده عدد الأقساط ومبلغها (add_obligation
--    في الشات)، فالإشعار الجاي بنفس المزوّد والمبلغ بيتربط تلقائيًا ويقلل remaining_installments.
--    مطابقة بمزوّد+مبلغ+نشط، مش استنتاج — SaBankParser أصلاً بيرجّع bank_name="تابي"/"تمارة"/
--    "فاليو" حرفيًا للإشعارات دي (Kotlin مش محتاج أي تعديل، البيانات وصلت للسيرفر من الأول).
-- 2) "قسط أول مرة نشوفه من المزوّد ده" — مفيش خطة نربطه بيها، فبيتسجل كمعاملة عادية بس
--    (زي دلوقتي بالظبط)، والعميل هو اللي يقرر لو عايز يسجل خطة عبر add_obligation.
--    مفيش placeholder ولا draft بيتكتب من غير ما العميل يقول تفاصيل حقيقية.

alter table public.zad_obligations
  add column if not exists provider text,
  add column if not exists total_installments integer,
  add column if not exists remaining_installments integer;

alter table public.zad_transactions
  add column if not exists linked_obligation_id uuid references public.zad_obligations(id) on delete set null;

create index if not exists idx_zad_obligations_provider
  on public.zad_obligations(user_id, provider) where provider is not null;

-- بيدوّر على خطة تقسيط نشطة بنفس المزوّد وبنفس المبلغ (سماحية صغيرة لفروق تقريب) وباقي
-- لها أقساط. مفيش تخمين هنا — تطابق أو مفيش نتيجة، من غير ترتيب "أقرب حاجة".
create or replace function public.zad_match_bnpl_obligation(p_user uuid, p_provider text, p_amount numeric)
returns uuid
language sql
stable
security definer
set search_path to 'public'
as $function$
  select id from zad_obligations
   where user_id = p_user
     and kind = 'installment'
     and confirmed = true
     and active = true
     and provider is not null and lower(btrim(provider)) = lower(btrim(p_provider))
     and remaining_installments is not null and remaining_installments > 0
     and abs(amount - p_amount) <= greatest(amount * 0.02, 1)
   order by created_at asc
   limit 1;
$function$;

-- تحديث zad_resolve_transaction_proposal_impl: بعد ما المعاملة تتكتب فعليًا (تأكيد العميل
-- زي أي حاجة تانية)، لو bank_name المقترح مزوّد تقسيط معروف، نجرّب نربطها بخطة موجودة
-- ونقلل الباقي. فشل الربط مش بيوقف كتابة المعاملة نفسها — ده تحسين إضافي مش شرط.
create or replace function private.zad_resolve_transaction_proposal_impl(p_user uuid, p_proposal uuid, p_decision text, p_channel text)
returns jsonb
language plpgsql
security definer
set search_path to ''
as $function$
declare
  v_proposal public.zad_transaction_proposals%rowtype;
  v_kind text;
  v_transaction_id uuid;
  v_matched_obligation uuid;
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

  -- تجهيز بنية أقساط تابي/تمارة/فاليو (2026-09-01) — مطابقة بس، مفيش اختراع بيانات.
  if v_kind = 'expense' and v_proposal.bank_name is not null
     and lower(btrim(v_proposal.bank_name)) = any (array['تابي', 'تمارة', 'فاليو', 'tabby', 'tamara', 'valu']) then
    v_matched_obligation := public.zad_match_bnpl_obligation(p_user, v_proposal.bank_name, v_proposal.amount);
    if v_matched_obligation is not null then
      update public.zad_transactions
         set linked_obligation_id = v_matched_obligation
       where id = v_transaction_id;
      update public.zad_obligations
         set remaining_installments = remaining_installments - 1,
             active = (remaining_installments - 1) > 0
       where id = v_matched_obligation;
    end if;
  end if;

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
$function$;
