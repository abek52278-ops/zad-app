-- ── zad_ad_ssv_receipts — إيصالات AdMob Server-Side Verification ─────────────
--
-- AdMob بتعيد نداء الـcallback لو ردّنا اتأخر أو فشل. من غير حارس تكرار، نفس
-- المكافأة بتتشحن أكتر من مرة. transaction_id فريد من جوجل لكل مكافأة، فالقيد
-- الفريد عليه هو الحارس — والإدراج بيسبق المنح عشان السباق يتحسم في الداتابيز
-- مش في الكود.
--
-- الجدول تدقيقي كمان: بيدي سجل لكل مكافأة موثّقة من جوجل، مقابل zad_ad_grants
-- اللي بيسجّل كل منحة بما فيها اللي جاية من الكلاينت بلا إثبات مشاهدة.

create table if not exists public.zad_ad_ssv_receipts (
  transaction_id text primary key,
  user_id        uuid not null references auth.users(id) on delete cascade,
  ad_unit        text,
  reward_amount  int,
  created_at     timestamptz not null default now()
);

create index if not exists idx_ad_ssv_receipts_user
  on public.zad_ad_ssv_receipts (user_id, created_at desc);

alter table public.zad_ad_ssv_receipts enable row level security;

-- بيتكتب من الدالة بمفتاح service-role بس (اللي بيتخطى RLS). العميل يقرا بتاعه
-- عشان الشفافية؛ مفيش insert/update/delete لأي حد — الشحن مايتزوّرش من الكلاينت.
create policy "own ssv receipts select" on public.zad_ad_ssv_receipts
  for select using ((select auth.uid()) = user_id);

comment on table public.zad_ad_ssv_receipts is
  'إيصالات مكافآت AdMob الموثّقة بتوقيع جوجل. transaction_id فريد = حارس التكرار.';
