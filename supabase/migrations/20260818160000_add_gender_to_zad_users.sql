-- Migration: Add gender column to zad_users
-- Purpose: Allows zad-brain's getAssistantName() to resolve the correct
--          assistant persona ("zada ai" for female, "zad intelligence" for male).
--          Without this column, the brain always fell back to the male default.

ALTER TABLE zad_users
  ADD COLUMN IF NOT EXISTS gender text
    CHECK (gender IN ('male', 'female', 'other') OR gender IS NULL);

COMMENT ON COLUMN zad_users.gender IS
  'Optional: user-declared gender used by the AI brain to personalise the assistant name. '
  'Values: male | female | other | NULL (default = male persona).';
