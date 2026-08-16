-- =====================================================
-- إقفال جداول لوحة المراقبة على المطوّرين المصرّح لهم بدل أي مستخدم مسجّل.
--
-- الست جداول دي اتعملوا بسياسة `USING (auth.role() = 'authenticated')`، ودي بتشمل
-- **كل مستخدم في تطبيق زاد**، مش المطوّر. أي عميل عنده حساب في التطبيق كان يقدر يقرا
-- الجداول دي بمفتاح anon العادي.
--
-- أخطرهم `agent_logs`: بيتكتب فيه `payload: { input, output }` بتاع كل نداء AI —
-- والـinput ده هو البرومبت، اللي جواه ميزانية العميل ومعاملاته ومخزونه وأدويته. يعني
-- أي عميل كان يقدر يقرا البيانات المالية لكل العملاء التانيين. التسريب كان شغّال فعليًا.
--
-- التصميم الصح كان موجود بالفعل في `dashboard_admins` (migration 20260814030000)
-- ومطبّق صح على `agent_actions` عبر إيدج فانكشن — بس الست جداول دول فضلوا على السياسة
-- المفتوحة. الترقيع هنا بيوصّلهم بنفس القايمة.
--
-- `brain_notes`/`note_links`/`workflows`/`nodes`/`edges` مكانتش تسريب مالي، بس كانت
-- كتابة مفتوحة: أي مستخدم يقدر يعدّل أو يمسح محتوى لوحة المراقبة.
-- =====================================================

-- `dashboard_admins` مالهاش أي policy عن قصد (مفيش دور بيقراها مباشرة)، فالفحص لازم
-- يبقى SECURITY DEFINER. الدالة مابتاخدش أي مدخل من المستخدم وبترجّع بوليان عن
-- **المنادي نفسه** بس — فمفيش سطح هجوم في إتاحتها للمسجّلين.
CREATE OR REPLACE FUNCTION is_dashboard_admin()
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM dashboard_admins WHERE user_id = auth.uid()
  );
$$;

REVOKE ALL ON FUNCTION is_dashboard_admin() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION is_dashboard_admin() TO authenticated, service_role;

-- ── agent_logs — التسريب الحقيقي ──────────────────────────────────────────────
-- قراءة للمطوّرين بس. الكتابة بتيجي من الإيدج فانكشن بـ service_role اللي بيتخطى RLS
-- أصلاً، فمش محتاجة policy.
DROP POLICY IF EXISTS "agent_logs_select_authenticated" ON agent_logs;
DROP POLICY IF EXISTS "agent_logs_select_admin" ON agent_logs;
CREATE POLICY "agent_logs_select_admin"
    ON agent_logs FOR SELECT
    USING (is_dashboard_admin());

-- ── جداول تصميم اللوحة ────────────────────────────────────────────────────────
DROP POLICY IF EXISTS "brain_notes_select_authenticated" ON brain_notes;
DROP POLICY IF EXISTS "brain_notes_write_authenticated" ON brain_notes;
DROP POLICY IF EXISTS "brain_notes_all_admin" ON brain_notes;
CREATE POLICY "brain_notes_all_admin"
    ON brain_notes FOR ALL
    USING (is_dashboard_admin()) WITH CHECK (is_dashboard_admin());

DROP POLICY IF EXISTS "note_links_select_authenticated" ON note_links;
DROP POLICY IF EXISTS "note_links_write_authenticated" ON note_links;
DROP POLICY IF EXISTS "note_links_all_admin" ON note_links;
CREATE POLICY "note_links_all_admin"
    ON note_links FOR ALL
    USING (is_dashboard_admin()) WITH CHECK (is_dashboard_admin());

DROP POLICY IF EXISTS "workflows_select_authenticated" ON workflows;
DROP POLICY IF EXISTS "workflows_write_authenticated" ON workflows;
DROP POLICY IF EXISTS "workflows_all_admin" ON workflows;
CREATE POLICY "workflows_all_admin"
    ON workflows FOR ALL
    USING (is_dashboard_admin()) WITH CHECK (is_dashboard_admin());

DROP POLICY IF EXISTS "nodes_select_authenticated" ON nodes;
DROP POLICY IF EXISTS "nodes_write_authenticated" ON nodes;
DROP POLICY IF EXISTS "nodes_all_admin" ON nodes;
CREATE POLICY "nodes_all_admin"
    ON nodes FOR ALL
    USING (is_dashboard_admin()) WITH CHECK (is_dashboard_admin());

DROP POLICY IF EXISTS "edges_select_authenticated" ON edges;
DROP POLICY IF EXISTS "edges_write_authenticated" ON edges;
DROP POLICY IF EXISTS "edges_all_admin" ON edges;
CREATE POLICY "edges_all_admin"
    ON edges FOR ALL
    USING (is_dashboard_admin()) WITH CHECK (is_dashboard_admin());

COMMENT ON FUNCTION is_dashboard_admin() IS
    'True when the calling user is in dashboard_admins. Used by the observability dashboard tables, whose earlier policies granted every authenticated Zad app user access — including read access to agent_logs, which carries other customers financial data inside AI prompts.';
