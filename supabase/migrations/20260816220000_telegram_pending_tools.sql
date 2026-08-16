-- طابور تأكيد للأدوات اللي مش log_transaction.
--
-- CONFIRM_REQUIRED_TOOLS فيه أربع أدوات: log_transaction, update_transaction,
-- delete_transaction, set_monthly_limit. تليجرام كان عنده زر تأكيد للأولانية بس؛
-- التلاتة الباقيين كانوا بيتحوّلوا لسطر نصي: "ℹ️ … — ابعتها لوحدها عشان أأكدها معاك"
-- — والعميل يكون أصلاً باعتها لوحدها، فنفس السطر يتكرر. مفيش زرار، ومفيش مخرج.
-- "امسح المعاملة دي" من تليجرام مكانتش صعبة، كانت **مستحيلة**.
--
-- الأداة ومدخلاتها بيتخزنوا هنا لأن callback_data محدود بـ٦٤ بايت، فالـ id بس هو
-- اللي بيسافر مع الزرار. نفس مبدأ telegram_pending_writes بالظبط.
--
-- مافيش check على `tool` عن قصد: القايمة الرسمية عايشة في validators.ts
-- (CONFIRM_REQUIRED_TOOLS)، وقيد هنا معناه مصدرين للحقيقة يتفرقوا عن بعض. البوت
-- بيتحقق من القايمة قبل ما يكتب الصف، والتنفيذ نفسه بيعدي على agent_confirm اللي
-- بيعيد التحقق سيرفر-سايد تاني.
create table if not exists public.telegram_pending_tools (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null references auth.users(id) on delete cascade,
  chat_id     bigint not null,
  tool        text not null,
  input       jsonb not null default '{}'::jsonb,
  summary     text not null,
  status      text not null default 'pending' check (status in ('pending', 'confirmed', 'cancelled')),
  created_at  timestamptz not null default now(),
  expires_at  timestamptz not null default now() + interval '30 minutes'
);

create index if not exists idx_telegram_pending_tools_user
  on public.telegram_pending_tools(user_id, status);

alter table public.telegram_pending_tools enable row level security;

-- المستخدم يشوف طلباته هو بس. الـ edge function (service role) بتتخطى RLS وبتتحقق
-- بنفسها عن طريق telegram_bindings — نفس ترتيب telegram_pending_writes.
create policy "user_own_telegram_pending_tool" on public.telegram_pending_tools for all
  using (auth.uid() = user_id) with check (auth.uid() = user_id);

comment on table public.telegram_pending_tools is
  'اقتراح أداة محتاج موافقة صريحة من تليجرام (غير الفلوس، اللي ليها telegram_pending_writes). بيتأكد عبر callback tx:<id> / tc:<id> وبيتنفذ من zad-brain agent_confirm.';
