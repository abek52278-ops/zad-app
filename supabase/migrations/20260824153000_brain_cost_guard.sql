
-- 20260824153000 — حارس الكلفة: سقف توكنز شهري إجمالي للعقل.
-- agent_brain_cost_guard يرجع نسبة الاستهلاك من السقف الشهري. فوق ٨٠٪ → الروتينات
-- الاستباقية تقلّل وتيرة التشغيل (بتتشيك عليه في بداية scan).
create or replace function public.agent_brain_cost_guard()
returns integer language plpgsql security definer set search_path = public as $$
declare
  v_month_start date := date_trunc('month', now())::date;
  v_used bigint;
  v_cap constant bigint := 50_000_000; -- ٥٠ مليون توكن/شهر ≈ حد أمان مالي
begin
  select coalesce(sum(input_tokens + output_tokens), 0) into v_used
    from public.zad_brain_runs
    where created_at >= v_month_start;
  return least(100, (v_used * 100 / v_cap)::int);
exception when others then return 0;
end;
$$;

revoke execute on function public.agent_brain_cost_guard() from public, anon, authenticated;

comment on function public.agent_brain_cost_guard() is
  'نسبة استهلاك سقف التوكنز الشهري (0-100). فوق ٨٠٪ العقل بيقلّل تشغيلات الاستباقية تلقائياً.';
