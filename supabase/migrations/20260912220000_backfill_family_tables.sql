-- ترميم انحراف السكيما: ٨ جداول حيّة في المشروع البعيد ومالهاش أي `create table` في الريبو.
--
-- المسح (2026-09-12): `family_groups`, `family_members`, `app_notifications`,
-- `chat_messages`, `family_chores`, `family_goals`, `family_tasbiha`,
-- `shared_grocery_list` — التطبيق بيقرا ويكتب فيهم كلهم (`app_notifications` لوحده فيه
-- ١٠٨ صف حقيقي و`chat_messages` ١٠٤)، والريبو مش شايفهم خالص. يعني بيئة جديدة أو
-- استرجاع نسخة احتياطية بينتج **تطبيق مكسور**: شاشة العيلة والشات والإشعارات كلها
-- بتضرب على جداول مش موجودة. ده الاتجاه العكسي للانحراف اللي CLAUDE.md بيحذّر منه
-- (الكود موجود والجدول لأ، بدل الجدول موجود والكود لأ).
--
-- الملف ده **لقطة لحظية** مقروءة من `pg_attribute`/`pg_constraint`/`pg_policy` على
-- الإنتاج نفسه، مش إعادة تصميم: نفس الأعمدة ونفس القيم الافتراضية ونفس السياسات بالحرف،
-- حتى اللي شكله غير متسق (مثلاً `uuid_generate_v4()` في جدولين و`gen_random_uuid()` في
-- الباقي، و`due_date`/`last_tasbih_at` نصوص مش تواريخ). تغيير أي حاجة منهم هنا معناه
-- إن الريبو والإنتاج يفضلوا مختلفين — وده بالظبط اللي بنقفله.
--
-- كل حاجة `if not exists` / `drop policy if exists` عشان تعدّي بلا أثر على الإنتاج
-- (الجداول موجودة أصلاً) وتبني من الصفر في أي بيئة جديدة.

create extension if not exists "uuid-ossp";

-- ── العيلة: المجموعة والأعضاء ────────────────────────────────────────────────
create table if not exists public.family_groups (
  id uuid primary key default gen_random_uuid(),
  invite_code text not null,
  created_at timestamptz default now()
);

create table if not exists public.family_members (
  id uuid primary key default gen_random_uuid(),
  family_id uuid,
  user_id uuid,
  role text default 'member',
  alias text,
  created_at timestamptz default now(),
  balance numeric default 0,
  savings_goal numeric default 0,
  zad_id text,
  last_seen_at timestamptz default timezone('utc', now()),
  daily_limit numeric,
  weekly_limit numeric
);

-- ── الإشعارات داخل التطبيق ───────────────────────────────────────────────────
create table if not exists public.app_notifications (
  id uuid primary key default uuid_generate_v4(),
  user_id uuid not null,
  title varchar not null,
  message text not null,
  is_read boolean default false,
  created_at timestamptz default timezone('utc', now())
);

-- ── شات العيلة ───────────────────────────────────────────────────────────────
create table if not exists public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  family_id uuid,
  sender_id text,
  message text not null,
  created_at timestamptz default now(),
  message_type text default 'TEXT',
  metadata text,
  is_pinned boolean default false,
  reactions text,
  voice_url text
);

-- ── مهام البيت وأهداف العيلة ─────────────────────────────────────────────────
create table if not exists public.family_chores (
  id uuid primary key default uuid_generate_v4(),
  family_id uuid not null,
  assigned_to uuid not null,
  title text not null,
  due_date text,
  is_completed boolean not null default false,
  created_at timestamptz default timezone('utc', now()),
  reward_amount numeric default 0
);

create table if not exists public.family_goals (
  id uuid primary key default gen_random_uuid(),
  family_id uuid,
  target_amount double precision default 0.0,
  current_amount double precision default 0.0,
  month_year text,
  reward_suggestion text,
  created_at timestamptz default now()
);

