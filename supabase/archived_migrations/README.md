# Archived migrations

Files in this directory are retained for audit history but must not be applied by the
Supabase CLI.

`20260817030000_monthly_quota_and_billing_engine.sql` is an obsolete parallel billing
design. It stores client-visible quota fields on `zad_users` and creates a second
`subscriptions` table, while Zad's active authority is `zad_entitlements` plus
`zad_tiers`. Applying both would create two conflicting sources of truth.
