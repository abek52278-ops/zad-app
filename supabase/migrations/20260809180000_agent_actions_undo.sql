-- ═══════════════════════════════════════════════════════════════════════════
-- agent_actions — سجل تدقيق لكل أداة نفّذها الوكيل، ومحرك التراجع المبني عليه.
--
-- ليه جدول جديد مع إن zad_brain_runs موجود؟ zad_brain_runs بيسجّل **اللفة** كلها
-- (jsonb blob في العمود mutations) — مفيد للتشخيص، بس مستحيل يتبنى عليه تراجع:
-- مفيش صف لكل فعل، ومفيش target_id، فمفيش حاجة تتحدد وترجع لحالتها. الجدول ده صف
-- لكل أداة، وفيه العنصر اللي بيخلي التراجع ممكن: previous_state/new_state.
--
-- العقد اللي الـ Edge Function لازم يحترمه:
--   INSERT  → previous_state = null,  new_state = الصف بعد الكتابة (مقروء من الداتابيز)
--   UPDATE  → previous_state = الصف قبل، new_state = الصف بعد (الاتنين مقروءين فعلاً)
--   DELETE  → previous_state = الصف قبل، new_state = null
-- الحالات التلاتة دي هي اللي zad_agent_undo() بيفرّق بيها لوحده — مفيش عمود "نوع
-- العملية" منفصل يقدر يتناقض معاهم.
--
-- ملاحظة أمان: new_state لازم يكون **مقروء من الداتابيز بعد الكتابة**، مش نسخة من
-- الـ input بتاع الموديل. الفرق ده هو كل الفرق بين سجل تدقيق حقيقي وسجل بيوثّق نية
-- الموديل: لو trigger أو default أو CHECK غيّر القيمة، السجل لازم يعرف القيمة اللي
-- استقرت فعلاً، مش اللي الموديل طلبها.
-- ═══════════════════════════════════════════════════════════════════════════

create table if not exists public.agent_actions (
  id uuid primary key default gen_random_uuid(),
  -- ترتيب مطلق للأفعال. **مش** created_at: في بوستجرس now() هو وقت الترانزاكشن، يعني
  -- فعلين في نفس الترانزاكشن ليهم نفس الطابع الزمني بالظبط — وساعتها شرط "في فعل أحدث
  -- لمس نفس الصف؟" (الحارس ضد التراجع البايت تحت) بيعدّي بالغلط. اتكشف باختبار فعلي،
  -- مش احتياط نظري. seq بيدي ترتيب كلي مستقل عن الساعة.
  seq bigint generated always as identity,
  user_id uuid not null references auth.users(id) on delete cascade,
  -- اللفة اللي الفعل ده حصل جواها. on delete set null عشان تنضيف zad_brain_runs
  -- القديمة ما تمسحش سجل التدقيق نفسه.
  run_id uuid references public.zad_brain_runs(id) on delete set null,
  -- من فين جه الطلب. القيم مطابقة لقنوات agent_turn/agent_confirm الحقيقية في
  -- zad-brain/index.ts دلوقتي (شات التطبيق، بوت تليجرام، زرار تأكيد) + trigger الحلقة
  -- الخلفية (daily/event، نفس قيم zad_brain_runs.trigger بالظبط) — مش قنوات نظرية
  -- لسه ملهاش استدعاء فعلي في الكود.
  source text not null default 'app_chat'
    check (source in ('app_chat','telegram','confirm','daily','event')),
  tool_name text not null,
  input jsonb not null default '{}'::jsonb,

  -- هدف الفعل. null/null معناها أداة مالهاش صف واحد محدد (مثلاً query_family) —
  -- بتتسجل للشفافية بس مش قابلة للتراجع.
  target_table text,
  target_id uuid,
  previous_state jsonb,
  new_state jsonb,

  status text not null default 'applied' check (status in ('applied','rejected','undone')),
  result_summary text,
  created_at timestamptz not null default now(),
  undone_at timestamptz
);

