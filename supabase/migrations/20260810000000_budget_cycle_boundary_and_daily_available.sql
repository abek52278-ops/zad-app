-- The single-authority function was already deployed before the correction below.
-- Editing its original migration only fixes new databases, so production receives this
-- small compatibility wrapper as well. It preserves every field from the established
-- authority and corrects the two boundary-sensitive derived values in one place.

alter function public.zad_budget_state(uuid, text) rename to zad_budget_state_legacy;

create or replace function public.zad_budget_state(p_user uuid, p_tz text default null)
returns jsonb language plpgsql stable set search_path = public as $$
declare
  v_state jsonb;
  v_end date;
  v_items jsonb;
  v_committed numeric;
  v_obligations numeric;
  v_subscriptions numeric;
  v_available numeric;
  v_daily numeric;
  v_next jsonb;
  v_remaining numeric;
  v_days_left int;
begin
  v_state := public.zad_budget_state_legacy(p_user, p_tz);
  v_end := (v_state ->> 'cycle_end')::date;
  v_remaining := (v_state ->> 'remaining')::numeric;
  v_days_left := coalesce((v_state ->> 'days_left')::int, 0);

  -- A cycle is [start, end). A charge on payday belongs to the next cycle; keeping it
  -- reserved in the closing cycle made "المتاح" too small precisely at the boundary.
  select coalesce(jsonb_agg(item order by item ->> 'next_due'), '[]'::jsonb)
    into v_items
  from jsonb_array_elements(coalesce(v_state -> 'committed_items', '[]'::jsonb)) item
  where (item ->> 'next_due')::date < v_end;

  select coalesce(sum((item ->> 'amount')::numeric), 0),
         coalesce(sum((item ->> 'amount')::numeric) filter (where item ->> 'kind' <> 'subscription'), 0),
         coalesce(sum((item ->> 'amount')::numeric) filter (where item ->> 'kind' = 'subscription'), 0)
    into v_committed, v_obligations, v_subscriptions
  from jsonb_array_elements(v_items) item;

  v_available := case when v_remaining is null then null else v_remaining - v_committed end;
  v_daily := case
    when v_available is null then null
    when v_days_left > 0 then v_available / v_days_left
    else v_available
  end;
  v_next := v_items -> 0;

  return v_state || jsonb_build_object(
    'committed', round(v_committed, 2),
    'committed_obligations', round(v_obligations, 2),
    'committed_subscriptions', round(v_subscriptions, 2),
    'committed_items', v_items,
    'next_obligation_due', v_next,
    'available', round(v_available, 2),
    'daily_allowance_left', round(v_daily, 2)
  );
end;
$$;

comment on function public.zad_budget_state(uuid, text) is
  'Single authority for every money figure zad shows. Cycle is [start,end); daily allowance uses available after committed charges.';

grant execute on function public.zad_budget_state(uuid, text) to authenticated, service_role;
