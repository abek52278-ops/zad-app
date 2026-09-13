-- دفعة واحدة، كذا إشعار: البنك بيبعت "تم خصم 500"، وInstaPay بيبعت "تم دفع فاتورة الإنترنت
-- 500"، ورسالة SMS بتظهر كإشعار تالت. كل واحد فيهم كان بيعمل اقتراح منفصل، وخطوة التأكيد
-- ماكانتش بتدوّر على تكرار — فالعميل اللي يدوس "أكد" على التلاتة بيلاقي الدفعة متخصومة
-- ٣ مرات.
--
-- الطبقة الضبابية الموجودة (20260829020000) مابتمسكش الحالة دي: محتاجة كلمة تاجر مشتركة،
-- ورسالة البنك ورسالة المحفظة عن نفس الدفعة غالبًا مافيهمش ولا كلمة مشتركة.
--
-- الحل على طبقتين:
-- 1) وقت الاستلام (handleNotificationIngest): اقتراح بنفس المبلغ خلال ١٥ دقيقة من اقتراح
--    تاني بيتعلّم duplicate_of_proposal_id، وسؤاله بيبقى "وصلني إشعارين بنفس المبلغ، هل دي
--    نفس المعاملة؟" بدل "أكد؟".
-- 2) وقت الحسم (الدالة دي): قبل أي قيد، لو فيه معاملة متسجلة فعلًا بنفس المبلغ ونفس الاتجاه
--    خلال ١٥ دقيقة من وقت الإشعار، القيد مايتكتبش — الدالة بترجع duplicate_suspected
--    وبتعلّم الاقتراح عشان التطبيق وتيليجرام يسألوا نفس السؤال.
--
-- التكرار **مابيتشالش لوحده أبدًا**. عمليتين حقيقيتين بنفس المبلغ في ربع ساعة (قهوتين، أو
-- فاتورتين بنفس القيمة) حاجة واردة، وبلع التانية بيخبّي صرف حقيقي — نفس مبدأ
-- allow_duplicate في log_transaction. القرار دايمًا من العميل:
--   'duplicate' = نفس المعاملة → الاقتراح بيتقفل merged ومايتقيدش.
--   'separate'  = عملية تانية → العلامة بتتمسح (duplicate_cleared_at) والقيد بيتكتب لو
--                 الاتجاه معروف، أو بيرجع needs_classification لو لسه مش معروف.

alter table public.zad_transaction_proposals
  add column if not exists duplicate_of_proposal_id uuid
    references public.zad_transaction_proposals(id) on delete set null,
  add column if not exists duplicate_of_transaction_id uuid
    references public.zad_transactions(id) on delete set null,
  add column if not exists duplicate_cleared_at timestamptz;

create index if not exists idx_zad_transaction_proposals_duplicate_of_proposal
  on public.zad_transaction_proposals (duplicate_of_proposal_id)
  where duplicate_of_proposal_id is not null;
create index if not exists idx_zad_transaction_proposals_duplicate_of_transaction
  on public.zad_transaction_proposals (duplicate_of_transaction_id)
  where duplicate_of_transaction_id is not null;

-- merged: العميل قال "نفس المعاملة". مش rejected عن قصد — zad_brain_stats بتحسب دقة التنبؤ
-- من posted/rejected، والإشعار المكرر مكانش تنبؤ غلط.
alter table public.zad_transaction_proposals
  drop constraint if exists zad_transaction_proposals_status_check;
alter table public.zad_transaction_proposals
  add constraint zad_transaction_proposals_status_check
  check (status in ('needs_classification', 'awaiting_confirmation', 'posted', 'rejected', 'expired', 'merged'));

-- لما اقتراح يترفض أو يتدمج، الاقتراحات اللي متعلّمة "مكررة منه" مايصحش تفضل تسأل
-- "هل دي نفس المعاملة؟" عن حاجة مابقتش هتتقيد: العميل يرفض إشعار البنك ويقول "نفس
-- المعاملة" على إشعار InstaPay، والدفعة تختفي من الحساب خالص. فالأبناء بيتنقلوا لأصل
-- الاقتراح المقفول (لو كان هو نفسه مكرر من حاجة)، أو بيرجعوا لسؤال التأكيد العادي.
create or replace function private.zad_repoint_duplicate_children(p_user uuid, p_closed uuid, p_new_parent uuid)
 returns void
 language sql
 security definer
 set search_path to ''
