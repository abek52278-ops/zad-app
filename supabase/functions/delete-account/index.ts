// deno-lint-ignore-file
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.6";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const admin = createClient(supabaseUrl, serviceKey);

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, x-client-info, Content-Type, Authorization",
};

// Tables with no FK/ON DELETE CASCADE to auth.users — must be cleaned explicitly.
// Everything else (zad_memory, zad_insights, zad_debts, zad_pharmacy_items,
// zad_maintenance_items, zad_dose_log, zad_consumption, zad_brain_queue,
// zad_brain_runs, user_behavior_profile, family_tasbiha, tasbiha_challenge_progress,
// financial_challenge_progress) cascades automatically once auth.users is deleted.
const USER_OWNED_TABLES = [
  "zad_inventory",
  "zad_transactions",
  "zad_subscriptions",
  "zad_shopping_list",
  "app_notifications",
  "affiliate_clicks",
  "affiliate_catalog_requests",
  "family_members",
];

// Same problem, different column name. Keyed on sender_id, no FK to auth.users.
const SENDER_OWNED_TABLES = [
  "chat_messages",
  "family_messages",
];

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });

  try {
    const authHeader = req.headers.get("Authorization") ?? "";
    const token = authHeader.replace(/^Bearer\s+/i, "");
    if (!token) {
      return new Response(JSON.stringify({ error: "missing_auth" }), { status: 401, headers: corsHeaders });
    }

    // Never trust a user_id from the request body — resolve the caller's own
    // id from their verified JWT so this can only ever delete the caller's
    // own account (never someone else's, even if body were tampered with).
    const callerClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: `Bearer ${token}` } },
    });
    const { data: userRes, error: userErr } = await callerClient.auth.getUser();
    if (userErr || !userRes?.user) {
      return new Response(JSON.stringify({ error: "invalid_session" }), { status: 401, headers: corsHeaders });
    }
    const userId = userRes.user.id;

    for (const table of USER_OWNED_TABLES) {
      const { error } = await admin.from(table).delete().eq("user_id", userId);
      if (error) console.error(`[DeleteAccount] cleanup failed table=${table}: ${error.message}`);
    }

    // Chat carries the user's own words, and neither table keys on `user_id` or has an
    // FK to auth.users — so deleting the account left every message they ever sent in
    // place, readable by the rest of the family group forever. Verified 2026-08-14 by
    // checking pg_constraint: no auth.users FK on either sender_id.
    for (const table of SENDER_OWNED_TABLES) {
      const { error } = await admin.from(table).delete().eq("sender_id", userId);
      if (error) console.error(`[DeleteAccount] cleanup failed table=${table}: ${error.message}`);
    }
    // zad_users primary key is the auth user id itself, not a "user_id" column.
    const { error: profileErr } = await admin.from("zad_users").delete().eq("id", userId);
    if (profileErr) console.error(`[DeleteAccount] cleanup failed table=zad_users: ${profileErr.message}`);

    // Deleting the auth user cascades every table with ON DELETE CASCADE to
    // auth.users, and is the actual account deletion — without this the
    // client-only row deletes just emptied the profile while the auth
    // account (and email) lived on forever.
    const { error: deleteErr } = await admin.auth.admin.deleteUser(userId);
    if (deleteErr) {
      console.error(`[DeleteAccount] auth.admin.deleteUser failed: ${deleteErr.message}`);
      return new Response(JSON.stringify({ error: deleteErr.message }), { status: 500, headers: corsHeaders });
    }

    return new Response(JSON.stringify({ success: true }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
  } catch (e) {
    console.error(`[DeleteAccount] Error: ${e.message}`);
    return new Response(JSON.stringify({ error: e.message }), { status: 500, headers: corsHeaders });
  }
});
