-- ═══════════════════════════════════════════════════════════════════════════
-- agent_usage — عداد يومي لكل مستخدم لاستخدام العقل عبر قناة المحادثة (agent_turn).
--
-- ليه صف يومي مجمّع، مش صف لكل نداء؟ الهدف هنا سقف/حماية تكلفة، مش تدقيق تفصيلي —
-- ده دور agent_actions (زي ما اتبنى في W1) و zad_brain_runs الموجود بالفعل. صف واحد
-- لكل (user_id, usage_date) upsert بيدي إجابة "هل العميل ده وصل لسقفه النهاردة؟" بقراءة
-- صف واحد، من غير ما نحتاج aggregate على آلاف الصفوف كل نداء.
-- ═══════════════════════════════════════════════════════════════════════════

create table if not exists public.agent_usage (
  user_id uuid not null references auth.users(id) on delete cascade,
  usage_date date not null default (now() at time zone 'utc')::date,
  request_count integer not null default 0,
  input_tokens integer not null default 0,
  output_tokens integer not null default 0,
  updated_at timestamptz not null default now(),
  primary key (user_id, usage_date)
);

alter table public.agent_usage enable row level security;

drop policy if exists "user_reads_own_agent_usage" on public.agent_usage;
create policy "user_reads_own_agent_usage" on public.agent_usage
  for select using (auth.uid() = user_id);

comment on table public.agent_usage is
  'صف يومي مجمّع لكل مستخدم — عدد الطلبات وتوكنز agent_turn. مصدر سقف الاستخدام اليومي في zad-brain.';

-- ═══════════════════════════════════════════════════════════════════════════
-- zad_agent_usage_record — بيسجّل نداء واحد ويرجّع إجمالي اليوم بعد التسجيل، في نفس
-- الاستعلام (upsert...returning) عشان مفيش سباق بين "اقرا" و"اكتب" لو نداءين وصلوا
-- في نفس اللحظة بالظبط.
-- ═══════════════════════════════════════════════════════════════════════════
create or replace function public.zad_agent_usage_record(
  p_user uuid, p_input_tokens integer, p_output_tokens integer
) returns table(request_count integer, input_tokens integer, output_tokens integer)
language plpgsql
security definer
set search_path = public
as $$
begin
  return query
  insert into public.agent_usage as u (user_id, usage_date, request_count, input_tokens, output_tokens)
    values (p_user, (now() at time zone 'utc')::date, 1, greatest(p_input_tokens, 0), greatest(p_output_tokens, 0))
  on conflict (user_id, usage_date) do update set
    request_count = u.request_count + 1,
    input_tokens = u.input_tokens + greatest(p_input_tokens, 0),
    output_tokens = u.output_tokens + greatest(p_output_tokens, 0),
    updated_at = now()
  returning u.request_count, u.input_tokens, u.output_tokens;
end;
$$;

revoke all on function public.zad_agent_usage_record(uuid, integer, integer) from public;
grant execute on function public.zad_agent_usage_record(uuid, integer, integer) to service_role;

comment on function public.zad_agent_usage_record(uuid, integer, integer) is
  'يسجّل نداء agent_turn واحد ويرجّع إجمالي اليوم الحالي (upsert atomic). سيرفر-سايد بس.';
