-- =====================================================
-- market_price_cache: server-side 12h cache for the "Zad Live Market Ticker"
-- (fetch_live_market_prices action in zad-core-intelligence). Shared/global
-- data (commodity prices per market/region), not user-scoped — so RLS is
-- read-only for authenticated clients; writes only happen via the edge
-- function's service-role client, which bypasses RLS.
-- =====================================================

CREATE TABLE IF NOT EXISTS market_price_cache (
  market TEXT PRIMARY KEY,
  prices JSONB NOT NULL DEFAULT '[]'::jsonb,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE market_price_cache ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "authenticated_read_market_price_cache" ON market_price_cache;
CREATE POLICY "authenticated_read_market_price_cache" ON market_price_cache
  FOR SELECT
  USING (auth.role() = 'authenticated');
