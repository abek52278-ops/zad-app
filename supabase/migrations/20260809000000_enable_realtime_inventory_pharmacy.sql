-- Extends 20260808220000's realtime fix to the other two personal tables the app now
-- subscribes to live (see RealtimePersonalRepo.kt): zad_inventory and zad_pharmacy_items.
-- Same root cause — neither was ever added to supabase_realtime, so a subscription to
-- either would silently never fire.
ALTER PUBLICATION supabase_realtime ADD TABLE zad_inventory;
ALTER PUBLICATION supabase_realtime ADD TABLE zad_pharmacy_items;
