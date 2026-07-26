-- Companion to 20260725140000_family_transaction_scoping.sql: that migration let admins
-- read children's zad_transactions rows, but the family dashboard also needs each child's
-- budget *ceiling* (zad_users.budget) to show "spent X of Y this month" — zad_users' only
-- policy today is strictly single-user (auth.uid() = id), so an admin querying a child's row
-- would get nothing back. Same additive-SELECT-policy pattern as the transactions one.
DROP POLICY IF EXISTS "family_admin_read_child_budget" ON zad_users;
CREATE POLICY "family_admin_read_child_budget" ON zad_users FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM family_members fm_admin
            JOIN family_members fm_child ON fm_child.family_id = fm_admin.family_id
            WHERE fm_admin.user_id = auth.uid()
              AND fm_admin.role = 'admin'
              AND fm_child.user_id = zad_users.id
              AND fm_child.role = 'child'
        )
    );
