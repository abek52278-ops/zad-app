-- Nothing leaves the balance card without the customer saying yes.
--
-- Until now a bank notification the phone parsed with confidence >= 0.9 was written
-- straight into zad_transactions by handleNotificationIngest, and the green card moved on
-- its own. Only the *unclear* ones raised a question. The customer asked for the opposite
-- rule, in these words: "النوتيفيكيشن ليسننج رصدت أي صرف ... يقراها عقل الايجنت يحلل يشوف
-- سحب ولا إيداع ويبعتلي عن طريق البوت ويقولي جالك كذا أو صرفت كذا، أقوله أيوة أو لا،
-- ويسجل ويعدل الكارت الأخضر".
--
-- So a parsed notification now becomes a *question* first — on Telegram when the account
-- is linked, in zad_insights when it isn't — and the transaction row is written by the
-- existing confirm path (telegram_pending_writes -> agent_confirm -> log_transaction),
-- which is already the only place in the bot that writes money.
--
-- The event needs a status for that state. 'ambiguous' would be a lie: these are the
-- notifications the parser understood completely, and reusing it would make the two
-- genuinely different outcomes ("we could not read this" / "we read it and are waiting on
-- the customer") indistinguishable in the audit table that exists to tell them apart.
alter table public.zad_notification_ingest_events
  drop constraint if exists zad_notification_ingest_events_status_check;

alter table public.zad_notification_ingest_events
  add constraint zad_notification_ingest_events_status_check
  check (status in ('received','logged','ignored','ambiguous','rejected','awaiting_confirmation'));

comment on column public.zad_notification_ingest_events.status is
  'received = seen. logged = a transaction row exists (transaction_id). ignored/rejected = never becoming money. ambiguous = unreadable, the customer was asked what it was. awaiting_confirmation = read fine, the customer was asked to approve it before it touches the balance.';

-- Transfers are deliberately NOT part of this. An ATM withdrawal moves money from card to
-- cash and leaves the ledger balance identical, so there is no number for the customer to
-- approve — and log_transaction (validators.ts) only accepts expense/income anyway.
-- Those keep the direct write path.
