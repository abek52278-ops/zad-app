-- ═══════════════════════════════════════════════════════════
-- 20260824150000 — وضع الإجازة + مراقبة صحة العقل + حارس الكلفة.
--
-- 1) user_alert_snooze: العميل يقول "أنا مسافر" → كل التنبيهات الاستباقية تتوقف
--    مؤقتاً. الجدول بسيط: user_id + snoozed_until. الروتينات بتشيك عليه قبل أي task.
-- 2) brain_health_report: أسبوعياً يبعت للتطبيق تقرير صحة العقل (نسبة نجاح التشغيلات)
--    — لو نسبة الفشل عالية صاحب المشروع يشوفها بدل ما يكتشفها من شكاوى العملاء.
-- ═══════════════════════════════════════════════════════════

create table if not exists public.user_alert_snooze (
    user_id uuid primary key references auth.users(id) on delete cascade,
    snoozed_until timestamptz not null,
    reason text,
    created_at timestamptz not null default now()
);

alter table public.user_alert_snooze enable row level security;
drop policy if exists "user_own_snooze" on user_alert_snooze;
create policy "user_own_snooze" on user_alert_snooze for all
  using (auth.uid() = user_id);

comment on table public.user_alert_snooze is
  'وضع إجازة/سكون تنبيهات — الروتينات الاستباقية بتتخطى أي مستخدم صفّه لسه ساري.';

-- helper واحد بيتنادى من كل روتين — مفيش منطق متكرر يتفرق
create or replace function public._agent_user_snoozed(p_user uuid)
returns boolean language sql security definer set search_path = public stable as $$
  select exists (
    select 1 from public.user_alert_snooze
    where user_id = p_user and snoozed_until > now()
  );
$$;

revoke execute on function public._agent_user_snoozed(uuid) from public, anon, authenticated;

-- ── مراقبة صحة العقل: آخر ٧ أيام، لو نسبة الفشل >= ٣٠٪ ابعت تنبيه تشغيلي ──
create or replace function public.agent_brain_health_check()
returns void language plpgsql security definer set search_path = public as $$
declare
  v_total int;
  v_failed int;
begin
  select count(*) into v_total from public.zad_brain_runs
    where created_at >= now() - interval '7 days';
  select count(*) into v_failed from public.zad_brain_runs
    where created_at >= now() - interval '7 days' and status = 'failed';

  -- أقل من ١٠ تشغيلات = عينة ضعيفة، متصنعش إنذار كاذب
  if v_total < 10 then return; end if;
  -- نسبة الفشل تحت الثلث = طبيعي (quota spikes لحظية)
  if v_failed * 100 < v_total * 30 then return; end if;

  insert into app_notifications (user_id, title, message)
  select id, '⚠️ صحة زاد برين',
    format('آخر ٧ أيام: %s%% من تشغيلات العقل فشلة (%s من %s). الأرجح quota النموذج خلص — راجع secrets أو غيّر الموديل.',
      round(v_failed * 100.0 / v_total), v_failed, v_total)
  from auth.users limit 1;
exception
  when others then return;
end;
$$;

revoke execute on function public.agent_brain_health_check() from public, anon, authenticated;

comment on function public.agent_brain_health_check() is
  'مراقبة ذاتية للعقل: فشل >=٣٠٪ من تشغيلات آخر ٧ أيام → تنبيه تشغيلي فوري.';
