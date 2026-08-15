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
-- Deleting them is the honest cleanup: they cannot be returned to an owner, because
-- nothing recorded who the owner was. The NOT NULL constraints after are what stop the
-- next one from being written at all — a rejected insert is loud and fixable, a silently
-- orphaned row is neither.

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
