-- Recovered from the remote migration history — this version was applied to the database
-- and never written to the repo, so `supabase db push` refused to run at all ("Remote
-- migration versions not found in local migrations directory").
--
-- The applied statement did two things: it created zad_try_date, and it redefined
-- zad_domain_observations to use it. Only the first is unique to this migration — the very
-- next applied version (20260814214755_domain_observations_budget_and_obligation_payment)
-- redefines zad_domain_observations again, and that one is in the repo. Reproducing a
-- superseded copy of a large function body here would add a version of it that no
-- environment has ever actually run past this point, so what is restored is the part that
-- still stands on its own.
--
-- Why it exists at all: the observation queries cast free-text date columns, and a single
-- malformed value would abort the whole query rather than skip one row. Wrapping the cast
-- so a bad value yields NULL is what lets one unparseable date cost one observation
-- instead of all of them.

CREATE OR REPLACE FUNCTION zad_try_date(p_text TEXT)
RETURNS DATE
LANGUAGE plpgsql
IMMUTABLE
AS $$
BEGIN
  RETURN left(btrim(p_text), 10)::date;
EXCEPTION WHEN others THEN
  RETURN NULL;
END $$;