-- ── التسبيحة (بستان العيلة) ──────────────────────────────────────────────────
create table if not exists public.family_tasbiha (
  id uuid primary key default gen_random_uuid(),
  family_id uuid references public.family_groups(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  tree_name text default 'بذرة',
  level integer default 1,
  score integer default 0,
  total_clicks integer default 0,
  last_tasbih_at text,
  created_at timestamptz default now(),
  tree_type text not null default 'normal',
  garden_name text not null default 'بستاني',
  is_mature boolean not null default false,
  matured_at timestamptz,
  tree_emoji text not null default '🌰',
  streak_days integer not null default 0,
  last_streak_date text
);

-- ── قايمة الشراء المشتركة ────────────────────────────────────────────────────
create table if not exists public.shared_grocery_list (
  id uuid primary key default gen_random_uuid(),
  family_id uuid,
  added_by uuid,
  item_name text,
  category text,
  is_purchased boolean default false,
  created_at timestamptz default timezone('utc', now())
);

-- ── الدالة اللي كل سياسات العيلة قايمة عليها ─────────────────────────────────
-- `get_my_family_ids()` **مستخدمة في تلات ميجريشنز موجودة ومتعرّفة في ولا واحدة منهم**
-- (`20260720010000`, `20260720231500`, `20260905140000` كلهم بيندهوها). يعني بناء من
-- الصفر كان بيقع عند أول سياسة بتستخدمها، بغض النظر عن الملف ده. متعرّفة هنا لأن ده
-- أول ملف بيعمل `family_members` اللي هي بتقرا منها. `create or replace` فالإنتاج
-- بيفضل زي ما هو بالحرف.
create or replace function public.get_my_family_ids()
returns setof uuid
language sql
stable
security definer
set search_path to 'public'
as $function$
    select family_id from family_members where user_id = auth.uid();
$function$;

-- ── RLS ──────────────────────────────────────────────────────────────────────
-- كلها متفعّل عليها RLS في الإنتاج، والنطاق العائلي بيمرّ من `get_my_family_ids()` —
-- نفس القاعدة اللي CLAUDE.md بيفرضها: الحدّ الأمني هو RLS مش فلترة في العميل، وممنوع
-- الثقة في `family_id` الجاي من الجهاز لوحده.
alter table public.family_groups       enable row level security;
alter table public.family_members      enable row level security;
alter table public.app_notifications   enable row level security;
alter table public.chat_messages       enable row level security;
alter table public.family_chores       enable row level security;
alter table public.family_goals        enable row level security;
alter table public.family_tasbiha      enable row level security;
alter table public.shared_grocery_list enable row level security;

drop policy if exists family_groups_select_authenticated on public.family_groups;
create policy family_groups_select_authenticated on public.family_groups
  for select using (true);
drop policy if exists family_groups_insert_authenticated on public.family_groups;
create policy family_groups_insert_authenticated on public.family_groups
  for insert with check (true);
drop policy if exists family_groups_update_members on public.family_groups;
create policy family_groups_update_members on public.family_groups
  for update using (id in (select public.get_my_family_ids()));
drop policy if exists family_groups_delete_members on public.family_groups;
create policy family_groups_delete_members on public.family_groups
  for delete using (id in (select public.get_my_family_ids()));

drop policy if exists family_members_select on public.family_members;
create policy family_members_select on public.family_members
  for select using (
    family_id in (select public.get_my_family_ids())
    or user_id = (select auth.uid())
  );
drop policy if exists family_members_insert on public.family_members;
create policy family_members_insert on public.family_members
  for insert with check ((select auth.role()) = 'authenticated');
drop policy if exists family_members_update on public.family_members;
create policy family_members_update on public.family_members
  for update using (family_id in (select public.get_my_family_ids()));
drop policy if exists family_members_delete on public.family_members;
create policy family_members_delete on public.family_members
  for delete using (family_id in (select public.get_my_family_ids()));

drop policy if exists users_read_own_notifications on public.app_notifications;
create policy users_read_own_notifications on public.app_notifications
  for select using (user_id = (select auth.uid()));
drop policy if exists users_update_own_notifications on public.app_notifications;
create policy users_update_own_notifications on public.app_notifications
  for update using (user_id = (select auth.uid()));
-- الكتابة مسموحة لعضو العيلة كمان (العقل بيكتب إشعار لفرد تاني في نفس البيت).
drop policy if exists notify_self_or_family_member on public.app_notifications;
create policy notify_self_or_family_member on public.app_notifications
  for insert with check (
    user_id = (select auth.uid())
    or user_id in (
      select fm.user_id from public.family_members fm
      where fm.family_id in (select public.get_my_family_ids())
    )
  );

drop policy if exists chat_messages_select on public.chat_messages;
create policy chat_messages_select on public.chat_messages
  for select using (family_id in (select public.get_my_family_ids()));
drop policy if exists chat_messages_insert on public.chat_messages;
create policy chat_messages_insert on public.chat_messages
  for insert with check (family_id in (select public.get_my_family_ids()));
drop policy if exists chat_messages_update on public.chat_messages;
create policy chat_messages_update on public.chat_messages
  for update using (family_id in (select public.get_my_family_ids()));

drop policy if exists family_chores_select on public.family_chores;
create policy family_chores_select on public.family_chores
  for select using (family_id in (select public.get_my_family_ids()));
drop policy if exists family_chores_insert on public.family_chores;
create policy family_chores_insert on public.family_chores
  for insert with check (family_id in (select public.get_my_family_ids()));
drop policy if exists family_chores_update on public.family_chores;
create policy family_chores_update on public.family_chores
  for update using (family_id in (select public.get_my_family_ids()));
drop policy if exists family_chores_delete on public.family_chores;
create policy family_chores_delete on public.family_chores
  for delete using (family_id in (select public.get_my_family_ids()));

drop policy if exists family_goals_modify on public.family_goals;
create policy family_goals_modify on public.family_goals
  for all using (family_id in (select public.get_my_family_ids()));

drop policy if exists family_tasbiha_select on public.family_tasbiha;
create policy family_tasbiha_select on public.family_tasbiha
  for select using (family_id in (select public.get_my_family_ids()));
drop policy if exists family_tasbiha_insert on public.family_tasbiha;
create policy family_tasbiha_insert on public.family_tasbiha
  for insert with check (family_id in (select public.get_my_family_ids()));
drop policy if exists family_tasbiha_update on public.family_tasbiha;
create policy family_tasbiha_update on public.family_tasbiha
  for update using (family_id in (select public.get_my_family_ids()));

drop policy if exists grocery_select on public.shared_grocery_list;
create policy grocery_select on public.shared_grocery_list
  for select using (family_id in (select public.get_my_family_ids()));
drop policy if exists grocery_insert on public.shared_grocery_list;
create policy grocery_insert on public.shared_grocery_list
  for insert with check (family_id in (select public.get_my_family_ids()));
drop policy if exists grocery_update on public.shared_grocery_list;
create policy grocery_update on public.shared_grocery_list
  for update using (family_id in (select public.get_my_family_ids()));
drop policy if exists grocery_delete on public.shared_grocery_list;
create policy grocery_delete on public.shared_grocery_list
  for delete using (family_id in (select public.get_my_family_ids()));
