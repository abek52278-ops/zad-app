-- update-behavior-profile-daily was firing every night but the underlying
-- net.http_post call to the edge function used pg_net's default 5s timeout.
-- The function loops per-user with serial DB round-trips and was observed
-- timing out at ~4.84s of the 5s budget (net._http_response.error_msg),
-- so user_behavior_profile stayed empty despite the cron reporting "succeeded"
-- (pg_net only confirms the request was queued, not that it got a response).
-- Raising the timeout to 60s gives the function room to finish.
select cron.schedule(
  'update-behavior-profile-daily',
  '0 23 * * *',
  $$
  select net.http_post(
      url:='https://auuftqncrjsnyylolhbu.supabase.co/functions/v1/update-behavior-profile',
      headers:='{"Content-Type":"application/json","Authorization":"Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImV4cCI6MTk4MzgxMjk5Nn0.EGIM96BfZm7Qn1dLe1iNFgRGRMdxUvO3tG0y3S0w0RQ"}'::jsonb,
      body:='{}'::jsonb,
      timeout_milliseconds:=60000
    ) as request_id;
  $$
);
