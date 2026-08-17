-- Five columns the Kotlin models have had all along and the tables never did.
--
-- Found by diffing every @Entity in Models.kt against information_schema, after the four
-- 400s fixed earlier came from exactly this class of drift. These ones hid better,
-- because kotlinx-serialization omits properties still equal to their default: PostgREST
-- logs show the working insert as
--   POST /zad_shopping_list?columns=id,user_id,item_name,quantity
-- i.e. it round-trips only while nobody sets the fields. The moment a real value appears
-- the insert names a column that does not exist, PostgREST answers 400, and
-- SupabaseRepo's catch logs it and returns — the row is already in Room, so the phone
-- looks correct and the sync silently never happened.
--
-- Three call sites set them today:
--   InventoryFlowEngine  priority = "high" for a depleted item, plus predicted_days_left
--                        → the whole auto-replenish path never reached Supabase
--   ShoppingListScreen   store, typed by the customer in the add dialog
--   SubscriptionsScreen  provider, typed by the customer in the add dialog
--
-- Defaults match the Kotlin defaults exactly so existing rows read back unchanged.

alter table public.zad_shopping_list
  add column if not exists priority text not null default 'medium',
  add column if not exists predicted_days_left integer,
  add column if not exists store text;

alter table public.zad_subscriptions
  add column if not exists type text not null default 'subscription',
  add column if not exists provider text;

-- SubscriptionsScreen's tabs filter on these, so they are read on every open.
create index if not exists idx_zad_subscriptions_user_type
  on public.zad_subscriptions (user_id, type);

comment on column public.zad_shopping_list.priority is
  'medium | high — set by InventoryFlowEngine auto-replenish (high when the item hit zero).';
comment on column public.zad_shopping_list.predicted_days_left is
  'ConsumptionLearner estimate at the time the item was listed. Null when the rate is unknown.';
comment on column public.zad_subscriptions.type is
  'subscription | bill | installment — drives the SubscriptionsScreen tabs.';
