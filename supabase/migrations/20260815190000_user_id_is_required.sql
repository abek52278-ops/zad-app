-- Rows with user_id = null belong to nobody and are invisible to everybody.
--
-- Every one of these tables has RLS filtering on `auth.uid() = user_id`, so a null owner
-- means no session can ever read the row back. It is not hidden data — it is unreachable
-- data, already gone as far as the product is concerned, still occupying the table.
--
-- Found on 2026-08-15: three zad_inventory rows ('بيض' twice, 'حليب') and two
-- zad_subscriptions rows ('نتفلكس', 'جيم'). They came from the client: addSubscription,
-- addPharmacyItem and addShoppingItem each read auth.uid(), copied it onto the row, and
-- inserted whether or not it was null. addInventory alone had the guard, and it was never
-- carried across to the other three — that asymmetry is fixed in SupabaseRepo.kt in the
-- same change as this migration.
--
-- They have to leave the tables for the NOT NULL constraints below to hold, but they do
-- not have to be destroyed to do that. Each one is copied into zad_orphaned_rows first,
-- whole, as jsonb. Nothing recorded who owned them, so nobody can claim them back today —
-- but "we cannot identify the owner right now" is not the same as "this can be thrown
-- away", and a delete is the one step that cannot be walked back if that turns out to be
-- wrong. The archive costs a few rows and removes the need to be certain.

create table if not exists public.zad_orphaned_rows (
  id          uuid primary key default gen_random_uuid(),
  source_table text not null,
  row_data    jsonb not null,
  archived_at timestamptz not null default now()
);

-- No policy is granted: nothing here belongs to any user, so nothing should read it
-- through the API. RLS on with zero policies means the anon and authenticated roles see
-- an empty table, while service_role and a database session still can.
alter table public.zad_orphaned_rows enable row level security;

insert into public.zad_orphaned_rows (source_table, row_data)
select 'zad_inventory',      to_jsonb(t) from public.zad_inventory      t where t.user_id is null
union all
select 'zad_subscriptions',  to_jsonb(t) from public.zad_subscriptions  t where t.user_id is null
union all
select 'zad_shopping_list',  to_jsonb(t) from public.zad_shopping_list  t where t.user_id is null
union all
select 'zad_pharmacy_items', to_jsonb(t) from public.zad_pharmacy_items t where t.user_id is null
union all
select 'zad_transactions',   to_jsonb(t) from public.zad_transactions   t where t.user_id is null;

delete from public.zad_inventory      where user_id is null;
delete from public.zad_subscriptions  where user_id is null;
delete from public.zad_shopping_list  where user_id is null;
delete from public.zad_pharmacy_items where user_id is null;
delete from public.zad_transactions   where user_id is null;

alter table public.zad_inventory      alter column user_id set not null;
alter table public.zad_subscriptions  alter column user_id set not null;
alter table public.zad_shopping_list  alter column user_id set not null;
alter table public.zad_pharmacy_items alter column user_id set not null;
alter table public.zad_transactions   alter column user_id set not null;
