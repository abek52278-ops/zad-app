-- "ضبط إعدادات البلد والعملة" kept showing after the country and currency were set.
--
-- The edge function was not failing to read the profile. It read it correctly on
-- 2026-08-10, when country and currency really were null, and wrote an insight saying so.
-- The profile was filled in later, and nothing retracts an insight once the condition it
-- describes stops being true. The row is a fact frozen on the day it was written, and it
-- stays `pending` until someone dismisses it by hand.
--
-- That is the general defect and this migration only closes the specific case, because
-- "an insight should expire when its premise expires" needs a per-kind answer — most
-- insights describe something that happened, which stays true, rather than a condition
-- that can be satisfied. This one is unambiguously the satisfiable sort: the moment
-- zad_users has both fields, the card has nothing left to ask for.

-- Retire the ones already sitting in customers' feeds whose premise is now false.
update public.zad_insights i
set status = 'acted', updated_at = now()
where i.status = 'pending'
  and i.title = 'ضبط إعدادات البلد والعملة'
  and exists (
    select 1 from public.zad_users u
    where u.id = i.user_id
      and nullif(btrim(coalesce(u.country, '')), '') is not null
      and nullif(btrim(coalesce(u.currency, '')), '') is not null
  );

/**
 * وبعدين خليها تتقفل لوحدها. أول ما العميل يحدد البلد والعملة، الكارت اللي بيطلب منه
 * يحددهم مالوش لازمة — ومحتاجش ينتظر دورة تحليل جاية ولا العميل يقفله بإيده.
 */
create or replace function public.zad_retire_market_insight()
returns trigger language plpgsql security definer set search_path = public as $$
begin
  if nullif(btrim(coalesce(new.country, '')), '') is not null
     and nullif(btrim(coalesce(new.currency, '')), '') is not null then
    update public.zad_insights
    set status = 'acted', updated_at = now()
    where user_id = new.id
      and status = 'pending'
      and title = 'ضبط إعدادات البلد والعملة';
  end if;
  return new;
end;
$$;

drop trigger if exists zad_users_retire_market_insight on public.zad_users;
create trigger zad_users_retire_market_insight
  after insert or update of country, currency on public.zad_users
  for each row execute function public.zad_retire_market_insight();
