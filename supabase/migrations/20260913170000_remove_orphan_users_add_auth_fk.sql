-- 20260913170000 — مسح المستخدمين اليتامى + FK يمنع رجوعهم (قرار المستخدم 2026-09-13).
--
-- `zad_users` كان فيه ٦ صفوف مقابل ٤ في `auth.users`، ومفيش FK بينهم. الصفين
-- اليتامى اتعملوا 2026-07-12..14 (فترة التجارب). مش من `delete-account` — ده بيمسح
-- `zad_users` الأول وبعدين الحساب — غالبًا حسابات اتمسحت من الداشبورد مباشرة.
--
-- الضرر اللي عملوه: الماسح الاستباقي كان بيلف على `zad_users`، و`home_weekly_digest`
-- بيقع عليهم كل ساعة بـ23503 (`agent_tasks.user_id` عليه FK على auth.users). الفشل ده
-- كان مبلوع لحد 20260913161000، اللي بقى يتخطّاهم (`skipped_orphans`).
--
-- بياناتهم اتقاست قبل المسح (2026-09-13): family_members 10، app_notifications 4،
-- zad_inventory 2، zad_subscriptions 1، وكلها معزولة: **صفر** من عائلاتهم العشرة فيها
-- مستخدم حقيقي، و**صفر** من صفوف مخزونهم ظاهرة لمستخدم حقيقي. العائلات العشرة
-- (`family_groups`) عائلات تجربة فاضية بعد خروجهم.
--
-- كل حاجة هنا محسوبة **وقت التطبيق بشرط**، مش بـids مكتوبة: لو ظهر يتيم جديد بين
-- القياس والنشر بيتمسح هو كمان، ولو مستخدم حقيقي انضم لعائلة منهم في النص العيلة
-- دي بتفضل.

create temporary table _orphan_users on commit drop as
  select u.id
  from public.zad_users u
  where not exists (select 1 from auth.users a where a.id = u.id);

create temporary table _orphan_families on commit drop as
  select distinct fm.family_id
  from public.family_members fm
  where fm.user_id in (select id from _orphan_users)
    and not exists (
      select 1 from public.family_members r
      join auth.users a on a.id = r.user_id
      where r.family_id = fm.family_id
    );

delete from public.app_notifications where user_id in (select id from _orphan_users);
delete from public.zad_inventory     where user_id in (select id from _orphan_users);
delete from public.zad_subscriptions where user_id in (select id from _orphan_users);
delete from public.family_members    where user_id in (select id from _orphan_users);
-- بعد المخزون عشان SET NULL على zad_inventory.family_id مايلمسش صفوف لسه هتتمسح.
-- الـcascade بيشيل تسبيحة/تحديات/صناديق العائلات دي (عائلات تجربة من غير أي عضو حقيقي).
delete from public.family_groups     where id in (select family_id from _orphan_families);
delete from public.zad_users         where id in (select id from _orphan_users);

-- المنع: أي حساب يتمسح من auth.users (من الداشبورد أو غيره) بياخد صفه في zad_users
-- معاه. التسجيل مش بيتأثر: `zad_users` بيتعمل بـtrigger
-- `on_auth_user_created_provision_zad_users` **بعد** إنشاء الحساب، و`upsert` في التطبيق
-- دايمًا بـid المستخدم المسجّل.
alter table public.zad_users
  add constraint zad_users_id_fkey
  foreign key (id) references auth.users(id) on delete cascade;
