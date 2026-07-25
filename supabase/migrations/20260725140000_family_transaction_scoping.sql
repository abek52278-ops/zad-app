-- Per-member budgets stay independent (unchanged), but parents (role='admin')
-- need read access to their children's (role='child') zad_transactions rows
-- for the family dashboard, and two admins sharing one physical joint card
-- need the duplicate SMS/notification that lands on both phones deduplicated
-- at the server, since client-side TxDeduplicator only ever sees its own
-- device's inserts. Single BEFORE INSERT trigger handles both concerns in a
-- fixed order (family_id populated first, then checked for a dup) rather than
-- two triggers relying on alphabetical firing order.

ALTER TABLE zad_transactions ADD COLUMN IF NOT EXISTS family_id UUID REFERENCES family_groups(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_zad_transactions_family_created ON zad_transactions(family_id, created_at);
CREATE INDEX IF NOT EXISTS idx_zad_transactions_family_user ON zad_transactions(family_id, user_id);

CREATE OR REPLACE FUNCTION public.populate_and_dedupe_family_transaction()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $function$
DECLARE
    inserter_is_admin BOOLEAN;
    dup_exists BOOLEAN;
BEGIN
    -- 1) family_id is always server-derived — the client never sends one, and
    --    even if it did, this overwrites it. Never trust a client-supplied family_id.
    IF NEW.user_id IS NOT NULL THEN
        SELECT family_id INTO NEW.family_id FROM family_members WHERE user_id = NEW.user_id LIMIT 1;
    ELSE
        NEW.family_id := NULL;
    END IF;

    IF NEW.family_id IS NULL THEN
        RETURN NEW;
    END IF;

    -- 2) shared joint-card dedup — v1, server-only guarantee: prevents the
    --    duplicate row from ever existing server-side (so family aggregates/
    --    dashboards are correct). Each phone's own local BudgetTracker may
    --    still separately reflect its own deduction — full cross-device
    --    rollback of that local state is a known, accepted limitation of this
    --    v1, not solved here. Scoped to two DIFFERENT admins in the same
    --    family only; children's transactions are never deduplicated this way.
    SELECT (role = 'admin') INTO inserter_is_admin
        FROM family_members WHERE user_id = NEW.user_id AND family_id = NEW.family_id;

    IF NOT COALESCE(inserter_is_admin, FALSE) THEN
        RETURN NEW;
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM zad_transactions t
        JOIN family_members fm ON fm.user_id = t.user_id AND fm.family_id = t.family_id
        WHERE t.family_id = NEW.family_id
          AND t.user_id <> NEW.user_id
          AND fm.role = 'admin'
          AND t.amount = NEW.amount
          AND t.is_expense = NEW.is_expense
          AND t.created_at >= NOW() - INTERVAL '5 minutes'
    ) INTO dup_exists;

    IF dup_exists THEN
        RETURN NULL; -- silently skip insert; SupabaseRepo.addTransaction sees a normal success
    END IF;

    RETURN NEW;
END;
$function$;

DROP TRIGGER IF EXISTS trigger_populate_and_dedupe_family_transaction ON zad_transactions;
CREATE TRIGGER trigger_populate_and_dedupe_family_transaction
    BEFORE INSERT ON zad_transactions
    FOR EACH ROW EXECUTE FUNCTION public.populate_and_dedupe_family_transaction();

-- Additive SELECT policy — Postgres ORs multiple permissive policies for the
-- same command, so this only grants extra read access; the existing
-- "user_own_transactions FOR ALL" policy still governs insert/update/delete.
DROP POLICY IF EXISTS "family_admin_read_child_transactions" ON zad_transactions;
CREATE POLICY "family_admin_read_child_transactions" ON zad_transactions FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM family_members fm_admin
            JOIN family_members fm_child ON fm_child.family_id = fm_admin.family_id
            WHERE fm_admin.user_id = auth.uid()
              AND fm_admin.role = 'admin'
              AND fm_child.user_id = zad_transactions.user_id
              AND fm_child.role = 'child'
        )
    );
