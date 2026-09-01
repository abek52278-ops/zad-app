-- zad_brain_runs_specialist_check نسي 'home' — واحد من الستة الفعليين اللي
-- routeSpecialist() في specialists.ts بيرجّعهم (finance/pantry/pharmacy/family/home/general).
-- الأثر: أي رسالة اتوجهت لـhome، recordSpecialistTrace() كان بيحاول يسجّلها ويفشل بصمت
-- (try/catch بيبتلع الخطأ عمدًا — specialists.ts:157-161)، فمفيش أي أثر لتصنيف "home"
-- في السجل خالص من أول ما القيد ده اتضاف.
alter table public.zad_brain_runs drop constraint if exists zad_brain_runs_specialist_check;
alter table public.zad_brain_runs
  add constraint zad_brain_runs_specialist_check
  check (specialist = any (array['finance'::text, 'pantry'::text, 'pharmacy'::text, 'family'::text, 'home'::text]));
