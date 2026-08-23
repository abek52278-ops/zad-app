-- ══════════════════════════════════════════════════════════════════
-- الشبكة العصبية الأبوية — تقارير دورية عن الأبناء للأبوين
--
-- الفكرة: لما ابن (role=child) يصرف أو يتصرف، العقل يحلل سلوكه، وكل
-- أسبوع يتعمل تقرير مجمع يوصله للوالدين (role=admin) في نفس العائلة:
-- صرف الفرد، أعلى فئة، التزام بالحد اليومي، ومقارنة بالأسبوع اللي فات.
--
-- الخصوصية: التقرير **مجمع** (أرقام واتجاهات) مش معاملات مفصلة —
-- الوالد يشوف "صرف ٤٠٪ أكثر" مش "اشترى إيه بالظبط". الأطفال فوق ١٣
-- يستاهلو خصوصية، والثقة أهم من المراقبة.
-- ══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS zad_parent_digests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL,
    parent_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    child_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    child_alias TEXT,
    -- أرقام مجمعة للأسبوع
    week_start DATE NOT NULL,
    total_spent NUMERIC NOT NULL DEFAULT 0,
    tx_count INT NOT NULL DEFAULT 0,
    top_category TEXT,
    delta_vs_prev_pct NUMERIC,            -- +30 يعني صرف أكبر ٣٠٪
    daily_limit_exceeded INT NOT NULL DEFAULT 0,  -- مرات تجاوز الحد اليومي
    tasks_completed INT NOT NULL DEFAULT 0,
    -- ملخص نصي جاهز من العقل (عربي، بلهجة السوق)
    summary_text TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(parent_user_id, child_user_id, week_start)
);

CREATE INDEX IF NOT EXISTS idx_parent_digests_parent
    ON zad_parent_digests(parent_user_id, created_at DESC);

ALTER TABLE zad_parent_digests ENABLE ROW LEVEL SECURITY;

-- الوالد يشوف تقارير عيلته هو بس
CREATE POLICY parent_reads_own_digests ON zad_parent_digests
    FOR SELECT USING (parent_user_id = auth.uid());

-- الكتابة من service_role فقط (الـ cron/edge functions)
