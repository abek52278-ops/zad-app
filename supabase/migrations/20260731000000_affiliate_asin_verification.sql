-- =====================================================
-- FIX: Amazon affiliate links opened a 404 for every catalog product.
--
-- Root cause: the five rows seeded into `affiliate_products` carry ASINs that were
-- never checked against amazon.sa — B07G9LQJ5M, B07F2Y9Z9T, B07GHPBF3Z, B07F2XB1V5,
-- B07G9ZYX1L. They are all well-formed (10 alphanumeric chars starting "B0"), so
-- AffiliateHelper.productUrl()'s format check accepted them and built
-- `https://www.amazon.sa/dp/<asin>?tag=...`, which resolves to Amazon's
-- "page not found" for an ASIN that doesn't exist. Format validation cannot detect
-- this: a fabricated ASIN and a real one are indistinguishable offline.
--
-- Fix: an explicit `asin_verified` flag. The client only builds a /dp/ deep link for
-- a row that is flagged verified; everything else falls back to the tagged Amazon
-- search URL (`/s?k=<product name>&tag=...`), which always resolves to a real page
-- with real products and still tracks the affiliate commission. That makes "link
-- works" the default and "deep link" the thing that has to be earned, instead of the
-- other way round.
--
-- The fabricated values are deliberately NOT deleted — they stay in `asin` so an
-- admin can see what needs correcting. Flipping a row back on is a one-liner:
--   UPDATE affiliate_products SET asin = '<real ASIN>', asin_verified = TRUE WHERE id = '...';
-- =====================================================

ALTER TABLE affiliate_products
    ADD COLUMN IF NOT EXISTS asin_verified BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN affiliate_products.asin_verified IS
    'TRUE only after a human has opened https://www.amazon.sa/dp/<asin> and confirmed it '
    'resolves to this product. The app deep-links to /dp/ only when TRUE; otherwise it '
    'uses a tagged search URL, which can never 404.';

-- `asin` becomes optional: a catalog entry with no known ASIN is now a legitimate,
-- fully-functional row (it searches by name) rather than something that had to be
-- filled with a placeholder to satisfy NOT NULL — which is how the fabricated values
-- got in here in the first place.
ALTER TABLE affiliate_products
    ALTER COLUMN asin DROP NOT NULL;

-- Every existing row is unverified by design (the DEFAULT above already covers rows
-- inserted before this migration); stated explicitly so re-running is idempotent and
-- so the intent is not left implicit in a column default.
UPDATE affiliate_products SET asin_verified = FALSE WHERE asin_verified IS DISTINCT FROM TRUE;
