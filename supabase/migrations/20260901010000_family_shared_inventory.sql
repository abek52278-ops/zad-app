-- Task 30 — توحيد المخزون العائلي. قرار المنتج (2026-09-01): دمج كامل، قايمة
-- واحدة مشتركة لكل عيلة، بنفس شكل shared_grocery_list. المستخدم الوحيد اللي
-- مش في عيلة يفضل شغال بالظبط زي ما كان (family_id فاضي = سلوك قديم بلا تغيير).

alter table public.zad_inventory
  add column if not exists family_id uuid references public.family_groups(id) on delete set null;
  -- set null مش cascade (بعكس shared_grocery_list عمدًا): انحلال جروب العيلة
  -- مايمسحش أكل حد فعلي من مخزونه، بس بيرجّعه شخصي بس.

create index if not exists idx_zad_inventory_family on public.zad_inventory(family_id) where family_id is not null;

-- family_id بيتحدد سيرفر-سايد دايمًا — نفس مبدأ populate_and_dedupe_family_transaction
-- على zad_transactions (20260725140000): العميل مايبعتش family_id ومايتصدقش لو بعته،
-- وبيتصحح مع كل لمسة (insert أو update) مش وقت الإنشاء بس — upsert من الكاميرا أو
-- تعديل من الايجنت لازم يفضلوا متزامنين مع عضوية المستخدم الحالية.
create or replace function public.populate_family_inventory()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $function$
begin
  if new.user_id is not null then
    select family_id into new.family_id from public.family_members where user_id = new.user_id limit 1;
  else
    new.family_id := null;
  end if;
  return new;
end;
$function$;

drop trigger if exists trigger_populate_family_inventory on public.zad_inventory;
create trigger trigger_populate_family_inventory
  before insert or update on public.zad_inventory
  for each row execute function public.populate_family_inventory();

-- الانضمام لعيلة لازم يوحّد المخزون **فورًا**، مش يستنى كل صنف يتلمس عشان
-- الـtrigger فوق يشتغل عليه لوحده. بتتنده بعد نجاح joinFamilyGroup() مباشرة.
create or replace function public.zad_inventory_backfill_on_family_join(p_family uuid)
returns void
language plpgsql
security definer
set search_path to 'public'
as $function$
begin
  if auth.uid() is null then
    raise exception 'authentication required' using errcode = '42501';
  end if;
  if not exists (
    select 1 from public.family_members where user_id = auth.uid() and family_id = p_family
  ) then
    raise exception 'not_a_member';
  end if;
  update public.zad_inventory
     set family_id = p_family
   where user_id = auth.uid() and (family_id is null or family_id <> p_family);
end;
$function$;

revoke execute on function public.zad_inventory_backfill_on_family_join(uuid) from public, anon;
grant execute on function public.zad_inventory_backfill_on_family_join(uuid) to authenticated, service_role;

drop policy if exists "user_own_inventory" on public.zad_inventory;

create policy "zad_inventory_select" on public.zad_inventory for select
  using (
    (family_id is null and auth.uid() = user_id)
    or (family_id is not null and family_id in (select public.get_my_family_ids()))
  );

-- family_id نفسه بيتحدد بالـtrigger، مش هنا — الـwith check بيتأكد بس إن
-- العميل مابيضيفش صف باسم حد تاني.
create policy "zad_inventory_insert" on public.zad_inventory for insert
  with check (auth.uid() = user_id);

create policy "zad_inventory_update" on public.zad_inventory for update
  using (
    (family_id is null and auth.uid() = user_id)
    or (family_id is not null and family_id in (select public.get_my_family_ids()))
  )
  with check (
    (family_id is null and auth.uid() = user_id)
    or (family_id is not null and family_id in (select public.get_my_family_ids()))
  );

create policy "zad_inventory_delete" on public.zad_inventory for delete
  using (
    (family_id is null and auth.uid() = user_id)
    or (family_id is not null and family_id in (select public.get_my_family_ids()))
  );
