-- =====================================================
-- zad_memory_links — الروابط بين ملاحظات العقل عن العميل.
--
-- `zad_memory` بيخزّن ملاحظات منفصلة، وكل ملاحظة بتتقوّى لوحدها عبر `evidence_count`.
-- اللي مفيش ليه مكان هو **العلاقة** بين ملاحظتين — «الصرف على المطاعم» مرتبط بـ«آخر
-- دورة الراتب». من غير الروابط دي الذاكرة قايمة مسطّحة، مش شبكة بتبني مسارات.
--
-- ليه جدول جديد مش `nodes`/`edges` الموجودين:
-- دول بتوع مصمّم الـworkflow في لوحة المراقبة (`dashboard/`) — مفتاحهم `workflow_id`،
-- وفيهم `position` بشكل React Flow، والأهم إن RLS بتاعتهم
-- (`USING (auth.role() = 'authenticated')`) بتدّي أي مستخدم مسجّل قراءة وكتابة على كل
-- الصفوف. ذاكرة عميل في جدول زي ده معناها إن كل عميل يقرا ذاكرة كل العملاء. الشكل
-- مختلف والحدود الأمنية مختلفة، فالجدولين مايتخلطوش.
-- =====================================================

CREATE TABLE IF NOT EXISTS zad_memory_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    from_id UUID NOT NULL REFERENCES zad_memory(id) ON DELETE CASCADE,
    to_id UUID NOT NULL REFERENCES zad_memory(id) ON DELETE CASCADE,
    -- نوع العلاقة. محصور عمداً: نوع حر معناه إن كل تشغيلة تخترع مسمّى جديد لنفس
    -- العلاقة، والشبكة تبقى ضوضاء مش بنية.
    relation TEXT NOT NULL CHECK (relation IN ('leads_to', 'co_occurs', 'explains', 'contradicts')),
    strength REAL NOT NULL DEFAULT 0.5 CHECK (strength >= 0 AND strength <= 1),
    -- نفس منطق zad_memory.evidence_count: الرابط بيتقوّى بالتكرار مش بيتكرر كصف جديد.
    evidence_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- ملاحظة مش بتترابط بنفسها، ونفس العلاقة بين نفس الملاحظتين صف واحد بس.
    CONSTRAINT zad_memory_links_no_self CHECK (from_id <> to_id),
    CONSTRAINT zad_memory_links_unique UNIQUE (user_id, from_id, to_id, relation)
);

CREATE INDEX IF NOT EXISTS zad_memory_links_user_idx ON zad_memory_links (user_id);
CREATE INDEX IF NOT EXISTS zad_memory_links_from_idx ON zad_memory_links (from_id);
CREATE INDEX IF NOT EXISTS zad_memory_links_to_idx ON zad_memory_links (to_id);

ALTER TABLE zad_memory_links ENABLE ROW LEVEL SECURITY;

-- العميل يقرا روابطه هو بس. الكتابة للعقل (service_role) لوحده — نفس تقسيم
-- الصلاحيات بتاع باقي جداول الوكيل بعد 20260813190358.
DROP POLICY IF EXISTS "zad_memory_links_select_own" ON zad_memory_links;
CREATE POLICY "zad_memory_links_select_own"
    ON zad_memory_links FOR SELECT
    USING (auth.uid() = user_id);

COMMENT ON TABLE zad_memory_links IS
    'Relations between a user''s zad_memory notes. Written only by zad-brain via zad_memory_link_upsert(); users read their own. Separate from nodes/edges, which belong to the observability dashboard workflow designer and are readable by every authenticated user.';

-- =====================================================
-- zad_memory_link_upsert — نفس عقد zad_memory_upsert بالظبط: تكرار نفس الرابط
-- بيزوّد الدليل ويرفع القوة، مش بيعمل صف جديد.
--
-- بيتحقق إن **الملاحظتين** بتوع نفس المستخدم قبل أي كتابة. من غير الفحص ده، تشغيلة
-- العقل (اللي شغالة بـ service_role وبتتخطى RLS) تقدر تربط ملاحظة عميل بملاحظة عميل
-- تاني لو الموديل هلوس بـ id غلط.
-- =====================================================
CREATE OR REPLACE FUNCTION zad_memory_link_upsert(
    p_user UUID,
    p_from UUID,
    p_to UUID,
    p_relation TEXT,
    p_strength REAL DEFAULT 0.5
) RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_id UUID;
    v_owned INTEGER;
BEGIN
    IF p_from = p_to THEN
        RAISE EXCEPTION 'zad_memory_link_upsert: from and to are the same note';
    END IF;

    SELECT COUNT(*) INTO v_owned
    FROM zad_memory
    WHERE id IN (p_from, p_to) AND user_id = p_user;

    IF v_owned <> 2 THEN
        RAISE EXCEPTION 'zad_memory_link_upsert: both notes must belong to the user';
    END IF;

    INSERT INTO zad_memory_links (user_id, from_id, to_id, relation, strength)
    VALUES (p_user, p_from, p_to, p_relation, LEAST(GREATEST(p_strength, 0), 1))
    ON CONFLICT (user_id, from_id, to_id, relation) DO UPDATE
        SET evidence_count = zad_memory_links.evidence_count + 1,
            -- القوة بتقرّب من ١ مع التكرار من غير ما توصلها: نفس فكرة الثقة اللي
            -- بتتراكم، بدون يقين مطلق من تكرار مش دليل قاطع.
            strength = LEAST(1.0, zad_memory_links.strength + (1.0 - zad_memory_links.strength) * 0.25),
            updated_at = NOW()
    RETURNING id INTO v_id;

    RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION zad_memory_link_upsert(UUID, UUID, UUID, TEXT, REAL) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION zad_memory_link_upsert(UUID, UUID, UUID, TEXT, REAL) TO service_role;
