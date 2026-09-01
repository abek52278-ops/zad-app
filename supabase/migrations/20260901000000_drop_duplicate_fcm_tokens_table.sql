-- market_intelligence_tables.sql (20260831140000) created a SECOND FCM token table,
-- `fcm_tokens`, separate from the real one (`zad_fcm_tokens`, 20260831010000) that
-- zad-brain/push.ts actually writes device tokens into and reads for delivery.
-- Nothing ever wrote a row into `fcm_tokens` — zad-market-intelligence's own
-- sendFCMNotifications() queried it, found nothing, and silently skipped every
-- alert. Now that the read side points at zad_fcm_tokens, this table has no
-- writer, no reader, and never held data. Safe to drop outright.
drop table if exists public.fcm_tokens;
