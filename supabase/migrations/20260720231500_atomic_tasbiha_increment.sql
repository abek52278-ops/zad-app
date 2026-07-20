-- =====================================================
-- FIX: family_tasbiha click sync was an absolute-value UPDATE computed
-- client-side (FamilyViewModel.tasbihaClick -> SupabaseRepo.clickTasbiha),
-- debounced 1.5s with cancel-and-replace on every tap. Verified during
-- testing that local state does chain correctly across rapid taps (so the
-- final debounced write itself carried the right total) — but two real
-- gaps remained:
--   1. If the ViewModel is cleared (screen left / process death) before
--      the 1.5s debounce fires, the entire pending batch of taps is lost —
--      nothing was ever sent to the server.
--   2. The write is a last-write-wins absolute SET, not an atomic
--      increment, so two sessions for the same user (or a retry racing a
--      fresh read) can clobber each other's counts.
--
-- This function makes the counter increment atomic and delta-based:
-- the client accumulates a local pending delta across rapid taps and
-- flushes it (or a best-effort flush on ViewModel teardown) as a single
-- `total_clicks/score = total_clicks/score + delta` — safe to call
-- multiple times with small deltas without losing intermediate taps.
--
-- SECURITY DEFINER is deliberately NOT used — this runs as the caller
-- (default SECURITY INVOKER), so the UPDATE inside is still gated by the
-- existing `family_tasbiha_update` RLS policy (family_id IN
-- get_my_family_ids()). No RLS bypass is introduced.
-- =====================================================

CREATE OR REPLACE FUNCTION public.increment_tasbiha_clicks(
    p_tree_id uuid,
    p_delta integer,
    p_level integer,
    p_tree_emoji text,
    p_is_mature boolean,
    p_matured_at timestamptz,
    p_streak_days integer,
    p_last_streak_date text,
    p_last_tasbih_at text
)
RETURNS family_tasbiha
LANGUAGE plpgsql
AS $$
DECLARE
    result family_tasbiha;
BEGIN
    UPDATE family_tasbiha
    SET total_clicks = total_clicks + p_delta,
        score = score + p_delta,
        level = p_level,
        tree_emoji = p_tree_emoji,
        is_mature = p_is_mature,
        matured_at = COALESCE(p_matured_at, matured_at),
        streak_days = p_streak_days,
        last_streak_date = p_last_streak_date,
        last_tasbih_at = p_last_tasbih_at
    WHERE id = p_tree_id
    RETURNING * INTO result;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'family_tasbiha row % not found or not visible to caller', p_tree_id;
    END IF;

    RETURN result;
END;
$$;

GRANT EXECUTE ON FUNCTION public.increment_tasbiha_clicks(
    uuid, integer, integer, text, boolean, timestamptz, integer, text, text
) TO authenticated;
