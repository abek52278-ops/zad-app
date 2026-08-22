-- المرحلة ٣: تتبّع الوكلاء المتخصصين في حلقة المحادثة.
-- كل لفة agent_turn بيتسجل فيها الوكيل اللي اتوجّه لها الرسالة (finance/pantry/
-- pharmacy/family). NULL = وكيل عام أو لفة تحليل خلفي مش محادثة.
alter table public.zad_brain_runs
  add column if not exists specialist text;

do $$
begin
  if exists (
    select 1 from pg_constraint where conname = 'zad_brain_runs_specialist_check'
  ) then
    return;
  end if;
  alter table public.zad_brain_runs
    add constraint zad_brain_runs_specialist_check
    check (specialist in ('finance', 'pantry', 'pharmacy', 'family'));
end $$;
