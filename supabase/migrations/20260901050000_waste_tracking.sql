-- رصد الهدر: صنف اتشترى وماتاكلش قبل ما صلاحيته تنتهي. الإشارة الوحيدة الموثوقة المتاحة
-- عندنا هي: الكمية نزلت لصفر (استهلاك عادي، عبر consumeInventoryItem) أو الصف اتمسح
-- (حذف يدوي)، وفي اللحظة دي expiry_date كان **فات بالفعل**. لو الكمية نزلت لصفر قبل
-- الصلاحية، ده استهلاك طبيعي مش هدر — الـtrigger بيفرّق بين الحالتين صراحةً.
--
-- مفيش تقدير تكلفة (estimated_value) — zad_inventory مالهاش عمود سعر، وأي تخمين سعر هنا
-- هيبقى رقم مخترع بالظبط زي المفهوم اللي اتفادينا بيه detect_subscriptions فوق. العدّ نفسه
-- (كام صنف، كام مرة) حقيقي ومقاس، والسعر مش.
create table if not exists public.zad_waste_log (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  item_name text not null,
  quantity numeric not null,
  unit text,
  logged_at timestamptz not null default now()
);

create index if not exists idx_zad_waste_log_user on public.zad_waste_log(user_id, logged_at desc);

alter table public.zad_waste_log enable row level security;

create policy "user_own_waste_log" on public.zad_waste_log
  for select using (auth.uid() = user_id);
-- الكتابة سيرفر-سايد بس (الـtrigger تحت، SECURITY DEFINER) — مفيش سبب العميل يكتب هنا
-- مباشرة، ده أثر مش إدخال يدوي.

create or replace function public.zad_log_inventory_waste()
returns trigger
language plpgsql
security definer
set search_path to 'public'
as $function$
declare
  v_expiry date;
  v_was_expired boolean;
begin
  if TG_OP = 'DELETE' then
    if OLD.quantity <= 0 or OLD.expiry_date is null then
      return OLD;
    end if;
    v_expiry := zad_try_date(OLD.expiry_date);
    if v_expiry is not null and v_expiry < current_date then
      insert into public.zad_waste_log (user_id, item_name, quantity, unit)
      values (OLD.user_id, OLD.item_name, OLD.quantity, OLD.unit);
    end if;
    return OLD;
  end if;

  -- UPDATE: بس الانتقال لصفر (مش كل تعديل) وبس لو كان فيه كمية فعلية اتلغت.
  if NEW.quantity = 0 and OLD.quantity > 0 and OLD.expiry_date is not null then
    v_expiry := zad_try_date(OLD.expiry_date);
    if v_expiry is not null and v_expiry < current_date then
      insert into public.zad_waste_log (user_id, item_name, quantity, unit)
      values (OLD.user_id, OLD.item_name, OLD.quantity, OLD.unit);
    end if;
  end if;
  return NEW;
end;
$function$;

drop trigger if exists trigger_log_inventory_waste on public.zad_inventory;
create trigger trigger_log_inventory_waste
  before update or delete on public.zad_inventory
  for each row execute function public.zad_log_inventory_waste();
