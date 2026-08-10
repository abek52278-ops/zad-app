-- Deliver a completed user-requested agent task to Telegram as well as the app.
-- The brain writes app_notifications as its channel-neutral outbox; this trigger
-- only performs delivery and never re-runs analysis or reconstructs budget data.

create or replace function public.notify_telegram_on_agent_task_notification()
returns trigger
language plpgsql
security definer
set search_path = public
as $function$
begin
  if new.title = 'زاد خلّص مهمة كنت طلبتها'
     and exists (
       select 1 from public.telegram_bindings
       where user_id = new.user_id and bound_at is not null and chat_id is not null
     ) then
    perform net.http_post(
      url := 'https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/zad-telegram-bot?job=realtime_push',
      headers := '{"Content-Type":"application/json","X-Realtime-Push-Secret":"7e78ce0aa8d2e83f67fbe48c39b5c39c17e54d32f781ccf79a1bd1000aaa7094"}'::jsonb,
      body := jsonb_build_object('user_id', new.user_id, 'title', new.title, 'body', new.message),
      timeout_milliseconds := 15000
    );
  end if;
  return new;
end;
$function$;

drop trigger if exists trigger_notify_telegram_on_agent_task_notification on public.app_notifications;
create trigger trigger_notify_telegram_on_agent_task_notification
after insert on public.app_notifications
for each row execute function public.notify_telegram_on_agent_task_notification();

