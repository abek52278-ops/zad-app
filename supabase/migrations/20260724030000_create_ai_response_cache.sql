-- =====================================================
-- ai_response_cache: server-side short-TTL cache for AI actions where the same
-- normalized input genuinely produces the same answer (meal_suggestions,
-- grocery_suggestions, estimate_price in zad-core-intelligence). cache_key
-- already encodes every input that affects the answer (including dialect for
-- the two dialect-prefixed actions), so this is global/shared data, not
-- user-scoped — same RLS shape as market_price_cache: read-only for
-- authenticated clients, writes only via the edge function's service-role
-- client (bypasses RLS).
-- =====================================================

CREATE TABLE IF NOT EXISTS ai_response_cache (
  cache_key TEXT PRIMARY KEY,
  action TEXT NOT NULL,
  response JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_ai_response_cache_action ON ai_response_cache (action);

ALTER TABLE ai_response_cache ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "authenticated_read_ai_response_cache" ON ai_response_cache;
CREATE POLICY "authenticated_read_ai_response_cache" ON ai_response_cache
  FOR SELECT
  USING (auth.role() = 'authenticated');
