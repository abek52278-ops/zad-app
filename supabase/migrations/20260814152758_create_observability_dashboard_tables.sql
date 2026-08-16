-- =====================================================
-- Zad Brain Observability Dashboard — schema for the standalone
-- React Flow dashboard (dashboard/) that visualizes the agent
-- graph and tails live AI activity from zad-core-intelligence.
-- =====================================================

-- 1. workflows — versioned snapshots of the agent graph (design mode)
CREATE TABLE IF NOT EXISTS workflows (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE workflows ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "workflows_select_authenticated" ON workflows;
CREATE POLICY "workflows_select_authenticated"
    ON workflows FOR SELECT
    USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "workflows_write_authenticated" ON workflows;
CREATE POLICY "workflows_write_authenticated"
    ON workflows FOR ALL
    USING (auth.role() = 'authenticated')
    WITH CHECK (auth.role() = 'authenticated');

-- 2. nodes — agents/tools/roles belonging to a workflow version
CREATE TABLE IF NOT EXISTS nodes (
    id TEXT NOT NULL,
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    type TEXT NOT NULL DEFAULT 'agent',
    position JSONB NOT NULL DEFAULT '{"x": 0, "y": 0}'::jsonb,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (workflow_id, id)
);

ALTER TABLE nodes ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "nodes_select_authenticated" ON nodes;
CREATE POLICY "nodes_select_authenticated"
    ON nodes FOR SELECT
    USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "nodes_write_authenticated" ON nodes;
CREATE POLICY "nodes_write_authenticated"
    ON nodes FOR ALL
    USING (auth.role() = 'authenticated')
    WITH CHECK (auth.role() = 'authenticated');

-- 3. edges — data/decision flow between nodes in a workflow version
CREATE TABLE IF NOT EXISTS edges (
    id TEXT NOT NULL,
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    source TEXT NOT NULL,
    target TEXT NOT NULL,
    data JSONB NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (workflow_id, id)
);

ALTER TABLE edges ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "edges_select_authenticated" ON edges;
CREATE POLICY "edges_select_authenticated"
    ON edges FOR SELECT
    USING (auth.role() = 'authenticated');

DROP POLICY IF EXISTS "edges_write_authenticated" ON edges;
CREATE POLICY "edges_write_authenticated"
    ON edges FOR ALL
    USING (auth.role() = 'authenticated')
    WITH CHECK (auth.role() = 'authenticated');

-- 4. agent_logs — one row per AI call made by zad-core-intelligence (monitoring mode).
-- Written only by the edge function via the service_role key (RLS is bypassed for
-- service_role, so there is deliberately no INSERT policy for other roles here).
CREATE TABLE IF NOT EXISTS agent_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    "timestamp" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    agent_name TEXT NOT NULL,
    tool_used TEXT,
    payload JSONB,
    status TEXT NOT NULL DEFAULT 'success' CHECK (status IN ('success', 'warning', 'error')),
    duration_ms INTEGER
);

CREATE INDEX IF NOT EXISTS agent_logs_timestamp_idx ON agent_logs ("timestamp" DESC);
CREATE INDEX IF NOT EXISTS agent_logs_agent_name_idx ON agent_logs (agent_name);

ALTER TABLE agent_logs ENABLE ROW LEVEL SECURITY;

-- Read-only for the dashboard; writes come exclusively from the edge function's
-- service_role client, which bypasses RLS entirely.
DROP POLICY IF EXISTS "agent_logs_select_authenticated" ON agent_logs;
CREATE POLICY "agent_logs_select_authenticated"
    ON agent_logs FOR SELECT
    USING (auth.role() = 'authenticated');

-- Realtime: dashboard listens for new agent_logs rows.
ALTER PUBLICATION supabase_realtime ADD TABLE agent_logs;