create index if not exists idx_agent_actions_user_time
  on public.agent_actions(user_id, seq desc);
-- بيخدم فحص "في فعل أحدث لمس نفس الصف؟" في zad_agent_undo — الحارس ضد التراجع البايت.
create index if not exists idx_agent_actions_target
  on public.agent_actions(user_id, target_table, target_id, seq desc)
  where target_id is not null;

alter table public.agent_actions enable row level security;

-- قراءة بس للعميل. الكتابة من الـ Edge Function بمفتاح service_role (بيتخطى RLS)،
-- والتراجع عن طريق الـ RPC تحت — مفيش مسار يخلي الكلاينت يكتب أو يعدّل سجل تدقيق
-- بنفسه، وده الفرق بين سجل موثوق وسجل قابل للتلفيق.
drop policy if exists "user_reads_own_agent_actions" on public.agent_actions;
create policy "user_reads_own_agent_actions" on public.agent_actions
  for select using (auth.uid() = user_id);

comment on table public.agent_actions is
  'صف لكل أداة نفّذها الوكيل، بلقطة قبل/بعد. مصدر شاشة "سجل تعديلات زاد" ومحرك zad_agent_undo().';

-- ═══════════════════════════════════════════════════════════════════════════
-- zad_agent_undo — يرجّع الداتابيز لحالة previous_state بالظبط.
--
-- الجداول المسموح بالتراجع فيها allowlist صريحة، مش أي اسم جاي في العمود: العمود
-- بيتحط من الـ Edge Function، وأي بق فيه (أو صف اتلفّق بأي طريقة مستقبلاً) ما ينفعش
-- يتحول لـ DDL/DML على جدول عشوائي عن طريق format('%I').
-- ═══════════════════════════════════════════════════════════════════════════

create or replace function public.zad_agent_undo(p_action_id uuid default null)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_act public.agent_actions%rowtype;
  v_owner_col text;
  v_set text;
  v_rows int;
  v_newer int;
