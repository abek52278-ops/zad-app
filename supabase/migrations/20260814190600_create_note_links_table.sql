-- =====================================================
-- note_links — real AI-inferred relationships between brain_notes
-- (e.g. "يعتمد على", "يكمل", "يناقض"), distinct from the shallow
-- shared-tag clustering the dashboard used before. Populated when a
-- new note is added: the extraction call is shown existing note
-- titles and asked which ones the new note actually relates to and how.
-- =====================================================

CREATE TABLE IF NOT EXISTS note_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_id UUID NOT NULL REFERENCES brain_notes(id) ON DELETE CASCADE,
    target_id UUID NOT NULL REFERENCES brain_notes(id) ON DELETE CASCADE,
    relation TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE (source_id, target_id)
);

CREATE INDEX IF NOT EXISTS note_links_source_idx ON note_links (source_id);
CREATE INDEX IF NOT EXISTS note_links_target_idx ON note_links (target_id);

ALTER TABLE note_links ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "note_links_select_authenticated" ON note_links;
CREATE POLICY "note_links_select_authenticated"
    ON note_links FOR SELECT
    USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "note_links_write_authenticated" ON note_links;
CREATE POLICY "note_links_write_authenticated"
    ON note_links FOR ALL
    USING (auth.role() = 'authenticated')
    WITH CHECK (auth.role() = 'authenticated');

ALTER PUBLICATION supabase_realtime ADD TABLE note_links;
