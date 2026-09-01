-- بند 31.6 — متخصص أساسي + استشاري تانٍ. routeSpecialists() في specialists.ts بترجّع
-- تاني أعلى نقاط لما رسالة بتمس نطاقين مع بعض ("أطبخ إيه بـ٥٠ جنيه؟" = مطبخ + فلوس).
-- عمود جديد نفس نمط specialist (20260822150000) — nullable لأن مش كل رسالة عندها
-- استشاري، وبنفس الخمسة قيم الفعلية (general مالهاش استشاري، فمش بيتكتب أصلاً).
alter table public.zad_brain_runs
  add column if not exists specialist_secondary text;

alter table public.zad_brain_runs drop constraint if exists zad_brain_runs_specialist_secondary_check;
alter table public.zad_brain_runs
  add constraint zad_brain_runs_specialist_secondary_check
  check (specialist_secondary is null or specialist_secondary = any (
    array['finance'::text, 'pantry'::text, 'pharmacy'::text, 'family'::text, 'home'::text]
  ));
