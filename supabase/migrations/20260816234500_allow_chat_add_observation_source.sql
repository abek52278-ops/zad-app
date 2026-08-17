-- `add_inventory_item` (zad-brain chat tool) records every quantity it writes as a
-- consumption observation with source 'chat_add'. That value was never in the CHECK
-- constraint, so the RPC raised
--   new row for relation "zad_inventory_observations" violates check constraint
--   "zad_inventory_observations_source_check"
-- on every chat-added item (observed live 2026-08-16). PostgREST turned that into a 400,
-- and the item stayed in stock_unknown forever: zad_consumption.avg_daily_qty — the
-- learned per-item burn rate the forward-ledger work depends on — never advanced for
-- anything added through chat.
--
-- 'chat_add' is kept as its own value rather than folded into 'manual' because
-- provenance is the whole point of the column: a number the customer typed into a chat
-- message is weaker evidence than one they entered on the inventory screen.
alter table public.zad_inventory_observations
  drop constraint if exists zad_inventory_observations_source_check;

alter table public.zad_inventory_observations
  add constraint zad_inventory_observations_source_check
  check (source = any (array[
    'question_answer'::text,
    'camera_ocr'::text,
    'manual'::text,
    'purchase'::text,
    'chat_add'::text
  ]));
