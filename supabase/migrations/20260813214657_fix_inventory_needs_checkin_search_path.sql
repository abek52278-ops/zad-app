-- Security advisor: _inventory_needs_checkin had a mutable search_path (missing from
-- the initial migration) -- every other function in this project sets it explicitly.
CREATE OR REPLACE FUNCTION public._inventory_needs_checkin(p_quantity NUMERIC, p_threshold NUMERIC, p_avg_daily_qty NUMERIC, p_rate_known BOOLEAN)
RETURNS BOOLEAN
LANGUAGE sql
IMMUTABLE
SET search_path = public
AS $function$
    SELECT p_quantity <= COALESCE(p_threshold, 2)
        OR (COALESCE(p_rate_known, false) AND COALESCE(p_avg_daily_qty, 0) > 0 AND p_quantity / p_avg_daily_qty <= 2);
$function$;
