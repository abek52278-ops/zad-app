-- 20260913160000 — إصلاح حارس الكلفة: كان بيجمع عمود مش موجود، وبيخبّي ده.
--
-- `agent_brain_cost_guard()` (20260824153000) كان بيفلتر `zad_brain_runs.created_at`،
-- والعمود اسمه `started_at` — الجدول مفيهوش `created_at` أصلاً. الاستعلام بيرمي
-- `undefined_column`، و`exception when others then return 0` كان بيبلعه ويرجّع ٠٪.
-- النتيجة من يوم 2026-08-24: الحارس عمره مااشتغل، والسطر `cost_pct=0` في لوج
-- `agent_proactive_scan` كان بيتقرا "مفيش استهلاك" وهو في الحقيقة "الحارس بايظ".
-- عتبة الـ٩٥٪ في الفحص الاستباقي والتنبيهات مكانتش هتتفعّل أبدًا مهما الصرف زاد.
--
-- قبل الإصلاح اتقاس على الإنتاج (2026-09-13): الاستهلاك الشهر ده 576,993 توكن
-- = **1%** من السقف، والشهر اللي فات 2,196,249 = 4%. يعني تشغيل الحارس صح مابيغيّرش
-- أي سلوك النهاردة (الخنق بيبدأ عند ٩٥٪) — بيرجّع بس للشغل.
--
-- الـcatch-all اتشال عن قصد مش بس اتصلّح العمود: هو اللي خبّى الغلطة تلات أسابيع.
-- لو الاستعلام وقع تاني، الأحسن الفحص الاستباقي يقع بصوت ويبان، من إن حارس الفلوس
-- يكدب بصمت ويرجّع "صفر".

create or replace function public.agent_brain_cost_guard()
returns integer language plpgsql security definer set search_path = public as $$
declare
  v_month_start date := date_trunc('month', now())::date;
  v_used bigint;
  v_cap constant bigint := 50_000_000; -- ٥٠ مليون توكن/شهر ≈ حد أمان مالي
begin
  select coalesce(sum(input_tokens + output_tokens), 0) into v_used
    from public.zad_brain_runs
    where started_at >= v_month_start;
  return least(100, (v_used * 100 / v_cap)::int);
end;
$$;

revoke execute on function public.agent_brain_cost_guard() from public, anon, authenticated;

comment on function public.agent_brain_cost_guard() is
  'نسبة استهلاك سقف التوكنز الشهري (0-100) من zad_brain_runs.started_at. فوق ٩٥٪ الروتينات الاستباقية بتتخنق. من غير catch-all عن قصد — الفشل لازم يبان.';

-- حارس داخل الميجريشن نفسها: plpgsql بيحلّ أسماء الأعمدة وقت التنفيذ مش وقت
-- الإنشاء، فـ`create function` لوحده كان هيعدّي بعمود غلط. النداء ده بيخلّي أي عمود
-- غلط يوقّع الـdeploy في CI بدل ما يوصل للإنتاج.
do $$
begin
  perform public.agent_brain_cost_guard();
end;
$$;
