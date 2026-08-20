-- A dose and its stock deduction are one fact. Keeping them in separate client/Edge
-- writes allowed retries and concurrent devices to decrement twice or lose an update.
alter table public.zad_pharmacy_items
  add column if not exists dose_carry numeric(10, 4) not null default 0
  check (dose_carry >= 0 and dose_carry < 1);

create or replace function public.zad_log_pharmacy_dose_atomic(
  p_user uuid,
  p_item uuid,
  p_scheduled_at timestamptz default null,
  p_taken_at timestamptz default now()
)
returns jsonb
language plpgsql
security invoker
set search_path = public, pg_temp
as $$
declare
  v_item public.zad_pharmacy_items%rowtype;
  v_slot timestamptz;
  v_dose_id uuid;
  v_units numeric;
  v_accumulated numeric;
  v_whole_units integer;
  v_new_quantity integer;
  v_new_carry numeric;
  v_shopping_added boolean := false;
begin
  if p_user is null or p_item is null then
    return jsonb_build_object('ok', false, 'reason', 'invalid_input');
  end if;

  if auth.uid() is distinct from p_user and coalesce(auth.role(), '') <> 'service_role' then
    raise exception 'forbidden' using errcode = '42501';
  end if;

  -- Explicit reminder slots remain exact. Ad-hoc voice/screen events share a five-minute
  -- bucket so a network retry is idempotent without blocking a later legitimate dose.
  v_slot := coalesce(
    p_scheduled_at,
    date_trunc('hour', coalesce(p_taken_at, now()))
      + floor(extract(minute from coalesce(p_taken_at, now())) / 5)::integer * interval '5 minutes'
  );

  select * into v_item
  from public.zad_pharmacy_items
  where id = p_item and user_id = p_user
  for update;

  if not found then
    return jsonb_build_object('ok', false, 'reason', 'not_found');
  end if;

  v_units := greatest(coalesce(v_item.units_per_dose, 1), 0);
  insert into public.zad_pharmacy_doses (
    user_id, item_id, scheduled_at, taken_at, status, units
  ) values (
    p_user, p_item, v_slot, coalesce(p_taken_at, now()), 'taken', v_units
  )
  on conflict (user_id, item_id, scheduled_at) where scheduled_at is not null
  do nothing
  returning id into v_dose_id;

  if v_dose_id is null then
    return jsonb_build_object(
      'ok', true,
      'duplicate', true,
      'item_id', v_item.id,
      'name', v_item.name,
      'remaining_quantity', v_item.remaining_quantity,
      'scheduled_at', v_slot
    );
  end if;

  v_accumulated := v_item.dose_carry + v_units;
  v_whole_units := floor(v_accumulated)::integer;
  v_new_quantity := greatest(0, v_item.remaining_quantity - v_whole_units);
  v_new_carry := case
    when v_new_quantity = 0 then 0
    else v_accumulated - v_whole_units
  end;

  update public.zad_pharmacy_items
  set remaining_quantity = v_new_quantity,
      dose_carry = v_new_carry
  where id = v_item.id;

  if v_new_quantity > 0
     and v_new_quantity <= ceil(greatest(coalesce(v_item.daily_dose_count, 1), 1) * v_units)
  then
    insert into public.zad_shopping_list (user_id, item_name, quantity, is_purchased)
    values (p_user, v_item.name, 1, false)
    on conflict (user_id, (lower(trim(item_name)))) where is_purchased = false
    do nothing;
    v_shopping_added := found;
  end if;

  return jsonb_build_object(
    'ok', true,
    'duplicate', false,
    'dose_id', v_dose_id,
    'item_id', v_item.id,
    'name', v_item.name,
    'unit', v_item.unit,
    'units', v_units,
    'previous_quantity', v_item.remaining_quantity,
    'remaining_quantity', v_new_quantity,
    'dose_carry', v_new_carry,
    'scheduled_at', v_slot,
    'shopping_added', v_shopping_added
  );
end;
$$;

revoke all on function public.zad_log_pharmacy_dose_atomic(uuid, uuid, timestamptz, timestamptz) from public, anon;
grant execute on function public.zad_log_pharmacy_dose_atomic(uuid, uuid, timestamptz, timestamptz) to authenticated, service_role;
