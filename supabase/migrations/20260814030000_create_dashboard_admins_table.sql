-- =====================================================
-- dashboard_admins — allowlist of Supabase Auth user ids permitted to read
-- cross-user real agent_actions data through zad-core-intelligence's new
-- admin_recent_activity action (Zad Brain Observability dashboard, "مراقبة
-- حية"). Deliberately has NO RLS policies at all: no role (anon,
-- authenticated) can read or write this table directly — only the
-- service_role client inside the edge function can, since RLS is bypassed
-- for service_role. This is what keeps agent_actions (real customers'
-- financial tool-call history) from being reachable by any authenticated
-- Zad app user, not just the developer.
-- =====================================================

CREATE TABLE IF NOT EXISTS dashboard_admins (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

ALTER TABLE dashboard_admins ENABLE ROW LEVEL SECURITY;

-- Seed: the developer's own dashboard login (abek52278@gmail.com), the only
-- account that has actually signed into the Zad Brain Observability dashboard.
INSERT INTO dashboard_admins (user_id)
VALUES ('20a420a9-5fab-42d5-910a-47fa9b37cb1d')
ON CONFLICT (user_id) DO NOTHING;
