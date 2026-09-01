-- ═══════════════════════════════════════════════════════════
-- بند 34.2 — ذاكرة عائلية: حقائق مشتركة (حساسية الولد، عيد ميلاد، عادة رمضان) يشوفها
-- كل عقول العائلة، والحقائق الفردية تفضل خاصة. نفس نمط zad_inventory.family_id
-- (family_groups، RLS عبر get_my_family_ids()) اللي 34.1 استخدمه بالظبط.
--
-- family_id نفسها اختيارية (nullable) — الملاحظة العادية تفضل خاصة زي ما هي، ومفيش
-- تغيير في السلوك الافتراضي. العميل (أو الموديل نيابة عنه، لما العميل يقول حاجة
-- واضح إنها بتخص العيلة كلها) لازم يختار المشاركة صراحة عبر remember() الجديدة.
-- ═══════════════════════════════════════════════════════════
alter table public.zad_memory
  add column if not exists family_id uuid references public.family_groups(id) on delete set null;

create index if not exists idx_zad_memory_family_id
  on public.zad_memory(family_id) where family_id is not null;

comment on column public.zad_memory.family_id is
  'null = ملاحظة خاصة بالعميل بس (الافتراضي). لو متظبطة، باقي أفراد العيلة يقدروا يقروها (RLS) وعقل زاد لأي فرد تاني يشوفها في السياق. تتظبط عبر remember(share_with_family=true) بس، مش تلقائي.';

-- قراءة إضافية: أفراد العيلة يقدروا يقروا ملاحظات العيلة المشتركة، مش بس ملاحظاتهم هم.
-- السياسة الأصلية (user_own_memory، ALL) فاضلة زي ما هي — دي سياسة SELECT إضافية بتتجمع
-- معاها (OR)، مش بديل عنها.
drop policy if exists "family_shared_memory_select" on public.zad_memory;
create policy "family_shared_memory_select"
  on public.zad_memory for select
  using (family_id in (select get_my_family_ids()));
