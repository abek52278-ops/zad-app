-- Covers the source-event foreign key for deletes and proposal audit lookups.
create index if not exists idx_zad_transaction_proposals_source_event
  on public.zad_transaction_proposals (source_event_id)
  where source_event_id is not null;
