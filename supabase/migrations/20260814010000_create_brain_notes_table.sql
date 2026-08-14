-- =====================================================
-- Brain Notes — free-text notes/documents dumped into the dashboard's
-- "Brain" tab (dashboard/src/components/BrainView.tsx). AI extracts a
-- title/category/tags from each note; the dashboard renders them as a
-- radial knowledge graph (category -> notes, shared-tag links between
-- notes). This is a personal project-tracking tool for the developer,
-- not part of the Zad Android app or its family finance data.
-- =====================================================

CREATE TABLE IF NOT EXISTS brain_notes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content TEXT NOT NULL,
    title TEXT,
    category TEXT,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS brain_notes_category_idx ON brain_notes (category);
CREATE INDEX IF NOT EXISTS brain_notes_created_at_idx ON brain_notes (created_at DESC);

ALTER TABLE brain_notes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "brain_notes_select_authenticated" ON brain_notes;
CREATE POLICY "brain_notes_select_authenticated"
    ON brain_notes FOR SELECT
    USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "brain_notes_write_authenticated" ON brain_notes;
CREATE POLICY "brain_notes_write_authenticated"
    ON brain_notes FOR ALL
    USING (auth.role() = 'authenticated')
    WITH CHECK (auth.role() = 'authenticated');

ALTER PUBLICATION supabase_realtime ADD TABLE brain_notes;
