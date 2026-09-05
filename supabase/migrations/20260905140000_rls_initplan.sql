-- =====================================================
-- بند BE-06 — إصلاح auth_rls_initplan على ١٩ سياسة.
--
-- المشكلة: `auth.uid()` مكتوبة عارية جوّه سياسة RLS بتتقيّم **لكل صف**. لفّها في
-- `(select auth.uid())` بتخلّي بوستجرس يقيّمها مرة واحدة كـInitPlan. ده التحويل اللي
-- سوبابيز نفسها بتوصّي بيه للّينت ده، و**الدلالة مطابقة تماماً** — نفس القيمة، نفس
-- النتيجة، فرق في خطة التنفيذ بس.
--
-- ليه الميجريشن دي متولّدة برنامجياً مش مكتوبة بالإيد:
-- كل تعريف هنا **مقروء من `pg_policies` على الداتابيز الحيّة** (2026-09-05) واتعمل عليه
-- استبدال واحد بس (`auth.uid()` → `(select auth.uid())`). مفيش سطر اتنسخ بالإيد ومفيش
-- تعبير اتعاد كتابته من الذاكرة. ده مقصود: السياسات دي هي حدود الأمان، وإعادة كتابتها
-- من تخمين كانت هتخاطر بتوسيع صلاحية من غير ما حد ياخد باله.
--
-- التعبيرات المركّبة (zad_inventory بفرعين family_id، وfamily_admin_read_pharmacy
-- بالـEXISTS/JOIN) اتنقلت حرف بحرف ما عدا الاستبدال ده.
--
-- ملاحظة: `to public` هنا مش تراخي — دي القيمة اللي السياسات شغالة بيها فعلاً على
-- المشروع، والميجريشن دي **بتحافظ على السلوك الحالي** مش بتغيّره. تغيير الأدوار قرار
-- منفصل ولازم يتاخد لوحده.
--
-- أربع سياسات كانت في تقرير الـadvisor ومش هنا، لأن جداولها اتمسحت في
-- 20260905120000/20260905130000: market_snapshot, price_alerts, user_alert_snooze,
-- zad_waste_log.
-- =====================================================

drop policy if exists "user_crud_own_agent_goals" on public.agent_goals;
create policy "user_crud_own_agent_goals"
  on public.agent_goals
  for all
  to public
  using (((select auth.uid()) = user_id))
  with check (((select auth.uid()) = user_id));

drop policy if exists "agent_logs_insert_own" on public.agent_logs;
create policy "agent_logs_insert_own"
  on public.agent_logs
  for insert
  to authenticated
  with check (((select auth.uid()) = user_id));

drop policy if exists "price_index_user_crowdsource_write" on public.price_index;
create policy "price_index_user_crowdsource_write"
  on public.price_index
  for insert
  to public
  with check (((user_id IS NULL) OR ((select auth.uid()) = user_id)));

drop policy if exists "shopping_recommendations_own" on public.shopping_recommendations;
create policy "shopping_recommendations_own"
  on public.shopping_recommendations
  for select
  to public
  using (((select auth.uid()) = user_id));

drop policy if exists "shopping_recommendations_own_update" on public.shopping_recommendations;
create policy "shopping_recommendations_own_update"
  on public.shopping_recommendations
  for update
  to public
  using (((select auth.uid()) = user_id));

drop policy if exists "user_achievements_own" on public.user_achievements;
create policy "user_achievements_own"
  on public.user_achievements
  for select
  to public
  using (((select auth.uid()) = user_id));

drop policy if exists "agent mail mark read" on public.zad_agent_messages;
create policy "agent mail mark read"
  on public.zad_agent_messages
  for update
  to public
  using (((select auth.uid()) = user_id));

drop policy if exists "agent sender insert" on public.zad_agent_messages;
create policy "agent sender insert"
  on public.zad_agent_messages
  for insert
  to public
  with check (((select auth.uid()) = user_id));

drop policy if exists "own agent mail select" on public.zad_agent_messages;
create policy "own agent mail select"
  on public.zad_agent_messages
  for select
  to public
  using (((select auth.uid()) = user_id));

drop policy if exists "fcm_tokens_own_read" on public.zad_fcm_tokens;
create policy "fcm_tokens_own_read"
  on public.zad_fcm_tokens
  for select
  to public
  using (((select auth.uid()) = user_id));

drop policy if exists "fcm_tokens_own_write" on public.zad_fcm_tokens;
create policy "fcm_tokens_own_write"
  on public.zad_fcm_tokens
  for all
  to public
  using (((select auth.uid()) = user_id))
  with check (((select auth.uid()) = user_id));

drop policy if exists "zad_inventory_delete" on public.zad_inventory;
create policy "zad_inventory_delete"
  on public.zad_inventory
  for delete
  to public
  using ((((family_id IS NULL) AND ((select auth.uid()) = user_id)) OR ((family_id IS NOT NULL) AND (family_id IN ( SELECT get_my_family_ids() AS get_my_family_ids)))));

drop policy if exists "zad_inventory_insert" on public.zad_inventory;
create policy "zad_inventory_insert"
  on public.zad_inventory
  for insert
  to public
  with check (((select auth.uid()) = user_id));

drop policy if exists "zad_inventory_select" on public.zad_inventory;
create policy "zad_inventory_select"
  on public.zad_inventory
  for select
  to public
  using ((((family_id IS NULL) AND ((select auth.uid()) = user_id)) OR ((family_id IS NOT NULL) AND (family_id IN ( SELECT get_my_family_ids() AS get_my_family_ids)))));

drop policy if exists "zad_inventory_update" on public.zad_inventory;
create policy "zad_inventory_update"
  on public.zad_inventory
  for update
  to public
  using ((((family_id IS NULL) AND ((select auth.uid()) = user_id)) OR ((family_id IS NOT NULL) AND (family_id IN ( SELECT get_my_family_ids() AS get_my_family_ids)))))
  with check ((((family_id IS NULL) AND ((select auth.uid()) = user_id)) OR ((family_id IS NOT NULL) AND (family_id IN ( SELECT get_my_family_ids() AS get_my_family_ids)))));

drop policy if exists "parent_reads_own_digests" on public.zad_parent_digests;
create policy "parent_reads_own_digests"
  on public.zad_parent_digests
  for select
  to public
  using ((parent_user_id = (select auth.uid())));

drop policy if exists "family_admin_read_pharmacy" on public.zad_pharmacy_items;
create policy "family_admin_read_pharmacy"
  on public.zad_pharmacy_items
  for select
  to public
  using ((EXISTS ( SELECT 1
   FROM (family_members fm_admin
     JOIN family_members fm_target ON ((fm_target.family_id = fm_admin.family_id)))
  WHERE ((fm_admin.user_id = (select auth.uid())) AND (fm_admin.role = 'admin'::text) AND (fm_target.user_id = zad_pharmacy_items.user_id)))));

drop policy if exists "user_own_recipe_feedback" on public.zad_recipe_feedback;
create policy "user_own_recipe_feedback"
  on public.zad_recipe_feedback
  for all
  to public
  using (((select auth.uid()) = user_id))
  with check (((select auth.uid()) = user_id));

drop policy if exists "user_own_skills" on public.zad_skills;
create policy "user_own_skills"
  on public.zad_skills
  for all
  to public
  using (((select auth.uid()) = user_id));
