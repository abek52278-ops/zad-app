-- Free tier shipped with brain_monthly=0 and voice_monthly=0. That's not literally a
-- hard lock for either: brain still has the weekly_free consult + the 3-ad session
-- unlock (zad_entitlement_consume), and voice/scan both have the 24h media_pass ad
-- unlock (zad_media_pass_grant) — but a brand-new user with zero transaction history has
-- no ads to watch yet and no reason to go looking, so their first live impression of
-- "شيف زاد" or the voice assistant is a silent 402. That's the bug being fixed here:
-- not the ad-gate design (kept as-is, still the monetization lever above these
-- baselines), just the fact that the baseline itself was zero.
--
-- Numbers are deliberately modest, not the 50-100 raised in conversation: brain_monthly
-- spends from the shared Gemini text pool documented in CLAUDE.md (~100 req/day total
-- across 5 keys, split across every user), so a generous per-user baseline here empties
-- that pool for everyone on day one. voice can afford to be more generous — it runs on
-- a separate model bucket (gemini-3.1-flash-live-preview via zad-voice-live), so it
-- doesn't compete with the same quota brain/chat share.
update public.zad_tiers set brain_monthly = 5  where tier = 'free';
update public.zad_tiers set voice_monthly = 10 where tier = 'free';
