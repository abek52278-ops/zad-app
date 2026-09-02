-- بند مطلوب صراحة: "الايجنت يكلم العميل تم سحب كذة" — قبل الميجريشن دي، تأكيد معاملة
-- بنكية (عبر التطبيق أو تليجرام) كان بيرجّع رد عام واحد بس ("تمام، اتأكدت واتسجلت") بغض
-- النظر كانت الحركة اشتراك اتجدد، ولا قسط تابي/تمارة/فاليو، ولا مصروف عادي، ولا إيداع،
-- ولا تحويل كاش. كل التصنيف ده (deductionKind) كان بيتحسب وقت إنشاء الاقتراح
-- (handleNotificationIngest) بس بيتستخدم للعنوان المعروض بس — الـRPC اللي فعلاً بيسجل
-- المعاملة (private.zad_resolve_transaction_proposal_impl) كان بيرجع status:'posted'
-- من غير ما يكتب أي تعليق مفهوم للعميل خالص، مش zad_insights ولا push ولا تليجرام.
--
-- الحل: بعد ما المعاملة تتسجل فعلاً (والقسط يتربط لو BNPL)، اكتب صف zad_insights واحد
-- بنص يوصف الحركة الحقيقية اللي حصلت — رصيد قسط تابي/تمارة، أو اشتراك معروف اتجدد (مطابقة
-- بالاسم مع zad_subscriptions النشطة، نفس أسلوب zad_domain_observations's
-- possible_price_change)، أو إيداع، أو تحويل، أو مصروف عادي. surface='voice' عشان يتقال
-- بصوت زاد (ZadAlertRouter بيعامل voice كـ"يتقال مرة واحدة" — نفس الآلية الموجودة أصلاً،
-- مفيش قناة تسليم جديدة). dedupe_key مربوط بـtransaction_id فمستحيل يتكرر حتى لو نفس
-- المعاملة اتأكدت أكتر من مرة (idempotent terminal status فوق أصلاً بيمنع ده، هنا حماية
-- إضافية).
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
  v_remaining_installments int;
  v_subscription_title text;
  v_insight_title text;
  v_insight_body text;
  v_amount_text text;
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
       where id = v_matched_obligation
      returning remaining_installments into v_remaining_installments;
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

  -- تعليق العقل على الحركة الفعلية — قبل كده مفيش أي رد فعل هنا خالص. fail-open: فشل
  -- كتابة التعليق مايكسرش تسجيل المعاملة نفسها (المعاملة سُجّلت فعلاً فوق).
  begin
    v_amount_text := trim(to_char(v_proposal.amount, 'FM999999999990.00')) || ' ' || coalesce(v_proposal.currency, '');

    if v_matched_obligation is not null then
      v_insight_title := 'قسط اتخصم';
      v_insight_body := 'خصمنا ' || v_amount_text || ' كقسط لـ' || coalesce(v_proposal.bank_name, 'الممول') ||
        case when v_remaining_installments is not null and v_remaining_installments > 0
          then ' — باقي ' || v_remaining_installments || ' قسط.'
          else ' — ده آخر قسط، مبروك خلاص الالتزام ده. 🎉' end;
    elsif v_kind = 'income' then
      v_insight_title := 'استلمت فلوس';
      v_insight_body := 'استلمت ' || v_amount_text ||
        coalesce(nullif(' من ' || v_proposal.merchant_name, ' من '), '') || '.';
    elsif v_kind = 'transfer' then
      v_insight_title := 'تحويل فلوس';
      v_insight_body := 'حوّلنا ' || v_amount_text || ' لـ' ||
        coalesce(nullif(v_proposal.transfer_to, ''), 'كاش') || '.';
    else
      select title into v_subscription_title
        from public.zad_subscriptions
       where user_id = p_user and is_active
         and title is not null and v_proposal.merchant_name is not null
         and lower(btrim(title)) = lower(btrim(v_proposal.merchant_name))
       limit 1;

      if v_subscription_title is not null then
        v_insight_title := 'اشتراك اتجدد';
        v_insight_body := 'خصمنا ' || v_amount_text || ' لاشتراك ' || v_subscription_title || '.';
      else
        v_insight_title := 'مصروف جديد';
        v_insight_body := 'خصمنا ' || v_amount_text ||
          coalesce(nullif(' من ' || v_proposal.merchant_name, ' من '), '') || '.';
      end if;
    end if;

    insert into public.zad_insights (user_id, kind, surface, priority, title, body, dedupe_key, status)
    values (p_user, 'insight', 'voice', 'normal', v_insight_title, v_insight_body,
            'txn_confirmed:' || v_transaction_id::text, 'pending')
    on conflict (user_id, dedupe_key) do nothing;
  exception when others then
    raise warning 'zad_resolve_transaction_proposal: post-confirm insight failed: %', sqlerrm;
  end;

  return jsonb_build_object(
    'ok', true, 'status', 'posted', 'proposal_id', v_proposal.id,
    'transaction_id', v_transaction_id, 'txn_kind', v_kind,
    'already_resolved', false
  );
end;
$function$;