as $function$
  update public.zad_transaction_proposals
     set duplicate_of_proposal_id = p_new_parent, updated_at = now()
   where user_id = p_user
     and duplicate_of_proposal_id = p_closed
     and status in ('needs_classification', 'awaiting_confirmation');
$function$;

revoke all on function private.zad_repoint_duplicate_children(uuid, uuid, uuid)
  from public, anon, authenticated;

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
  v_twin_proposal_id uuid;
  v_twin_transaction_id uuid;
  v_twin_title text;
  v_twin_at timestamptz;
begin
  if p_user is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  if p_decision not in ('confirm', 'reject', 'expense', 'income', 'transfer', 'duplicate', 'separate') then
    raise exception 'invalid proposal decision' using errcode = '22023';
  end if;

  -- قفل على مستوى العميل: قفل صف الاقتراح لوحده مابيمنعش ضغطتين على اقتراحين *مختلفين*
  -- لنفس الدفعة (البنك من التطبيق وInstaPay من تيليجرام في نفس اللحظة) — كل واحد كان
  -- هيدوّر على توأم متسجل، مايلاقيش، ويقيد. القفل بيخلّي التاني يشوف قيد الأول.
  perform pg_advisory_xact_lock(hashtext('zad_resolve_transaction_proposal:' || p_user::text));

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
  if v_proposal.status = 'merged' then
    return jsonb_build_object(
      'ok', true, 'status', 'merged', 'proposal_id', v_proposal.id,
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

    perform private.zad_repoint_duplicate_children(p_user, v_proposal.id, v_proposal.duplicate_of_proposal_id);

    return jsonb_build_object(
      'ok', true, 'status', 'rejected', 'proposal_id', v_proposal.id,
      'transaction_id', null, 'already_resolved', false
    );
  end if;

  if p_decision = 'duplicate' then
    update public.zad_transaction_proposals
       set status = 'merged', decision_channel = p_channel,
           decided_at = now(), updated_at = now()
     where id = v_proposal.id;

    if v_proposal.source_event_id is not null then
      update public.zad_notification_ingest_events
         set status = 'ignored', rejection_reason = 'duplicate_confirmed_by_user',
             processed_at = coalesce(processed_at, now()), updated_at = now()
       where id = v_proposal.source_event_id and user_id = p_user;
    end if;

    perform private.zad_repoint_duplicate_children(p_user, v_proposal.id, v_proposal.duplicate_of_proposal_id);

    return jsonb_build_object(
      'ok', true, 'status', 'merged', 'proposal_id', v_proposal.id,
      'transaction_id', null, 'already_resolved', false
    );
  end if;

  if p_decision = 'separate' then
    update public.zad_transaction_proposals
       set duplicate_cleared_at = now(), updated_at = now()
     where id = v_proposal.id;
    v_proposal.duplicate_cleared_at := now();

    -- "عملية تانية" بترد على سؤال التكرار بس، مش على سؤال الاتجاه. لو الاتجاه لسه مش
    -- معروف، الاقتراح بيرجع لسؤال مصروف/دخل/تحويل بدل ما يتقيد بتخمين.
    if v_proposal.txn_kind is null then
      return jsonb_build_object(
        'ok', true, 'status', 'needs_classification', 'proposal_id', v_proposal.id,
        'transaction_id', null, 'already_resolved', false
      );
    end if;
  end if;

  v_kind := case
    when p_decision in ('expense', 'income', 'transfer') then p_decision
    else v_proposal.txn_kind
  end;

  if v_kind is null then
    raise exception 'proposal direction must be classified before confirmation' using errcode = '22023';
  end if;

  -- ─── حارس التكرار ───
  -- التوأم = حاجة **متقيدة فعلًا** بنفس المبلغ ونفس الاتجاه خلال ١٥ دقيقة من وقت الإشعار:
  -- اقتراح تاني اتأكد، أو معاملة اتسجلت من أي طريق (الشات، إدخال يدوي). الاقتراحات اللي
  -- لسه مستنية مش توأم هنا عن قصد: لو اتحسبت، أول ضغطة على أي واحد من الاتنين كانت
  -- هتتوقف، والعميل يلف في دايرة أسئلة على دفعة ماتقيدتش أصلًا. أول تأكيد بيعدّي، والتاني
  -- هو اللي بيتسأل.
  --
  -- الوقت المرجعي created_at بتاع الاقتراح (وصول الإشعار) مش now(): العميل ممكن يأكد
  -- بعد ساعات، والتكرار بيتقاس بقرب الإشعارات من بعض مش بقرب الضغطات.
  if v_proposal.duplicate_cleared_at is null then
    select p.id, p.transaction_id, p.title, p.created_at
      into v_twin_proposal_id, v_twin_transaction_id, v_twin_title, v_twin_at
      from public.zad_transaction_proposals p
     where p.user_id = p_user
       and p.id <> v_proposal.id
       and p.status = 'posted'
       -- اقتراح اتقيد والعميل مسح معاملته بعدين مابقاش بيعدّ في الرصيد — مش توأم.
       and p.transaction_id is not null
       and p.amount = v_proposal.amount
       and p.txn_kind = v_kind
       and p.created_at between v_proposal.created_at - interval '15 minutes'
                            and v_proposal.created_at + interval '15 minutes'
     order by abs(extract(epoch from (p.created_at - v_proposal.created_at)))
     limit 1;

    if v_twin_proposal_id is null then
      select t.id, t.title, t.created_at
        into v_twin_transaction_id, v_twin_title, v_twin_at
        from public.zad_transactions t
       where t.user_id = p_user
         and abs(t.amount - v_proposal.amount) < 0.005
         and coalesce(t.txn_kind, case when t.is_expense then 'expense' else 'income' end) = v_kind
         and t.created_at between v_proposal.created_at - interval '15 minutes'
                              and v_proposal.created_at + interval '15 minutes'
         -- معاملة جاية من اقتراح created_at بتاعها هو وقت الضغطة مش وقت الإشعار — اقتراح
         -- عمره ساعة اتأكد دلوقتي كان هيبان توأم لإشعار لسه واصل. دي اتقاست فوق بوقت
         -- إشعارها الحقيقي؛ هنا المعاملات اللي اتسجلت من غير اقتراح بس.
         and not exists (
           select 1 from public.zad_transaction_proposals p2 where p2.transaction_id = t.id
         )
       order by abs(extract(epoch from (t.created_at - v_proposal.created_at)))
       limit 1;
    end if;

    if v_twin_proposal_id is not null or v_twin_transaction_id is not null then
      -- الاتجاه اللي العميل اختاره بيتحفظ، عشان "عملية تانية" بعد كده تقيد بيه من غير ما
      -- تسأله تاني. والعلامة بتتكتب على الصف نفسه عشان التطبيق يعرض السؤال من القراءة
      -- العادية (Realtime) من غير نداء زيادة.
      update public.zad_transaction_proposals
         set duplicate_of_proposal_id = coalesce(v_twin_proposal_id, duplicate_of_proposal_id),
             duplicate_of_transaction_id = v_twin_transaction_id,
             txn_kind = v_kind,
             transfer_to = case when v_kind = 'transfer' then coalesce(transfer_to, 'cash') else null end,
             status = 'awaiting_confirmation',
             updated_at = now()
       where id = v_proposal.id;

      return jsonb_build_object(
        'ok', false, 'status', 'duplicate_suspected', 'proposal_id', v_proposal.id,
        'transaction_id', null, 'txn_kind', v_kind,
        'amount', v_proposal.amount, 'currency', v_proposal.currency,
        'twin_proposal_id', v_twin_proposal_id,
        'twin_transaction_id', v_twin_transaction_id,
        'twin_title', v_twin_title,
        'twin_created_at', v_twin_at,
        'already_resolved', false
      );
    end if;
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

-- create or replace بيحافظ على الصلاحيات، بس الإعادة صريحة هنا عشان الدالة الخاصة
-- ماتبقاش قابلة للنداء من برّه لو حد عملها drop/create بعد كده.
revoke all on function private.zad_resolve_transaction_proposal_impl(uuid, uuid, text, text)
  from public, anon, authenticated;

notify pgrst, 'reload schema';
