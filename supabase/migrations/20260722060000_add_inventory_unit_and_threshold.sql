-- Fix Room ↔ Supabase schema mismatch on zad_inventory: the Kotlin ZadInventory entity
-- has always had `unit` and `lowStockThreshold`, but the live table never did. Inserts from
-- the app that included these fields were silently failing server-side (caught/swallowed
-- exception) while succeeding in the local Room cache, causing cloud/device data to diverge.
ALTER TABLE public.zad_inventory
  ADD COLUMN IF NOT EXISTS unit TEXT DEFAULT 'pcs',
  ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER DEFAULT 2;