begin
  if v_uid is null then
    return jsonb_build_object('ok', false, 'error', 'not_authenticated');
  end if;

  -- من غير p_action_id: آخر فعل قابل للتراجع للعميل ده. "قابل للتراجع" = متنفّذ فعلاً،
  -- لسه ماترجعش، وليه صف هدف محدد.
  if p_action_id is null then
    select * into v_act from public.agent_actions
     where user_id = v_uid and status = 'applied' and target_table is not null and target_id is not null
     order by seq desc limit 1;
  else
    select * into v_act from public.agent_actions
     where id = p_action_id and user_id = v_uid;
  end if;

  if not found then
    return jsonb_build_object('ok', false, 'error', 'no_action_to_undo');
  end if;
  if v_act.status = 'undone' then
    return jsonb_build_object('ok', false, 'error', 'already_undone');
  end if;
  if v_act.status = 'rejected' then
    return jsonb_build_object('ok', false, 'error', 'action_never_applied');
  end if;
  if v_act.target_table is null or v_act.target_id is null then
    return jsonb_build_object('ok', false, 'error', 'action_not_undoable');
  end if;

  -- عمود الملكية بيفرق حسب الجدول، والـ allowlist دي هي الحارس الوحيد على اسم الجدول
  -- قبل ما يدخل format('%I'). أي جدول مش هنا = مرفوض، مش "يتجرب ونشوف".
  v_owner_col := case v_act.target_table
    when 'zad_transactions'    then 'user_id'
    when 'zad_inventory'       then 'user_id'
    when 'zad_pharmacy_items'  then 'user_id'
    when 'zad_pharmacy_doses'  then 'user_id'
    when 'zad_shopping_list'   then 'user_id'
    when 'zad_obligations'     then 'user_id'
    -- بروفايل العميل: الـ PK نفسه هو الـ user id، مفيش عمود user_id.
    when 'zad_users'           then 'id'
    else null
  end;
  if v_owner_col is null then
    return jsonb_build_object('ok', false, 'error', 'table_not_undoable', 'table', v_act.target_table);
  end if;

  -- الحارس ضد التراجع البايت: لو فعل أحدث لمس **نفس الصف**، الرجوع لـ previous_state
  -- بتاع الفعل القديم هيدهس التعديل الأحدث من غير ما حد يقصد. الترتيب الصح إن العميل
  -- يتراجع عن الأحدث الأول.
  select count(*) into v_newer from public.agent_actions
   where user_id = v_uid
     and target_table = v_act.target_table
     and target_id = v_act.target_id
     and status = 'applied'
     and seq > v_act.seq;
  if v_newer > 0 then
    return jsonb_build_object('ok', false, 'error', 'newer_action_exists', 'newer_count', v_newer);
  end if;

  if v_act.previous_state is null then
    -- كان INSERT → التراجع حذف. zad_users مستثنى: مفيش "إدراج بروفايل" أصلاً، ومسح
    -- صف بروفايل بالغلط خسارة مالهاش رجعة.
    if v_act.target_table = 'zad_users' then
      return jsonb_build_object('ok', false, 'error', 'profile_delete_refused');
    end if;
    execute format('delete from public.%I where id = $1 and %I = $2', v_act.target_table, v_owner_col)
      using v_act.target_id, v_uid;
    get diagnostics v_rows = row_count;

  elsif v_act.new_state is null then
    -- كان DELETE → التراجع إعادة إدراج الصف بالظبط زي ما كان (بنفس الـ id).
    -- jsonb_populate_record بيحوّل الـ jsonb لصف من نوع الجدول نفسه، فالأنواع بتتحوّل
    -- صح من غير كتابة CAST يدوي لكل عمود.
    if (v_act.previous_state ->> v_owner_col)::uuid is distinct from v_uid then
      return jsonb_build_object('ok', false, 'error', 'ownership_mismatch');
    end if;
    execute format(
      'insert into public.%I select * from jsonb_populate_record(null::public.%I, $1)',
      v_act.target_table, v_act.target_table
    ) using v_act.previous_state;
    get diagnostics v_rows = row_count;

  else
    -- كان UPDATE → رجّع الأعمدة اللي كانت في previous_state بس، مش الصف كله: أي عمود
    -- الوكيل مالمسوش (مثلاً created_at أو عمود ضافه migration بعدين) يفضل زي ما هو.
    select string_agg(format('%I = r.%I', k, k), ', ')
      into v_set
      from jsonb_object_keys(v_act.previous_state) k
     where k <> 'id' and k <> v_owner_col;   -- الهوية والملكية عمرهم ما يتكتبوا من تراجع

    if v_set is null then
      return jsonb_build_object('ok', false, 'error', 'nothing_to_restore');
    end if;

    execute format(
      'update public.%I t set %s from (select * from jsonb_populate_record(null::public.%I, $3)) r
        where t.id = $1 and t.%I = $2',
      v_act.target_table, v_set, v_act.target_table, v_owner_col
    ) using v_act.target_id, v_uid, v_act.previous_state;
    get diagnostics v_rows = row_count;
  end if;

  -- صفر صفوف = التراجع ما حصلش. تعليم الفعل "undone" هنا كان هيبقى كذب مسجّل في
  -- سجل التدقيق نفسه — أسوأ من عدم وجود السجل.
  if v_rows = 0 then
    return jsonb_build_object('ok', false, 'error', 'target_row_missing', 'table', v_act.target_table);
  end if;

  update public.agent_actions
     set status = 'undone', undone_at = now()
   where id = v_act.id;

  return jsonb_build_object(
    'ok', true,
    'action_id', v_act.id,
    'tool_name', v_act.tool_name,
    'table', v_act.target_table,
    'rows', v_rows
  );
end;
$$;

revoke all on function public.zad_agent_undo(uuid) from public;
grant execute on function public.zad_agent_undo(uuid) to authenticated;

comment on function public.zad_agent_undo(uuid) is
  'يتراجع عن فعل واحد في agent_actions برجوع previous_state. بيرفض لو فيه فعل أحدث لمس نفس الصف.';
