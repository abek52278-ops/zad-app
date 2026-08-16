// zad-telegram-bot — Phase B4 (PRODUCT_PLAN.md), built on grammY per explicit user
// instruction (2026-07-30). Read-only: view balance/recent transactions/pending
// insights, act on Task 28's dismiss-with-reason from a Telegram button, and — as of
// v2 — hold a real conversation. No writes beyond dismissal; the agent is explicitly
// told (context.ts rule 5) not to claim it logged anything, because it can't.
//
// v2 (conversational): free text goes to Zad itself instead of bouncing back a button
// menu. The customer's full picture is assembled server-side by context.ts using the
// same === SECTION === contract as the Kotlin client's buildFullChatContext(), and the
// model call routes through zad-core-intelligence's `ai_text` action so the bot
// inherits the app's Groq-pool-primary/Gemini-fallback policy instead of forking it.
//
// Identity: EPIC_1_4.md's own warning — "a chat_id is never an identity". A user
// generates a one-time binding code in the app (telegram_bindings row, user_id set,
// chat_id null); this function only trusts a chat_id once it's bound to that exact
// code via /start <code>. Every subsequent request is authorized by chat_id → user_id
// through that table, never by anything the client claims about itself.
import { Bot, InlineKeyboard, webhookCallback } from "npm:grammy@1";
import { createClient, SupabaseClient } from "jsr:@supabase/supabase-js@2";
import {
  InlineKeyboardButton, mainMenuKeyboard, dismissKeyboard,
  reasonForCode, parseDismissCallback, normalizeBindingCode, memoryNoteForDismissal,
  formatBalanceMessage, type BudgetStateRow, formatTransactionsMessage, formatInsightTitle,
  confirmSpendKeyboard, parseSpendCallback,
  confirmMedicationKeyboard, parseMedicationCallback,
  checkInKeyboard, parseCheckInCallback, checkInPromptMessage,
  confirmToolKeyboard, parseToolCallback,
} from "./telegram.ts";
import {
  AgentContextInput, agentSystemPrompt, buildAgentContext, clampForTelegram,
  confirmSpendMessage, deriveWebhookSecret, money,
  confirmMedicationMessage,
  isolate, sanitizeName,
} from "./context.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
// NOT `!`-asserted, deliberately. A missing token blew up at module load — new Bot("")
// throws — which the platform surfaces as an opaque WORKER_ERROR/500 on every request
// with nothing useful in the logs, so the function couldn't tell you what was wrong.
// It now boots regardless and the GET probe reports which config is actually present.
const BOT_TOKEN = Deno.env.get("TELEGRAM_BOT_TOKEN") ?? "";
const BOT_CONFIGURED = BOT_TOKEN.length > 0;
// Telegram's setWebhook secret_token, verified by grammY itself when passed to
// webhookCallback below. If unset we derive one from the bot token rather than leaving
// verification off entirely — see deriveWebhookSecret in context.ts.
const WEBHOOK_SECRET_ENV = Deno.env.get("TELEGRAM_WEBHOOK_SECRET") ?? undefined;

// Gates the daily check-in cron trigger (?job=daily_checkins) below. This endpoint has to
// live on zad-telegram-bot because Telegram's own webhook needs verify_jwt=false on this
// function, so the platform-level JWT check that gates every other function doesn't apply
// here — without this, anyone on the internet could POST ?job=daily_checkins and spam
// every bound user. Deliberately a plain literal (matching the same value in this repo's
// telegram_checkin_pipeline migration) rather than a project secret: it authorizes nothing
// beyond triggering this one job (no data access of its own), so committing it is a much
// smaller blast radius than a leaked service-role key, and there is no tool available in
// this environment to provision a new Supabase project secret remotely.
const CHECKIN_CRON_SECRET = "5bbebc0b2acb1099e758e822be15144f9bf30175da67a459dd8511b0139c8ec5";

// Same rationale as CHECKIN_CRON_SECRET immediately above (plain literal, not a project
// secret — this endpoint runs with verify_jwt=false so it needs its own gate) but its own
// distinct value, not reused: a leaked secret here should only ever be able to trigger
// subscription alerts, not the check-in job too. Gates ?job=subscription_alerts below.
const SUBSCRIPTION_CRON_SECRET = "2ceb272a5a1b12cce797b99f3e6d07a79b540cb95a5823ac9155588269214d55";

// Same rationale again, own distinct value. Gates ?job=realtime_push below — fired by a
// Postgres trigger (not cron) the moment a transaction is inserted or a budget threshold
// is crossed, matching this repo's existing pattern of the client/DB detecting the
// real-world event and this function only doing delivery (see GeofenceBroadcastReceiver's
// zad-brain call for the same split on the Android side). This endpoint does NOT recompute
// anything — the caller (a SQL trigger) sends pre-formatted title/body text, so no budget
// math is duplicated here or in SQL beyond what notify_parents_on_child_spend() already does.
const REALTIME_PUSH_CRON_SECRET = "7e78ce0aa8d2e83f67fbe48c39b5c39c17e54d32f781ccf79a1bd1000aaa7094";

// Same rationale again, own distinct value. Gates ?job=live_checkin below — fired by a
// zad_inventory AFTER UPDATE trigger the instant an item crosses into low-stock/predicted-
// depletion territory, instead of waiting for the once-daily runDailyCheckins cron. Shares
// sendCheckInPrompt()/MAX_CHECKINS_PER_USER_PER_DAY with the daily job so the two paths
// can't double the user's daily prompt budget between them.
const LIVE_CHECKIN_CRON_SECRET = "58dda37fa693d2351ab038f07303fb9b621ce983c597046de7c7edc2b6d283a6";

// Same rationale again, own distinct value. Gates ?job=confirm_transaction below — called
// by zad-brain's handleNotificationIngest the moment the phone's NotificationListener
// hands it a bank notification it could read. This endpoint asks; it never writes money.
// The row it creates is a telegram_pending_writes row, and the write itself still happens
// only in the confirm-button branch of callback_query, which is the single place in this
// function that has ever written a transaction. zad-brain holds the same literal — see
// NOTIFICATION_CONFIRM_SECRET there, and keep the two in step.
const CONFIRM_TRANSACTION_SECRET = "b1f0a4c7d29e63581c0a7f4e2b9d8c3a65e07f14d8b2c96035ae7143f0d92b68";

function toGrammyKeyboard(rows: InlineKeyboardButton[][]): InlineKeyboard {
  const kb = new InlineKeyboard();
  for (const row of rows) {
    row.forEach((btn, i) => {
      kb.text(btn.text, btn.callback_data);
      if (i < row.length - 1) kb.row();
    });
    kb.row();
  }
  return kb;
}

async function resolveUserId(sb: SupabaseClient, chatId: number): Promise<string | null> {
  const { data } = await sb.from("telegram_bindings")
    .select("user_id")
    .eq("chat_id", chatId)
    .not("bound_at", "is", null)
    .maybeSingle();
  return (data as { user_id: string } | null)?.user_id ?? null;
}

/** Reverse of resolveUserId — the realtime_push job only knows user_id (from a DB trigger
 * row), never chat_id. Returns null for an unbound user, which the caller treats as a
 * silent no-op (most users won't have Telegram linked at all). */
async function resolveChatId(sb: SupabaseClient, userId: string): Promise<number | null> {
  const { data } = await sb.from("telegram_bindings")
    .select("chat_id")
    .eq("user_id", userId)
    .not("bound_at", "is", null)
    .maybeSingle();
  return (data as { chat_id: number } | null)?.chat_id ?? null;
}

/** Direct Telegram API call, not a grammY ctx.reply — this fires OUTSIDE any inbound
 * webhook update (the cron job below has no ctx to reply through). */
async function sendTelegramMessage(chatId: number, text: string, keyboard?: InlineKeyboardButton[][]): Promise<void> {
  await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/sendMessage`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      chat_id: chatId,
      text,
      ...(keyboard ? { reply_markup: { inline_keyboard: keyboard } } : {}),
    }),
  });
}

/** Server-side low-stock detection against zad_inventory + zad_consumption (Task 18's
 * learning loop) — deliberately NOT a port of ConsumptionLearner's on-device
 * SharedPreferences model, which never leaves the Android client. "Needs a check-in" here
 * means: at/under its low_stock_threshold, OR — when a real consumption rate has been
 * learned (rate_known) — predicted to run out within 2 days at that rate. Items with an
 * already-pending prompt are excluded (the unique index on telegram_checkin_prompts is the
 * hard guarantee; this filter just avoids the wasted query/round-trip).
 */
async function findCheckInCandidates(sb: SupabaseClient, userId: string): Promise<Array<{ item_name: string; quantity: number }>> {
  const [{ data: inv }, { data: cons }, { data: pending }] = await Promise.all([
    sb.from("zad_inventory").select("item_name,quantity,low_stock_threshold").eq("user_id", userId),
    sb.from("zad_consumption").select("item_name,avg_daily_qty,rate_known").eq("user_id", userId),
    sb.from("telegram_checkin_prompts").select("item_name").eq("user_id", userId).eq("status", "pending"),
  ]);

  const consByItem = new Map(
    ((cons ?? []) as Array<{ item_name: string; avg_daily_qty: number; rate_known: boolean }>)
      .map((c) => [c.item_name, c]),
  );
  const pendingItems = new Set(((pending ?? []) as Array<{ item_name: string }>).map((p) => p.item_name));

  return ((inv ?? []) as Array<{ item_name: string; quantity: number; low_stock_threshold: number | null }>)
    .filter((item) => {
      if (pendingItems.has(item.item_name)) return false;
      const threshold = item.low_stock_threshold ?? 2;
      if (item.quantity <= threshold) return true;
      const rate = consByItem.get(item.item_name);
      if (rate?.rate_known && rate.avg_daily_qty > 0) {
        return item.quantity / rate.avg_daily_qty <= 2;
      }
      return false;
    })
    .map((item) => ({ item_name: item.item_name, quantity: item.quantity }));
}

/** The daily cron entry point. Capped at 2 prompts/user/day — this is a check-in nudge,
 * not a notification flood; a household with many low-stock items still only hears about
 * its two most pressing ones today (candidates aren't ranked beyond DB order — good enough
 * for a cap this small, not worth a scoring pass). Same cap applies to the real-time
 * job below (?job=live_checkin) — one shared daily budget, not two separate allowances. */
const MAX_CHECKINS_PER_USER_PER_DAY = 2;

/** كام prompt اتبعت النهاردة (UTC) للمستخدم ده، بغض النظر عن حالته دلوقتي (pending/
 *  answered/expired) — العدّاد هو "كام مرة إتقلق النهاردة"، مش "كام لسه مستني رد". */
async function checkinsSentToday(sb: SupabaseClient, userId: string): Promise<number> {
  const todayStart = new Date();
  todayStart.setUTCHours(0, 0, 0, 0);
  const { count } = await sb.from("telegram_checkin_prompts")
    .select("id", { count: "exact", head: true })
    .eq("user_id", userId)
    .gte("created_at", todayStart.toISOString());
  return count ?? 0;
}

/** بيبعت prompt واحد فعلياً — مشترك بين الكرون اليومي والمسار الفوري (live_checkin)
 *  عشان الاتنين يحترموا نفس السقف اليومي، مش نسختين بمنطق مختلف شوية. بيرجع false
 *  لو الكتابة فشلت — الكولر بيقرر يعمل إيه بالفشل. */
async function sendCheckInPrompt(sb: SupabaseClient, userId: string, chatId: number, itemName: string, quantity: number): Promise<boolean> {
  const { data: prompt, error } = await sb.from("telegram_checkin_prompts")
    .insert({ user_id: userId, item_name: itemName, quantity_at_prompt: quantity })
    .select("id")
    .single();
  if (error || !prompt) {
    console.error("checkin prompt insert failed:", error?.message);
    return false;
  }
  await sendTelegramMessage(chatId, checkInPromptMessage(isolate(sanitizeName(itemName))), checkInKeyboard((prompt as { id: string }).id));
  return true;
}

async function runDailyCheckins(sb: SupabaseClient): Promise<{ usersChecked: number; promptsSent: number }> {
  // Pending prompts nobody ever answered would otherwise block that item forever.
  await sb.from("telegram_checkin_prompts")
    .update({ status: "expired" })
    .eq("status", "pending")
    .lt("expires_at", new Date().toISOString());

  const { data: bindings } = await sb.from("telegram_bindings")
    .select("user_id,chat_id")
    .not("bound_at", "is", null)
    .not("chat_id", "is", null);

  const rows = (bindings ?? []) as Array<{ user_id: string; chat_id: number }>;
  let promptsSent = 0;

  for (const b of rows) {
    const alreadySentToday = await checkinsSentToday(sb, b.user_id);
    const budget = MAX_CHECKINS_PER_USER_PER_DAY - alreadySentToday;
    if (budget <= 0) continue;
    const candidates = await findCheckInCandidates(sb, b.user_id);
    for (const item of candidates.slice(0, budget)) {
      if (await sendCheckInPrompt(sb, b.user_id, b.chat_id, item.item_name, item.quantity)) promptsSent++;
    }
  }

  return { usersChecked: rows.length, promptsSent };
}

/**
 * Daily cron: DMs any Telegram-bound user whose active subscription/bill renews within
 * the next 3 days (same window as the in-app renewal reminder — ZadCentralBrain.fullAnalysis
 * / ZadViewModel.generateSmartNotifications, which do daysLeft in 0..3). This is the piece
 * that was entirely missing: those two only ever produce an in-app row or a local Android
 * notification, nothing reaches Telegram. Same net.http_post + secret-header pattern as
 * runDailyCheckins (see telegram_checkin_pipeline migration) — no per-day dedup table like
 * check-ins have, since a subscription only enters the 0..3 day window once per renewal
 * cycle, so a user gets at most ~4 daily pings per bill, not an unbounded repeat.
 *
 * Also covers zad_obligations (rent/installments incl. تابي/تمارة/فاليو/utilities) — these
 * had NO due-date reminder anywhere at all until now (unlike subscriptions, which at least
 * had the in-app/local-notification version). Next-due-date math reuses
 * zad_obligation_next_due() (SQL, SECURITY INVOKER, pure function) instead of a third
 * reimplementation of the same recurrence rules already in BudgetMath.kt and zad-brain.
 */
async function runDailySubscriptionAlerts(sb: SupabaseClient): Promise<{ usersChecked: number; alertsSent: number }> {
  const { data: bindings } = await sb.from("telegram_bindings")
    .select("user_id,chat_id")
    .not("bound_at", "is", null)
    .not("chat_id", "is", null);

  const rows = (bindings ?? []) as Array<{ user_id: string; chat_id: number }>;
  let alertsSent = 0;
  const today = new Date();
  today.setUTCHours(0, 0, 0, 0);
  const todayStr = today.toISOString().slice(0, 10);

  for (const b of rows) {
    const [{ data: subs }, { data: obligations }, { data: userRow }] = await Promise.all([
      sb.from("zad_subscriptions")
        .select("title,amount,renewal_date,currency")
        .eq("user_id", b.user_id)
        .eq("is_active", true)
        .not("renewal_date", "is", null),
      sb.from("zad_obligations")
        .select("title,amount,kind,recurrence,due_day,due_date")
        .eq("user_id", b.user_id)
        .eq("active", true),
      sb.from("zad_users").select("currency").eq("id", b.user_id).maybeSingle(),
    ]);
    const currency = (userRow as { currency: string | null } | null)?.currency ?? null;

    for (const sub of (subs ?? []) as Array<{ title: string; amount: number; renewal_date: string; currency: string | null }>) {
      const renewal = new Date(sub.renewal_date);
      if (isNaN(renewal.getTime())) continue;
      const daysLeft = Math.round((renewal.getTime() - today.getTime()) / 86400000);
      if (daysLeft < 0 || daysLeft > 3) continue;
      const amountText = `${sub.amount}${sub.currency ? " " + sub.currency : ""}`;
      const when = daysLeft === 0 ? "اليوم" : `خلال ${daysLeft} يوم`;
      await sendTelegramMessage(b.chat_id, `🔔 ${isolate(sanitizeName(sub.title))} يتجدد ${when} (${isolate(amountText)})`);
      alertsSent++;
    }

    for (const ob of (obligations ?? []) as Array<{ title: string; amount: number; kind: string; recurrence: string; due_day: number | null; due_date: string | null }>) {
      const { data: nextDue } = await sb.rpc("zad_obligation_next_due", {
        p_recurrence: ob.recurrence, p_due_day: ob.due_day, p_due_date: ob.due_date, p_asof: todayStr,
      });
      if (!nextDue) continue;
      const dueDate = new Date(nextDue as string);
      if (isNaN(dueDate.getTime())) continue;
      const daysLeft = Math.round((dueDate.getTime() - today.getTime()) / 86400000);
      if (daysLeft < 0 || daysLeft > 3) continue;
      const amountText = `${ob.amount}${currency ? " " + currency : ""}`;
      const when = daysLeft === 0 ? "اليوم" : `خلال ${daysLeft} يوم`;
      await sendTelegramMessage(b.chat_id, `🔔 ${isolate(sanitizeName(ob.title))} مستحق ${when} (${isolate(amountText)})`);
      alertsSent++;
    }
  }

  return { usersChecked: rows.length, alertsSent };
}

/** Pulls the same picture of the customer the in-app chat gets. Every query is
 * user-scoped explicitly — this runs on the service-role key, so RLS is NOT the
 * guard here; the .eq("user_id", userId) on each query is. */
async function fetchAgentContext(sb: SupabaseClient, userId: string): Promise<AgentContextInput> {
  const today = new Date().toISOString().slice(0, 10);

  // العيلة بتتجاب على خطوتين لأن family_members مالهاش عمود بيربط عضو بعضو مباشرة: الأول
  // نلاقي عضوية المستخدم عشان نعرف family_id، وبعدين نجيب كل أعضاء العيلة دي.
  const { data: myMembership } = await sb.from("family_members")
    .select("family_id").eq("user_id", userId).maybeSingle();
  const familyId = (myMembership as { family_id: string } | null)?.family_id ?? null;

  const [user, txs, inv, subs, obligations, debts, pharmacy, shopping, insights, tasbiha, memory, family, budget, domainObs, lifeObs] = await Promise.all([
    sb.from("zad_users").select("name,monthly_limit,currency,country").eq("id", userId).maybeSingle(),
    // The 200-newest window is what the prompt's "آخر 30 معاملة" section is sliced from.
    // It is no longer what any total is computed over — totals come from the RPC below,
    // which sees every row regardless of this limit, so a heavy month can no longer
    // silently under-report.
    sb.from("zad_transactions").select("title,amount,txn_kind,category,created_at")
      .eq("user_id", userId).order("created_at", { ascending: false }).limit(200),
    sb.from("zad_inventory").select("item_name,quantity,unit,expiry_date").eq("user_id", userId).limit(60),
    sb.from("zad_subscriptions").select("title,amount,renewal_date,is_active").eq("user_id", userId).limit(30),
    sb.from("zad_obligations").select("title,amount,due_date,status").eq("user_id", userId).limit(30),
    sb.from("zad_debts").select("name,remaining_balance,interest_rate,minimum_payment,due_day").eq("user_id", userId).eq("is_active", true).limit(30),
    sb.from("zad_pharmacy_items").select("name,remaining_quantity,unit,dosage").eq("user_id", userId).limit(30),
    sb.from("zad_shopping_list").select("item_name,is_purchased").eq("user_id", userId).limit(40),
    sb.from("zad_insights").select("title,body").eq("user_id", userId).eq("status", "pending").limit(8),
    sb.from("family_tasbiha").select("garden_name,tree_emoji,level,score,total_clicks,streak_days").eq("user_id", userId).limit(10),
    sb.from("zad_memory").select("scope,note").eq("user_id", userId).limit(20),
    familyId
      ? sb.from("family_members").select("role,alias,balance,savings_goal").eq("family_id", familyId).limit(20)
      : Promise.resolve({ data: [] as unknown[] }),
    // Phase 0 — the single authority for every budget figure the bot states. Shared with
    // the app's own screens and with zad-brain; see
    // migrations/20260809120000_single_budget_authority.sql.
    sb.rpc("zad_budget_state", { p_user: userId }),
    // نفس الملاحظات اللي العقل بيشوفها في buildSnapshot. من غيرها نفس السؤال بياخد
    // إجابة أغنى في التطبيق منها في تيليجرام — وده بالظبط التفاوت اللي اتقفل النهارده
    // في الذاكرة ورجع من هنا مع كل ملاحظة جديدة اتضافت.
    sb.rpc("zad_domain_observations", { p_user: userId }),
    sb.rpc("zad_lifestyle_observations", { p_user: userId }),
  ]);

  if ((budget as any)?.error) {
    console.error(`[zad-telegram-bot] zad_budget_state FAILED: ${String((budget as any).error.message ?? (budget as any).error)}`);
  }

  return {
    userName: (user.data as any)?.name ?? null,
    budget: ((budget as any)?.data ?? null) as any,
    // "غير معروف" بدل "ر.س" — كان افتراض ميت خلّى البوت يرد على عميل في مصر "مفيش
    // ولا ريال" وهو فلوسه بالمصري. لو العمود موجود، قيمته الحقيقية (EGP/SAR/TRY)
    // هي اللي بتوصل من الكلاينت (MarketPrefs → syncMarketProfile).
    currency: (user.data as any)?.currency ?? "غير معروف",
    country: (user.data as any)?.country ?? null,
    today,
    family: (family.data ?? []) as any,
    transactions: (txs.data ?? []) as any,
    inventory: (inv.data ?? []) as any,
    subscriptions: (subs.data ?? []) as any,
    obligations: (obligations.data ?? []) as any,
    debts: (debts.data ?? []) as any,
    pharmacy: (pharmacy.data ?? []) as any,
    shopping: (shopping.data ?? []) as any,
    insights: (insights.data ?? []) as any,
    tasbiha: (tasbiha.data ?? []) as any,
    memory: (memory.data ?? []) as any,
    // الاتنين بيتلموا في مصفوفة واحدة: الموديل مايهموش الملاحظة جت من أنهي دالة، يهمه
    // إيه اللي محتاج تصرّف. الترتيب بالأهمية بيحصل في buildAgentContext.
    observations: ([...(domainObs.data ?? []), ...(lifeObs.data ?? [])]) as any,
  };
}

/** Routes through zad-core-intelligence's `ai_text` rather than calling Groq directly,
 * so the bot inherits the exact same multi-key Groq pool + Gemini fallback the app uses
 * — one provider policy, not a second one drifting out of sync here. */
async function askZad(systemPrompt: string, userPrompt: string): Promise<string | null> {
  try {
    const res = await fetch(`${SUPABASE_URL}/functions/v1/zad-core-intelligence`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${SERVICE_ROLE_KEY}` },
      body: JSON.stringify({ action: "ai_text", payload: { system_prompt: systemPrompt, user_prompt: userPrompt } }),
    });
    if (!res.ok) {
      console.error("askZad: core-intelligence returned", res.status);
      return null;
    }
    const json = await res.json();
    return json?.ok === false ? null : (json?.text ?? null);
  } catch (e) {
    console.error("askZad failed:", e);
    return null;
  }
}

/** أداة اتنفذت فعلاً سيرفر-سايد، أو اقتراح مالي مستني تأكيد — نفس شكل رد agent_turn. */
interface AgentExecuted { tool: string; summary: string }
interface AgentProposal { tool: string; summary: string; input: Record<string, unknown> }
interface AgentTurnResult {
  ok: boolean;
  reply: string;
  executed: AgentExecuted[];
  proposals: AgentProposal[];
}

/**
 * المرحلة ٢-د — لفة محادثة عبر zad-brain باستدعاء أدوات حقيقي.
 *
 * بتحل محل برومبتات تصنيف النية المنفصلة (spendIntentPrompt/medicationIntentPrompt) اللي
 * كانت بتشوف الرسالة تلات مرات بتلات أسئلة ضيقة. دلوقتي الموديل شايف الرسالة مرة واحدة
 * ومعاه كل الأدوات، فرسالة زي "صرفت ٥٠ بقالة وضيف لبن للمخزون" بتتعامل كاملة بدل ما
 * تتقسم على مسارين مايعرفوش بعض.
 *
 * `user_id` في الجسم مقبول هنا لأن النداء بمفتاح service-role — راجع resolveAuthedUserId
 * في zad-brain. الهوية نفسها جاية من telegram_bindings، مش من أي حاجة العميل بيدّعيها.
 */
/**
 * Turns an internal failure string into something a customer should actually read.
 *
 * The reason strings themselves are diagnostics — "zad-brain rejected the turn (ok:false)",
 * "gemini 503: {...}", raw HTTP bodies. Those were being pasted straight into the chat, so
 * on 2026-08-15 the customer's Telegram window was a wall of English stack-talk instead of
 * an answer. Telling them the write didn't happen is right and stays; naming our internal
 * component and quoting the upstream JSON at them is not, and it also leaks how the
 * backend is wired to anyone who talks to the bot.
 *
 * The full `reason` still goes to console.error at every call site, so nothing is lost for
 * debugging — it just stops being the customer's problem.
 */
function userFacingFailure(reason: string): string {
  const r = reason.toLowerCase();
  if (r.includes("429") || r.includes("quota") || r.includes("exhausted")) {
    return "الخدمة الذكية واصلة حد الاستخدام دلوقتي";
  }
  if (r.includes("503") || r.includes("unavailable") || r.includes("high demand") || r.includes("overload")) {
    return "الخدمة الذكية زحمة دلوقتي";
  }
  if (r.includes("timeout") || r.includes("timed out") || r.includes("abort")) {
    return "الرد أخد وقت أطول من اللازم";
  }
  return "الخدمة الذكية مش متاحة دلوقتي";
}

/** errorReason is set only when the agent path failed and the caller fell back to the
 * read-only prose reply — it's what tells the Telegram user (and the logs) why their
 * "عدّل"/"ذكرني" request silently became a plain answer instead of an executed action.
 * It is an internal string: pass it through [userFacingFailure] before it reaches a chat. */
async function agentTurn(userId: string, message: string): Promise<{ result: AgentTurnResult | null; errorReason?: string }> {
  try {
    const res = await fetch(`${SUPABASE_URL}/functions/v1/zad-brain`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${SERVICE_ROLE_KEY}` },
      // source: "telegram" — تصنيف عرضي بس لـ agent_actions (أي فعل يتنفّذ يتوسم إنه
      // جه من القناة دي)، مش أداة أمان: الهوية أصلاً محسومة بمفتاح service-role +
      // userId من telegram_bindings، مش من الحقل ده.
      body: JSON.stringify({ action: "agent_turn", user_id: userId, message, source: "telegram" }),
    });
    if (!res.ok) {
      const bodyText = await res.text().catch(() => "");
      const reason = `zad-brain HTTP ${res.status}${bodyText ? `: ${bodyText.slice(0, 200)}` : ""}`;
      console.error("agentTurn: zad-brain returned", res.status, bodyText);
      return { result: null, errorReason: reason };
    }
    const json = await res.json() as AgentTurnResult;
    if (json?.ok === false) {
      console.error("agentTurn: zad-brain replied ok:false", json);
      return { result: null, errorReason: "zad-brain rejected the turn (ok:false)" };
    }
    return { result: json };
  } catch (e) {
    const reason = e instanceof Error ? e.message : String(e);
    console.error("agentTurn failed:", e);
    return { result: null, errorReason: reason };
  }
}

/**
 * Telegram never writes a confirmed financial operation itself.  The pending row is
 * only a UI hand-off for the Telegram button; the actual mutation must go back to
 * zad-brain so it gets the same validation, audit trail, and budget side effects as
 * an approval from the in-app chat.
 */
async function agentConfirm(userId: string, tool: string, input: Record<string, unknown>): Promise<{ ok: boolean; summary?: string }> {
  try {
    const res = await fetch(`${SUPABASE_URL}/functions/v1/zad-brain`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${SERVICE_ROLE_KEY}` },
      body: JSON.stringify({ action: "agent_confirm", user_id: userId, tool, input, source: "telegram" }),
    });
    if (!res.ok) {
      console.error("agentConfirm: zad-brain returned", res.status, await res.text().catch(() => ""));
      return { ok: false };
    }
    const data = await res.json() as { ok?: boolean; summary?: string };
    return { ok: data.ok === true, summary: data.summary };
  } catch (error) {
    console.error("agentConfirm failed:", error);
    return { ok: false };
  }
}

/** Runs a deterministic, non-financial household tool through the shared brain.
 * OCR/voice handlers only extract fields; they never mutate inventory or pharmacy
 * tables themselves, which keeps learning observations and the audit trail unified. */
async function agentExecute(userId: string, tool: string, input: Record<string, unknown>): Promise<{ ok: boolean; summary?: string }> {
  try {
    const res = await fetch(`${SUPABASE_URL}/functions/v1/zad-brain`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${SERVICE_ROLE_KEY}` },
      body: JSON.stringify({ action: "agent_execute", user_id: userId, tool, input, source: "telegram" }),
    });
    if (!res.ok) {
      console.error("agentExecute: zad-brain returned", res.status, await res.text().catch(() => ""));
      return { ok: false };
    }
    const data = await res.json() as { ok?: boolean; summary?: string };
    return { ok: data.ok === true, summary: data.summary };
  } catch (error) {
    console.error("agentExecute failed:", error);
    return { ok: false };
  }
}

/** Generic version of askZad's fetch for any zad-core-intelligence action (voice_agent,
 * analyze_receipt, ...) that returns a structured JSON body rather than a plain string. */
async function callCoreIntelligence<T>(action: string, payload: Record<string, unknown>): Promise<T | null> {
  try {
    const res = await fetch(`${SUPABASE_URL}/functions/v1/zad-core-intelligence`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${SERVICE_ROLE_KEY}` },
      body: JSON.stringify({ action, payload }),
    });
    if (!res.ok) {
      console.error(`callCoreIntelligence: ${action} returned`, res.status);
      return null;
    }
    return await res.json() as T;
  } catch (e) {
    console.error(`callCoreIntelligence: ${action} failed:`, e);
    return null;
  }
}

/** Telegram file download is a two-step dance: resolve file_id → file_path via getFile,
 * then GET the actual bytes from the file/ CDN host. Both calls use the bot token, not the
 * webhook secret — this is Telegram's own API, unrelated to inbound webhook auth. */
async function downloadTelegramFileBytes(fileId: string): Promise<ArrayBuffer | null> {
  try {
    const infoRes = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/getFile?file_id=${fileId}`);
    const info = await infoRes.json();
    const filePath = info?.result?.file_path;
    if (!filePath) {
      console.error("downloadTelegramFileBytes: getFile returned no file_path", JSON.stringify(info));
      return null;
    }
    const fileRes = await fetch(`https://api.telegram.org/file/bot${BOT_TOKEN}/${filePath}`);
    if (!fileRes.ok) {
      console.error("downloadTelegramFileBytes: file download HTTP", fileRes.status);
      return null;
    }
    return await fileRes.arrayBuffer();
  } catch (e) {
    console.error("downloadTelegramFileBytes failed:", e);
    return null;
  }
}

/** Chunked to avoid a call-stack overflow from String.fromCharCode(...bytes) on a large
 * array — voice notes/photos are small (KB, not MB) but no reason to rely on that. */
function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = "";
  const chunkSize = 0x8000;
  for (let i = 0; i < bytes.length; i += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunkSize));
  }
  return btoa(binary);
}

interface VoiceAgentResult {
  action: "chat" | "add_expense" | "add_income" | "check_budget" | "add_inventory" | "log_pharmacy_dose" | "add_pharmacy";
  message: string;
  data: {
    amount?: number; title?: string; category?: string;
    dosage?: string; daily_dose_count?: number; dose_times?: string; unit?: string;
  } | null;
  transcript: string;
}

interface AnalyzeReceiptResult {
  total: number;
  category: string;
  storeName: string;
  // "pharmacy" | "grocery" | "general" — see zad-core-intelligence's analyze_receipt.
  receiptType: string;
  items: Array<{ name: string; price: number; quantity: number; unit: string; category: string }>;
}

// Constructed with a syntactically-valid placeholder when the token is missing so the
// module still loads and the GET probe can explain the misconfiguration. No request is
// ever routed to this bot in that state — Deno.serve short-circuits below.
const bot = new Bot(BOT_CONFIGURED ? BOT_TOKEN : "0:placeholder");

bot.command("start", async (ctx) => {
  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const chatId = ctx.chat.id;
  const code = normalizeBindingCode(ctx.match as string | undefined);

  if (!code) {
    const userId = await resolveUserId(sb, chatId);
    if (userId) {
      await ctx.reply("اختار من تحت:", { reply_markup: toGrammyKeyboard(mainMenuKeyboard()) });
    } else {
      await ctx.reply("أهلاً! لو عندك كود ربط من تطبيق زاد ابعته كده: /start الكود");
    }
    return;
  }

  const { data: link } = await sb.from("telegram_bindings")
    .select("id,code_expires_at")
    .eq("binding_code", code)
    .is("bound_at", null)
    .maybeSingle();
  const expired = !link || new Date((link as any).code_expires_at) < new Date();
  if (expired) {
    await ctx.reply("الكود ده غلط أو منتهي — افتح تطبيق زاد واعمل كود ربط جديد.");
    return;
  }

  const { error } = await sb.from("telegram_bindings")
    .update({ chat_id: chatId, bound_at: new Date().toISOString() })
    .eq("id", (link as any).id);
  if (error) {
    // الأرجح unique violation على chat_id (الحساب ده مربوط بيوزر تاني بالفعل)
    await ctx.reply("فشل الربط — الحساب ده ممكن يكون مربوط بيوزر تاني بالفعل.");
  } else {
    await ctx.reply("تم الربط بنجاح ✅ اختار من تحت:", { reply_markup: toGrammyKeyboard(mainMenuKeyboard()) });
  }
});

bot.command("menu", async (ctx) => {
  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const userId = await resolveUserId(sb, ctx.chat.id);
  if (!userId) {
    await ctx.reply("أهلاً! لو عندك كود ربط من تطبيق زاد ابعته كده: /start الكود");
    return;
  }
  await ctx.reply("اختار من تحت، أو اسألني أي حاجة بالكلام العادي:", {
    reply_markup: toGrammyKeyboard(mainMenuKeyboard()),
  });
});

/** التحليل الكامل — نفس بيانات الشات، بس السؤال جاهز، عشان العميل ياخد قراءة شاملة
 * من غير ما يكتب سؤال. */
bot.command("tahlil", async (ctx) => {
  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const userId = await resolveUserId(sb, ctx.chat.id);
  if (!userId) {
    await ctx.reply("الحساب ده مش مربوط — افتح تطبيق زاد واعمل كود ربط.");
    return;
  }
  await ctx.replyWithChatAction("typing");
  // كان بيروح مباشرة لسياق منفصل (fetchAgentContext + askZad) بدل agentTurn — نفس
  // مسار الرسالة النصية العادية تحت، فالتحليل ممكن يختلف عن رد الشات العادي لأنهم
  // مبنيين من دالتين مختلفتين لنفس الصورة. دلوقتي بيفضّل الوكيل الموحد الأول، ونفس
  // fallback بس لو فشل — مش تنفيذ جديد، نفس البنية.
  const analysisPrompt = "اعملي تحليل سريع لوضعي المالي وحالة البيت: أهم ٣ ملاحظات، وأهم حاجة أعملها دلوقتي.";
  const { result: turn, errorReason } = await agentTurn(userId, analysisPrompt);
  if (turn?.reply.trim()) {
    await ctx.reply(clampForTelegram(turn.reply.trim()));
    return;
  }
  if (errorReason) console.error("photo analysis fell back to read-only:", errorReason);
  const notice = errorReason
    ? `⚠️ ${userFacingFailure(errorReason)}، فده تحليل مبدئي من البيانات المسجّلة:\n\n`
    : "";
  const context = buildAgentContext(await fetchAgentContext(sb, userId));
  const answer = await askZad(
    agentSystemPrompt(),
    `${context}\n\n=== سؤال العميل ===\n${analysisPrompt}`,
  );
  await ctx.reply(answer ? clampForTelegram(notice + answer) : clampForTelegram(notice + "معلش، التحليل مش متاح دلوقتي — جرب كمان شوية."));
});

// المحادثة الحقيقية — أي كلام عادي بيروح لزاد بنفس السياق والشخصية بتوع الشات
// اللي جوه التطبيق، مش رد ثابت بقائمة أزرار زي النسخة الأولى.
/**
 * لفة الوكيل + تجهيز الرد، من غير أي اعتماد على نوع الرسالة اللي جابت النص.
 * اتفصلت عن `bot.on("message:text")` عشان الصوت يقدر يستخدم **نفس** اللفة بالظبط:
 * كان الصوت بيعدي على `voice_agent` اللي بيعرف ٧ أفعال، والنص بياخد ٣٦ أداة — يعني
 * فويس نوت بتقول "اشترك نتفليكس ١٠٠ في الشهر" مكانش ليها أي طريق تتسجل كاشتراك.
 * القناة مالهاش لازمة تحدد قدرات الوكيل.
 *
 * بترجّع null بس لما اللفة نفسها تقع (نت/موديل/مهلة) — ساعتها المنادي بيقع على
 * الرد القرائي، وبيقول للعميل صراحةً إن التنفيذ ماحصلش.
 */
async function agentTurnReply(
  sb: SupabaseClient,
  userId: string,
  chatId: number,
  text: string,
): Promise<{ lines: string[]; pendingId?: string; toolPendingId?: string; errorReason?: string }> {
  const { result: turn, errorReason } = await agentTurn(userId, text);
  if (!turn) return { lines: [], errorReason: errorReason ?? "agent turn unavailable" };

  const lines: string[] = [];
  if (turn.reply.trim()) lines.push(turn.reply.trim());
  for (const done of turn.executed) lines.push(`✅ ${isolate(sanitizeName(done.summary))}`);

  const money = turn.proposals.find((p) => p.tool === "log_transaction");
  const rest = turn.proposals.filter((p) => p !== money);

  // كان: `ℹ️ … — ابعتها لوحدها عشان أأكدها معاك` — والعميل أصلاً باعتها لوحدها،
  // فالسطر يتكرر للأبد ومفيش زرار. الاقتراح دلوقتي بياخد صف في telegram_pending_tools
  // وزر تأكيد حقيقي. رسالة تليجرام الواحدة بتشيل كيبورد واحد، فأول اقتراح غير مالي هو
  // اللي بياخد الزرار والباقي بيتقال بصراحة إنه محتاج رسالة لوحده.
  let toolPendingId: string | undefined;
  for (const extra of rest) {
    if (toolPendingId) {
      lines.push(`ℹ️ ${extra.summary} — ابعتها في رسالة لوحدها عشان أقدر أحط ليها زر تأكيد.`);
      continue;
    }
    const { data: row, error } = await sb.from("telegram_pending_tools").insert({
      user_id: userId,
      chat_id: chatId,
      tool: extra.tool,
      input: extra.input,
      summary: extra.summary,
    }).select("id").single();
    if (error || !row) {
      console.error("pending tool insert failed:", error);
      lines.push(`ℹ️ ${extra.summary} — معلش، مقدرتش أجهّز التأكيد. جرب تاني.`);
      continue;
    }
    lines.push(`⚠️ ${extra.summary}\n\nأأكدها؟`);
    toolPendingId = (row as { id: string }).id;
  }

  // الفلوس ليها الأولوية على الزرار الواحد — تأكيد معاملة أخطر من تأكيد أداة تانية.
  if (!money) return { lines, toolPendingId };

  const amount = Number(money.input.amount);
  const kind = money.input.txn_kind === "income" ? "income" : "expense";
  const title = String(money.input.title ?? "مصروف").slice(0, 80);
  const category = String(money.input.category ?? "أخرى").slice(0, 40);
  const currency = (await sb.from("zad_users").select("currency").eq("id", userId).maybeSingle())
    .data?.currency ?? "غير معروف";
  const { data: pending, error } = await sb.from("telegram_pending_writes").insert({
    user_id: userId,
    chat_id: chatId,
    txn_kind: kind,
    amount,
    title,
    category,
    confidence: null,
  }).select("id").single();

  if (error || !pending) {
    console.error("pending write insert failed:", error);
    lines.push("معلش، مقدرتش أجهّز تأكيد المصروف — جرب تاني.");
    return { lines };
  }

  lines.push(confirmSpendMessage({ is_spend: true, kind, amount, title, category, confidence: 1 }, currency));
  return { lines, pendingId: (pending as { id: string }).id };
}

bot.on("message:text", async (ctx) => {
  if (ctx.message.text.startsWith("/")) return; // أوامر متسجلة فوق بتتعامل لوحدها
  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const userId = await resolveUserId(sb, ctx.chat.id);
  if (!userId) {
    await ctx.reply("أهلاً! لو عندك كود ربط من تطبيق زاد ابعته كده: /start الكود");
    return;
  }

  await ctx.replyWithChatAction("typing");

  // المرحلة ٢-د — نداء واحد بكل الأدوات، بدل تلات مرات تصنيف نية منفصلة. الأدوات
  // المباشرة (مخزون/صيدلية/تسوق/بلد وعملة) بتكون اتنفذت خلاص لما الرد ده يوصل؛ أدوات
  // الفلوس بترجع كاقتراح لسه ماحصلش، وبيتحوّل لنفس زر التأكيد الموجود من الأول.
  const turnReply = await agentTurnReply(sb, userId, ctx.chat.id, ctx.message.text);
  const errorReason = turnReply.errorReason;

  if (turnReply.lines.length > 0) {
    const body = clampForTelegram(turnReply.lines.join("\n\n"));
    if (turnReply.pendingId) {
      await ctx.reply(body, { reply_markup: toGrammyKeyboard(confirmSpendKeyboard(turnReply.pendingId)) });
    } else if (turnReply.toolPendingId) {
      await ctx.reply(body, { reply_markup: toGrammyKeyboard(confirmToolKeyboard(turnReply.toolPendingId)) });
    } else {
      await ctx.reply(body);
    }
    return;
  }

  // fallback: الوكيل مش متاح (نت/موديل/مهلة) — الرد القرائي القديم أحسن من صمت.
  // بيتشال في المرحلة ٢-هـ بعد ما agent_turn يثبت نفسه على مستخدمين حقيقيين.
  //
  // errorReason بيبقى موجود بس هنا (turn === null) — لو أي أمر تنفيذي (عدّل/ذكرني/ضيف)
  // وقع على المسار ده، لازم العميل يعرف إنه رد قراءة بس ومحصلش تنفيذ فعلي، بدل ما يفتكر
  // إن التعديل اتسجل وهو ماتسجلش. صمت هنا هو بالظبط الشكوى اللي البلاغ ده بيوصفها.
  if (errorReason) console.error("agent turn fell back to read-only:", errorReason);
  const notice = errorReason
    ? `⚠️ ${userFacingFailure(errorReason)}. اللي تحت رد قراءة من بياناتك المسجّلة — لو كنت طالب تعديل أو إضافة أو تذكير، **هو ماتسجّلش**، جرب تاني كمان شوية.\n\n`
    : "";

  const context = buildAgentContext(await fetchAgentContext(sb, userId));
  const answer = await askZad(
    agentSystemPrompt(),
    `${context}\n\n=== سؤال العميل ===\n${ctx.message.text}`,
  );

  if (answer) {
    await ctx.reply(clampForTelegram(notice + answer));
  } else {
    await ctx.reply(clampForTelegram(notice + "معلش، مش قادر أرد دلوقتي — جرب تاني كمان شوية، أو اختار من القائمة:"), {
      reply_markup: toGrammyKeyboard(mainMenuKeyboard()),
    });
  }
});

// رسالة صوتية — نفس فكرة رسالة الكتابة العادية، بس بعد تفريغ الصوت لنص عبر
// zad-core-intelligence's voice_agent (Whisper + استخراج نية بخطوة واحدة). أي صرف/دخل
// برضه بيعدي على نفس تأكيد الكتابة العادية (telegram_pending_writes + زر تأكيد) — مفيش
// كتابة مباشرة في zad_transactions من صوت متسمعش صح، نفس قاعدة الأمان بتاعة النص.
// إضافة مخزون بس هي اللي بتتكتب مباشرة (نفس فلسفة مسح الكاميرا في التطبيق: مخزون خطره
// أقل بكتير من فلوس حقيقية في الدفتر).
bot.on("message:voice", async (ctx) => {
  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const userId = await resolveUserId(sb, ctx.chat.id);
  if (!userId) {
    await ctx.reply("أهلاً! لو عندك كود ربط من تطبيق زاد ابعته كده: /start الكود");
    return;
  }
  await ctx.replyWithChatAction("typing");

  const bytes = await downloadTelegramFileBytes(ctx.message.voice.file_id);
  if (!bytes) {
    await ctx.reply("معلش، مقدرتش أنزّل الرسالة الصوتية — جرب تاني.");
    return;
  }

  const result = await callCoreIntelligence<VoiceAgentResult>("voice_agent", {
    audio_base64: arrayBufferToBase64(bytes),
    mime_type: ctx.message.voice.mime_type || "audio/ogg",
  });
  if (!result || !result.transcript) {
    await ctx.reply("معلش، مسمعتش كلام واضح في الرسالة الصوتية — جرب تاني.");
    return;
  }
  const heard = `🎤 "${isolate(sanitizeName(result.transcript))}"\n\n`;

  // كان هنا dispatch يدوي على `result.action` — ٧ أفعال بس (مصروف/دخل/مخزون/دوا/
  // ميزانية/جرعة/دردشة)، بينما نفس الجملة مكتوبة كانت بتوصل لـ٣٦ أداة. الفرق مكانش
  // في الأمان، كان في إن الصوت اتبنى قبل لفة الوكيل ومحدش رجع وصّله بيها: فويس نوت
  // بتقول "اشترك نتفليكس ١٠٠ في الشهر" مكانش ليها أي طريق تتسجل كاشتراك.
  //
  // التفريغ هو الحاجة الوحيدة اللي محتاجينها من voice_agent دلوقتي. النص الناتج بيدخل
  // نفس agentTurnReply اللي الكتابة بتدخله — نفس الأدوات، نفس التحقق، نفس سجل التدقيق،
  // ونفس زر التأكيد على الفلوس. مفيش مسار كتابة تاني اتفتح هنا.
  const turnReply = await agentTurnReply(sb, userId, ctx.chat.id, result.transcript);

  if (turnReply.lines.length > 0) {
    const body = clampForTelegram(heard + turnReply.lines.join("\n\n"));
    if (turnReply.pendingId) {
      await ctx.reply(body, { reply_markup: toGrammyKeyboard(confirmSpendKeyboard(turnReply.pendingId)) });
    } else if (turnReply.toolPendingId) {
      await ctx.reply(body, { reply_markup: toGrammyKeyboard(confirmToolKeyboard(turnReply.toolPendingId)) });
    } else {
      await ctx.reply(body);
    }
    return;
  }

  // اللفة نفسها وقعت. الرسالة الصوتية دايماً بتبقى طلب — الصمت أو "تمام" هنا بيخلي
  // العميل يفتكر إن اللي قاله اتسجل، وهو ماتسجلش.
  if (turnReply.errorReason) console.error("voice agent turn failed:", turnReply.errorReason);
  await ctx.reply(clampForTelegram(
    heard + `⚠️ ${userFacingFailure(turnReply.errorReason ?? "")}. اللي قلته **ماتسجّلش** — جرب تبعته تاني كمان شوية.`,
  ));
});

// صورة (فاتورة أو صنف) — نفس مبدأ التسجيل الصوتي: الأصناف بتتضاف للمخزون مباشرة (زي
// مسح الكاميرا في التطبيق)، أي مبلغ إجمالي مقروء من الفاتورة بيعدي على نفس تأكيد
// الكتابة العادية قبل ما يتسجل في zad_transactions.
bot.on("message:photo", async (ctx) => {
  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const userId = await resolveUserId(sb, ctx.chat.id);
  if (!userId) {
    await ctx.reply("أهلاً! لو عندك كود ربط من تطبيق زاد ابعته كده: /start الكود");
    return;
  }
  await ctx.replyWithChatAction("typing");

  // آخر عنصر في مصفوفة PhotoSize دايماً أعلى دقة بعتها تليجرام (الترتيب تصاعدي مضمون)
  const sizes = ctx.message.photo;
  const largest = sizes[sizes.length - 1];
  const bytes = await downloadTelegramFileBytes(largest.file_id);
  if (!bytes) {
    await ctx.reply("معلش، مقدرتش أنزّل الصورة — جرب تاني.");
    return;
  }

  const result = await callCoreIntelligence<AnalyzeReceiptResult>("analyze_receipt", {
    image_base64: arrayBufferToBase64(bytes),
    mime_type: "image/jpeg", // تليجرام بيضغط صور الـ photo دايماً JPEG
  });
  if (!result || (result.items.length === 0 && (!result.total || result.total <= 0))) {
    await ctx.reply("معلش، مقدرتش أقرا حاجة واضحة في الصورة دي — جرب صورة أوضح.");
    return;
  }

  // كارت ميزانية/رصيد (سكرين شوت راتب أو رصيد حساب، مش فاتورة مقاضي فعلية): مفيش أصناف
  // نحقنها، ومفيش مصروف نسجله — كتابة monthly_limit من رقم OCR بدون تأكيد صريح خطر (رقم
  // غلط بيكسر كل حسابات الميزانية). أقصى حاجة آمنة: نعرض الرقم اللي اتقرا ونوجّه المستخدم
  // يأكده بجملة عادية في الشات، اللي عنده مسار تأكيد فعلي بالفعل (voice_agent/chat actions).
  if (result.receiptType === "budget_card") {
    // مفيش مسار كتابة لـ monthly_limit من الشات/الصوت حالياً (check_budget قراءة بس) —
    // مينفعش نعد المستخدم بأمر نصي بيسجلها، فبس نوضح إنها مش فاتورة ونوجهه للتطبيق.
    const amountHint = result.total > 0
      ? `قريت رقم ${result.total} في الصورة دي، بس شكلها كارت رصيد أو راتب مش فاتورة مقاضي — مقريتش منها أصناف. لو عايز تحدد ميزانيتك، ده من تطبيق زاد.`
      : "الصورة دي شكلها كارت رصيد أو راتب مش فاتورة، فمقريتش منها أصناف.";
    await ctx.reply(amountHint);
    return;
  }

  // OCR only supplies fields. Both pharmacy and inventory mutations are executed by
  // zad-brain; the receipt total still waits for the normal financial confirmation.
  if (result.receiptType === "pharmacy" && result.items.length > 0) {
    let addedCount = 0;
    for (const item of result.items) {
      if (!item.name?.trim()) continue;
      const qty = Number.isFinite(item.quantity) && item.quantity > 0 ? Math.round(item.quantity) : 1;
      const added = await agentExecute(userId, "add_pharmacy_item", {
        name: item.name.trim(),
        quantity: qty,
        unit: item.unit || "قرص",
        category: "عام",
      });
      if (!added.ok) {
        console.error("photo pharmacy through zad-brain failed");
        continue;
      }
      addedCount++;
    }
    const summary = addedCount > 0
      ? `✅ اتضاف ${addedCount} صنف للصيدلية${result.storeName ? ` من ${result.storeName}` : ""}.`
      : "";
    if (result.total > 0) {
      const currency = (await sb.from("zad_users").select("currency").eq("id", userId).maybeSingle()).data?.currency ?? "غير معروف";
      const title = (result.storeName || "فاتورة صيدلية").slice(0, 80);
      const { data: pending, error } = await sb.from("telegram_pending_writes").insert({
        user_id: userId, chat_id: ctx.chat.id, txn_kind: "expense", amount: Math.round(result.total * 100) / 100,
        title, category: "الرعاية الصحية", confidence: 0.75,
      }).select("id").single();
      if (!error && pending) {
        await ctx.reply(`${summary}${summary ? "\n\n" : ""}` + confirmSpendMessage({ is_spend: true, kind: "expense", amount: result.total, title, category: "الرعاية الصحية", confidence: 0.75 }, currency), {
          reply_markup: toGrammyKeyboard(confirmSpendKeyboard((pending as { id: string }).id)),
        });
        return;
      }
    }
    await ctx.reply(summary || "معلش، ملقتش أصناف واضحة في الصورة دي.");
    return;
  }

  let addedCount = 0;
  for (const item of result.items) {
    if (!item.name?.trim()) continue;
    const qty = Number.isFinite(item.quantity) && item.quantity > 0 ? Math.round(item.quantity) : 1;
    const added = await agentExecute(userId, "add_inventory_item", {
      item_name: item.name.trim(),
      quantity: qty,
      unit: item.unit || "قطعة",
      category: item.category || null,
    });
    if (added.ok) {
      addedCount++;
    } else {
      console.error("photo inventory through zad-brain failed");
    }
  }
  const itemsSummary = addedCount > 0
    ? `✅ اتضاف ${addedCount} صنف للمخزون${result.storeName ? ` من ${result.storeName}` : ""}.`
    : "";

  if (result.total > 0) {
    const currency = (await sb.from("zad_users").select("currency").eq("id", userId).maybeSingle())
      .data?.currency ?? "غير معروف";
    const title = (result.storeName || "فاتورة").slice(0, 80);
    const category = (result.category || "أخرى").slice(0, 40);
    const { data: pending, error } = await sb.from("telegram_pending_writes").insert({
      user_id: userId,
      chat_id: ctx.chat.id,
      txn_kind: "expense",
      amount: Math.round(result.total * 100) / 100,
      title,
      category,
      confidence: 0.75,
    }).select("id").single();
    if (!error && pending) {
      await ctx.reply(
        (itemsSummary ? itemsSummary + "\n\n" : "") +
          confirmSpendMessage({ is_spend: true, kind: "expense", amount: result.total, title, category, confidence: 0.75 }, currency),
        { reply_markup: toGrammyKeyboard(confirmSpendKeyboard((pending as { id: string }).id)) },
      );
      return;
    }
    console.error("photo pending write insert failed:", error);
  }

  await ctx.reply(itemsSummary || "معلش، ملقتش أصناف ولا مبلغ واضح في الصورة دي.");
});

bot.on("callback_query:data", async (ctx) => {
  await ctx.answerCallbackQuery();
  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const chatId = ctx.chat?.id;
  if (!chatId) return;

  const userId = await resolveUserId(sb, chatId);
  if (!userId) {
    await ctx.reply("الحساب ده مش مربوط — افتح تطبيق زاد واعمل كود ربط.");
    return;
  }

  const data = ctx.callbackQuery.data;

  // تأكيد أداة غير مالية (tx:/tc:) — نفس بروتوكول تأكيد الفلوس بالظبط: نقرا الصف،
  // نتأكد إنه بتاع نفس المستخدم، ما اتصرفش فيه قبل كده، وما انتهتش صلاحيته؛ ندّعيه
  // بتحديث مشروط عشان ضغطتين سريعتين ما ينفذوش الأداة مرتين؛ وبعدين ننفذ عن طريق
  // zad-brain نفسه (agent_confirm) مش من هنا — تليجرام مابيكتبش عملية مؤكدة بنفسه
  // أبداً، عشان التحقق وسجل التدقيق يفضلوا في مكان واحد.
  const toolCallback = parseToolCallback(data);
  if (toolCallback) {
    const { data: pendingRow } = await sb.from("telegram_pending_tools")
      .select("id,user_id,tool,input,summary,status,expires_at")
      .eq("id", toolCallback.pendingId)
      .maybeSingle();
    const row = pendingRow as {
      id: string; user_id: string; tool: string; input: Record<string, unknown>;
      summary: string; status: string; expires_at: string;
    } | null;

    if (!row || row.user_id !== userId) {
      await ctx.reply("الطلب ده مش موجود.");
      return;
    }
    if (row.status !== "pending") {
      await ctx.reply("الطلب ده اتصرف فيه خلاص.");
      return;
    }
    if (toolCallback.action === "cancel") {
      await sb.from("telegram_pending_tools").update({ status: "cancelled" }).eq("id", row.id);
      await ctx.reply("تمام، ملغي.");
      return;
    }
    if (new Date(row.expires_at).getTime() < Date.now()) {
      await sb.from("telegram_pending_tools").update({ status: "cancelled" }).eq("id", row.id);
      await ctx.reply("الطلب ده عدى عليه وقت طويل — ابعته تاني لو لسه عايزه.");
      return;
    }

    const { error: claimError } = await sb.from("telegram_pending_tools")
      .update({ status: "confirmed" })
      .eq("id", row.id)
      .eq("status", "pending");
    if (claimError) {
      await ctx.reply("معلش، حصلت مشكلة — جرب تاني.");
      return;
    }

    const done = await agentConfirm(userId, row.tool, row.input);
    if (!done.ok) {
      // رجّع الصف لـ pending: الأداة ماتنفذتش، فالعميل لازم يقدر يضغط تأكيد تاني
      // بدل ما الطلب يفضل "مؤكد" وهو محصلش — نفس تصرف تأكيد الفلوس.
      await sb.from("telegram_pending_tools").update({ status: "pending" }).eq("id", row.id);
      await ctx.reply("معلش، مقدرتش أنفذها — جرب تاني كمان شوية.");
      return;
    }
    await ctx.reply(`✅ ${isolate(sanitizeName(done.summary ?? row.summary))}`);
    return;
  }

  if (data === "b") {
    // Phase 0 — the same RPC row the app's budget card and zad-brain read. This button
    // used to run its own calendar-month sum AND blank the ceiling whenever
    // limit_confirmed_at was null, which is null on accounts that do hold a real
    // monthly_limit (AGENT_GAP_ANALYSIS.md §6) — so it told those customers their budget
    // was 0. Both behaviours are gone: one query, one formula, salary-cycle aware.
    const { data: state, error } = await sb.rpc("zad_budget_state", { p_user: userId });
    if (error || !state) {
      console.error(`[zad-telegram-bot] /balance zad_budget_state FAILED: ${String(error?.message ?? error)}`);
      await ctx.reply("مقدرتش أحسب الرصيد دلوقتي — جرب تاني بعد شوية.");
      return;
    }
    const { data: user } = await sb.from("zad_users").select("currency").eq("id", userId).maybeSingle();
    await ctx.reply(formatBalanceMessage(state as BudgetStateRow, (user as any)?.currency ?? ""));
    return;
  }

  if (data === "t") {
    const { data: txs } = await sb.from("zad_transactions")
      .select("title,amount,txn_kind,created_at")
      .eq("user_id", userId)
      .order("created_at", { ascending: false })
      .limit(10);
    await ctx.reply(formatTransactionsMessage((txs ?? []) as any));
    return;
  }

  if (data === "i") {
    const { data: insights } = await sb.from("zad_insights")
      .select("id,title,body")
      .eq("user_id", userId)
      .eq("status", "pending")
      .limit(5);
    const rows = (insights ?? []) as Array<{ id: string; title: string; body: string }>;
    if (rows.length === 0) {
      await ctx.reply("مفيش تنبيهات معلقة دلوقتي 👍");
    } else {
      for (const insight of rows) {
        await ctx.reply(formatInsightTitle(insight), { reply_markup: toGrammyKeyboard(dismissKeyboard(insight.id)) });
      }
    }
    return;
  }

  // تأكيد/إلغاء تسجيل مصروف. الكتابة الوحيدة في zad_transactions بتحصل هنا بس،
  // بعد ضغطة تأكيد صريحة من العميل.
  const spend = parseSpendCallback(data);
  if (spend) {
    const { data: pendingRow } = await sb.from("telegram_pending_writes")
      .select("id,user_id,txn_kind,amount,title,category,status,expires_at")
      .eq("id", spend.pendingId)
      // إعادة التحقق: الـ chat اللي بيأكد لازم يكون لسه مربوط بنفس اليوزر صاحب الطلب.
      .eq("user_id", userId)
      .maybeSingle();

    const row = pendingRow as {
      id: string; txn_kind: string; amount: number; title: string;
      category: string | null; status: string; expires_at: string;
    } | null;

    if (!row) {
      await ctx.reply("الطلب ده مش موجود أو مش بتاعك.");
      return;
    }
    if (row.status !== "pending") {
      await ctx.reply("الطلب ده اتعامل معاه قبل كده.");
      return;
    }
    if (new Date(row.expires_at) < new Date()) {
      await sb.from("telegram_pending_writes").update({ status: "cancelled" }).eq("id", row.id);
      await ctx.reply("الطلب ده انتهت صلاحيته — ابعت المصروف تاني.");
      return;
    }

    if (spend.action === "cancel") {
      await sb.from("telegram_pending_writes").update({ status: "cancelled" }).eq("id", row.id);
      await ctx.reply("تمام، ملغي ✖️");
      return;
    }

    // اتنقل لـ confirmed الأول: لو الإدخال فشل بعد كده مش هنكرر الكتابة، ولو ضغط
    // تأكيد مرتين بسرعة التانية هتلاقي status مش pending وتقف.
    const { error: claimError } = await sb.from("telegram_pending_writes")
      .update({ status: "confirmed" })
      .eq("id", row.id)
      .eq("status", "pending");
    if (claimError) {
      await ctx.reply("حصلت مشكلة، جرب تاني.");
      return;
    }

    const confirmed = await agentConfirm(userId, "log_transaction", {
      amount: row.amount,
      title: row.title,
      category: row.category ?? undefined,
      txn_kind: row.txn_kind,
      wallet: "card",
    });
    if (!confirmed.ok) {
      console.error("telegram expense confirmation via zad-brain failed");
      await sb.from("telegram_pending_writes").update({ status: "pending" }).eq("id", row.id);
      await ctx.reply("معلش، التسجيل فشل — جرب تاني.");
      return;
    }

    const { data: u } = await sb.from("zad_users").select("currency").eq("id", userId).maybeSingle();
    const cur = (u as { currency?: string } | null)?.currency ?? "غير معروف";
    await ctx.reply(`اتسجل ✅ ${isolate(sanitizeName(row.title))} — ${isolate(money(row.amount, cur))}`);
    return;
  }

  // تأكيد/إلغاء دواء جديد بجدول جرعات (Smart Medication Parsing). الكتابة الوحيدة في
  // zad_pharmacy_items من الشات بتحصل هنا بس، بعد ضغطة تأكيد صريحة — نفس مبدأ تسجيل
  // المصروف فوق بالظبط، لأن دواء جديد بيفتح تذكيرات متكررة لما التطبيق يعمل sync.
  const medication = parseMedicationCallback(data);
  if (medication) {
    const { data: pendingRow } = await sb.from("telegram_pending_pharmacy")
      .select("id,name,dosage,daily_dose_count,dose_times,unit,quantity,category,status,expires_at")
      .eq("id", medication.pendingId)
      .eq("user_id", userId)
      .maybeSingle();

    const row = pendingRow as {
      id: string; name: string; dosage: string | null; daily_dose_count: number;
      dose_times: string | null; unit: string; quantity: number; category: string;
      status: string; expires_at: string;
    } | null;

    if (!row) {
      await ctx.reply("الطلب ده مش موجود أو مش بتاعك.");
      return;
    }
    if (row.status !== "pending") {
      await ctx.reply("الطلب ده اتعامل معاه قبل كده.");
      return;
    }
    if (new Date(row.expires_at) < new Date()) {
      await sb.from("telegram_pending_pharmacy").update({ status: "cancelled" }).eq("id", row.id);
      await ctx.reply("الطلب ده انتهت صلاحيته — ابعت تفاصيل الدواء تاني.");
      return;
    }

    if (medication.action === "cancel") {
      await sb.from("telegram_pending_pharmacy").update({ status: "cancelled" }).eq("id", row.id);
      await ctx.reply("تمام، ملغي ✖️");
      return;
    }

    const { error: claimError } = await sb.from("telegram_pending_pharmacy")
      .update({ status: "confirmed" })
      .eq("id", row.id)
      .eq("status", "pending");
    if (claimError) {
      await ctx.reply("حصلت مشكلة، جرب تاني.");
      return;
    }

    const added = await agentExecute(userId, "add_pharmacy_item", {
      name: row.name,
      dosage: row.dosage,
      daily_dose_count: row.daily_dose_count,
      dose_times: row.dose_times,
      unit: row.unit,
      quantity: row.quantity,
      category: row.category,
    });
    if (!added.ok) {
      console.error("telegram add_pharmacy through zad-brain failed");
      await sb.from("telegram_pending_pharmacy").update({ status: "pending" }).eq("id", row.id);
      await ctx.reply("معلش، التسجيل فشل — جرب تاني.");
      return;
    }

    // مفيش AlarmManager على السيرفر — التذكيرات الفعلية بتتفعل لما تطبيق زاد يعمل sync
    // ويلاقي الدواء الجديد في zad_pharmacy_items (نفس آلية PharmacyReminderScheduler
    // اللي بتشتغل تلقائي عند أي تغيير في قائمة الأدوية).
    await ctx.reply(`اتسجل ✅ ${isolate(sanitizeName(row.name))} — المواعيد: ${isolate(row.dose_times ?? "")}\nهتلاقي التذكير شغال في التطبيق بعد أول فتح.`);
    return;
  }

  // Telegram Micro-Checkins — "لسه موجود ✅" / "خلص ❌" reply to a proactive daily prompt.
  const checkin = parseCheckInCallback(data);
  if (checkin) {
    const { data: promptRow } = await sb.from("telegram_checkin_prompts")
      .select("id,item_name,quantity_at_prompt,status,expires_at")
      .eq("id", checkin.promptId)
      .eq("user_id", userId)
      .maybeSingle();
    const prompt = promptRow as {
      id: string; item_name: string; quantity_at_prompt: number; status: string; expires_at: string;
    } | null;

    if (!prompt) {
      await ctx.reply("السؤال ده مش موجود أو مش بتاعك.");
      return;
    }
    if (prompt.status !== "pending") {
      await ctx.reply("رديت على السؤال ده قبل كده.");
      return;
    }
    if (new Date(prompt.expires_at) < new Date()) {
      await sb.from("telegram_checkin_prompts").update({ status: "expired" }).eq("id", prompt.id);
      await ctx.reply("السؤال ده قديم — هسأل تاني في المرة الجاية.");
      return;
    }

    // نفس نمط zad-brain's update_inventory_qty tool بالظبط: أي إجابة كمية لازم تتسجل
    // كـ observation وتعيد حساب معدل الاستهلاك، وإلا الصنف يفضل "غير معروف" للأبد
    // (Task 18 Fault B) والعقل يسأل عنه تاني وتاني من غير ما يتعلم حاجة.
    const newQty = checkin.stillInStock ? prompt.quantity_at_prompt : 0;
    const observed = await agentExecute(userId, "update_inventory_qty", {
      item_name: prompt.item_name,
      new_qty: newQty,
      reason: "إجابة العميل على سؤال متابعة المخزون",
    });
    if (!observed.ok) {
      await ctx.reply("معلش، مقدرتش أحفظ إجابتك — جرّب تاني.");
      return;
    }

    await sb.from("telegram_checkin_prompts")
      .update({ status: checkin.stillInStock ? "answered_yes" : "answered_no", answered_at: new Date().toISOString() })
      .eq("id", prompt.id);

    await ctx.reply(checkin.stillInStock ? "تمام ✅ هفتكر إني سألت عنه." : `سجلتها خلصت ✅ ${isolate(sanitizeName(prompt.item_name))}`);
    return;
  }

  const dismiss = parseDismissCallback(data);
  if (dismiss) {
    const reason = reasonForCode(dismiss.reasonCode);
    if (!reason) return;
    const { data: insight } = await sb.from("zad_insights")
      .select("title,about_item")
      .eq("id", dismiss.insightId)
      .eq("user_id", userId)
      .maybeSingle();
    await sb.from("zad_insights")
      .update({ status: "dismissed", dismiss_reason: reason, updated_at: new Date().toISOString() })
      .eq("id", dismiss.insightId)
      .eq("user_id", userId);
    const subject = (insight as any)?.about_item ?? (insight as any)?.title ?? "";
    const note = memoryNoteForDismissal(reason, subject);
    if (note) {
      await sb.rpc("zad_memory_upsert", { p_user: userId, p_scope: note.scope, p_note: note.note, p_conf: note.confidence });
    }
    await ctx.reply("تم ✅");
  }
});

// An explicitly-set project secret always wins; otherwise derive one (see context.ts for
// why). Either way WEBHOOK_SECRET is now always a real value, so grammY's secretToken
// check is genuinely enforced — previously it fell through to undefined and was skipped.
const WEBHOOK_SECRET = BOT_CONFIGURED
  ? (WEBHOOK_SECRET_ENV ?? await deriveWebhookSecret(BOT_TOKEN))
  : "";
const FUNCTION_URL = `${SUPABASE_URL}/functions/v1/zad-telegram-bot`;

/** Self-registration. setWebhook can't be run from the deploy path used here (it needs
 * the bot token, which is a write-only secret), so the function registers itself: it
 * already has the token at runtime. Idempotent — checks getWebhookInfo first and only
 * calls setWebhook when the registered URL differs from this deployment's. */
async function ensureWebhook(force = false): Promise<Record<string, unknown>> {
  if (!BOT_CONFIGURED) {
    return { ok: false, reason: "TELEGRAM_BOT_TOKEN is not set on this project" };
  }
  try {
    const infoRes = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/getWebhookInfo`);
    const info = await infoRes.json();
    const current = info?.result?.url ?? "";
    if (!force && current === FUNCTION_URL) {
      return { ok: true, changed: false, url: current, pending: info?.result?.pending_update_count ?? 0 };
    }
    const setRes = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/setWebhook`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        url: FUNCTION_URL,
        secret_token: WEBHOOK_SECRET,
        allowed_updates: ["message", "callback_query"],
        drop_pending_updates: false,
      }),
    });
    const set = await setRes.json();
    console.log("ensureWebhook: setWebhook ->", JSON.stringify(set));
    return { ok: set?.ok === true, changed: true, previous: current, url: FUNCTION_URL, telegram: set };
  } catch (e) {
    console.error("ensureWebhook failed:", e);
    return { ok: false, error: String(e) };
  }
}

// Register on cold start too, so a redeploy re-asserts the webhook without anyone
// having to poke it. Fire-and-forget: a Telegram outage must not stop the function
// from booting and serving updates it may already be receiving.
if (BOT_CONFIGURED) {
  ensureWebhook().catch((e) => console.error("boot ensureWebhook:", e));
} else {
  console.error("zad-telegram-bot: TELEGRAM_BOT_TOKEN is not set — bot is inert.");
}

const handleUpdate = webhookCallback(bot, "std/http", { secretToken: WEBHOOK_SECRET });

Deno.serve(async (req: Request) => {
  // GET is not a Telegram update — it's the health/registration probe. Reports whether
  // the webhook is wired up, without ever echoing the token or the secret itself.
  if (req.method === "GET") {
    const status = await ensureWebhook(new URL(req.url).searchParams.get("force") === "1");
    return new Response(
      JSON.stringify({
        function: "zad-telegram-bot",
        // Presence only — never the values themselves.
        config: {
          bot_token: BOT_CONFIGURED,
          webhook_secret: WEBHOOK_SECRET.length > 0,
          webhook_secret_source: WEBHOOK_SECRET_ENV ? "env" : (BOT_CONFIGURED ? "derived" : "none"),
          supabase_url: Boolean(SUPABASE_URL),
          service_role_key: Boolean(SERVICE_ROLE_KEY),
        },
        webhook: status,
      }, null, 2),
      { headers: { "Content-Type": "application/json" } },
    );
  }
  // Daily check-in cron trigger — see CHECKIN_CRON_SECRET's comment above for why this
  // needs its own auth instead of relying on verify_jwt. Checked before BOT_CONFIGURED so
  // a misconfigured bot token still reports a clear reason instead of falling through to
  // "bot not configured" below, which would otherwise read as this branch not existing.
  if (req.method === "POST" && new URL(req.url).searchParams.get("job") === "daily_checkins") {
    if (req.headers.get("X-Checkin-Cron-Secret") !== CHECKIN_CRON_SECRET) {
      return new Response("unauthorized", { status: 401 });
    }
    if (!BOT_CONFIGURED) {
      return new Response(JSON.stringify({ ok: false, reason: "bot not configured" }), { status: 503 });
    }
    try {
      const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
      const result = await runDailyCheckins(sb);
      return new Response(JSON.stringify({ ok: true, ...result }), { headers: { "Content-Type": "application/json" } });
    } catch (e) {
      console.error("runDailyCheckins failed:", e);
      return new Response(JSON.stringify({ ok: false, error: String(e) }), { status: 500 });
    }
  }
  // Daily subscription/bill renewal cron trigger — same shape as daily_checkins above,
  // own secret (SUBSCRIPTION_CRON_SECRET).
  if (req.method === "POST" && new URL(req.url).searchParams.get("job") === "subscription_alerts") {
    if (req.headers.get("X-Subscription-Cron-Secret") !== SUBSCRIPTION_CRON_SECRET) {
      return new Response("unauthorized", { status: 401 });
    }
    if (!BOT_CONFIGURED) {
      return new Response(JSON.stringify({ ok: false, reason: "bot not configured" }), { status: 503 });
    }
    try {
      const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
      const result = await runDailySubscriptionAlerts(sb);
      return new Response(JSON.stringify({ ok: true, ...result }), { headers: { "Content-Type": "application/json" } });
    } catch (e) {
      console.error("runDailySubscriptionAlerts failed:", e);
      return new Response(JSON.stringify({ ok: false, error: String(e) }), { status: 500 });
    }
  }

  // Real-time push — fired by a Postgres trigger (zad_transactions AFTER INSERT), not
  // cron. Payload is pre-formatted title/body text; this endpoint only resolves the
  // chat_id and delivers, no calculation happens here. Missing binding is a silent
  // no-op (200), not an error — most rows won't belong to a Telegram-linked user.
  if (req.method === "POST" && new URL(req.url).searchParams.get("job") === "realtime_push") {
    if (req.headers.get("X-Realtime-Push-Secret") !== REALTIME_PUSH_CRON_SECRET) {
      return new Response("unauthorized", { status: 401 });
    }
    if (!BOT_CONFIGURED) {
      return new Response(JSON.stringify({ ok: false, reason: "bot not configured" }), { status: 503 });
    }
    try {
      const { user_id, title, body } = await req.json() as { user_id?: string; title?: string; body?: string };
      if (!user_id || !title || !body) {
        return new Response(JSON.stringify({ ok: false, reason: "missing user_id/title/body" }), { status: 400 });
      }
      const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
      const chatId = await resolveChatId(sb, user_id);
      if (chatId === null) {
        return new Response(JSON.stringify({ ok: true, delivered: false, reason: "not linked" }), { headers: { "Content-Type": "application/json" } });
      }
      await sendTelegramMessage(chatId, `${title}\n\n${body}`);
      return new Response(JSON.stringify({ ok: true, delivered: true }), { headers: { "Content-Type": "application/json" } });
    } catch (e) {
      console.error("realtime_push failed:", e);
      return new Response(JSON.stringify({ ok: false, error: String(e) }), { status: 500 });
    }
  }

  // A bank notification the phone read and zad-brain accepted — delivered here as a
  // question, not as news. The customer presses تأكيد and the existing callback_query
  // branch does the write; they press إلغاء and nothing ever reaches zad_transactions.
  // This endpoint performs no money math and writes no transaction: it resolves the chat,
  // parks a telegram_pending_writes row, and sends it with the same confirm keyboard a
  // typed message gets. delivered:false (unlinked account) is a 200, and zad-brain falls
  // back to an in-app question — an unlinked customer must still be asked.
  if (req.method === "POST" && new URL(req.url).searchParams.get("job") === "confirm_transaction") {
    if (req.headers.get("X-Confirm-Transaction-Secret") !== CONFIRM_TRANSACTION_SECRET) {
      return new Response("unauthorized", { status: 401 });
    }
    if (!BOT_CONFIGURED) {
      return new Response(JSON.stringify({ ok: false, reason: "bot not configured" }), { status: 503 });
    }
    try {
      const { user_id, txn_kind, amount, title, category, confidence, source_label } = await req.json() as {
        user_id?: string; txn_kind?: string; amount?: number; title?: string;
        category?: string; confidence?: number; source_label?: string;
      };
      // txn_kind is checked against the same two values log_transaction accepts, here
      // rather than only at confirm time: a row parked with an unusable kind would sit in
      // the customer's chat as a button that can only ever fail.
      if (!user_id || !amount || !Number.isFinite(amount) || amount <= 0 || !title ||
          (txn_kind !== "expense" && txn_kind !== "income")) {
        return new Response(JSON.stringify({ ok: false, reason: "missing or invalid user_id/txn_kind/amount/title" }), { status: 400 });
      }
      const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
      const chatId = await resolveChatId(sb, user_id);
      if (chatId === null) {
        return new Response(JSON.stringify({ ok: true, delivered: false, reason: "not linked" }), {
          headers: { "Content-Type": "application/json" },
        });
      }

      const { data: pending, error } = await sb.from("telegram_pending_writes").insert({
        user_id,
        chat_id: chatId,
        txn_kind,
        amount,
        title,
        category: category ?? null,
        confidence: confidence ?? null,
      }).select("id").maybeSingle();
      if (error || !pending) {
        console.error("confirm_transaction pending write insert failed:", error);
        return new Response(JSON.stringify({ ok: false, reason: "insert failed" }), { status: 500 });
      }

      const { data: u } = await sb.from("zad_users").select("currency").eq("id", user_id).maybeSingle();
      const cur = (u as { currency?: string } | null)?.currency ?? "غير معروف";
      // "جالك" / "صرفت" — the customer's own framing of the question. The amount and the
      // direction are the whole message; the source is named so an unexpected charge can
      // be recognised without opening the app.
      const heard = txn_kind === "income"
        ? `جالك ${isolate(money(amount, cur))}`
        : `صرفت ${isolate(money(amount, cur))}`;
      const from = source_label ? ` من ${isolate(sanitizeName(source_label))}` : "";
      const text = [
        `${heard}${from}؟`,
        "",
        confirmSpendMessage({ is_spend: true, kind: txn_kind, amount, title, category: category ?? "أخرى", confidence: confidence ?? 0.9 }, cur),
      ].join("\n");

      await sendTelegramMessage(chatId, clampForTelegram(text), confirmSpendKeyboard((pending as { id: string }).id));
      return new Response(JSON.stringify({ ok: true, delivered: true }), {
        headers: { "Content-Type": "application/json" },
      });
    } catch (e) {
      console.error("confirm_transaction failed:", e);
      return new Response(JSON.stringify({ ok: false, error: String(e) }), { status: 500 });
    }
  }

  // Real-time check-in — fired by a Postgres trigger (zad_inventory AFTER UPDATE) the
  // instant an item crosses into low-stock/predicted-depletion territory, instead of
  // waiting for runDailyCheckins' once-a-day pass. Reuses the exact same prompt-sending
  // path and daily budget as the cron job — this is not a second, unlimited channel.
  if (req.method === "POST" && new URL(req.url).searchParams.get("job") === "live_checkin") {
    if (req.headers.get("X-Live-Checkin-Secret") !== LIVE_CHECKIN_CRON_SECRET) {
      return new Response("unauthorized", { status: 401 });
    }
    if (!BOT_CONFIGURED) {
      return new Response(JSON.stringify({ ok: false, reason: "bot not configured" }), { status: 503 });
    }
    try {
      const { user_id, item_name, quantity } = await req.json() as { user_id?: string; item_name?: string; quantity?: number };
      if (!user_id || !item_name || typeof quantity !== "number") {
        return new Response(JSON.stringify({ ok: false, reason: "missing user_id/item_name/quantity" }), { status: 400 });
      }
      const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
      const chatId = await resolveChatId(sb, user_id);
      if (chatId === null) {
        return new Response(JSON.stringify({ ok: true, delivered: false, reason: "not linked" }), { headers: { "Content-Type": "application/json" } });
      }
      const alreadySentToday = await checkinsSentToday(sb, user_id);
      if (alreadySentToday >= MAX_CHECKINS_PER_USER_PER_DAY) {
        return new Response(JSON.stringify({ ok: true, delivered: false, reason: "daily budget spent" }), { headers: { "Content-Type": "application/json" } });
      }
      const { data: existingPending } = await sb.from("telegram_checkin_prompts")
        .select("id").eq("user_id", user_id).eq("item_name", item_name).eq("status", "pending").maybeSingle();
      if (existingPending) {
        return new Response(JSON.stringify({ ok: true, delivered: false, reason: "already pending" }), { headers: { "Content-Type": "application/json" } });
      }
      const delivered = await sendCheckInPrompt(sb, user_id, chatId, item_name, quantity);
      return new Response(JSON.stringify({ ok: true, delivered }), { headers: { "Content-Type": "application/json" } });
    } catch (e) {
      console.error("live_checkin failed:", e);
      return new Response(JSON.stringify({ ok: false, error: String(e) }), { status: 500 });
    }
  }

  if (!BOT_CONFIGURED) {
    // Fail loudly rather than 500-ing opaquely: Telegram retries on 5xx, and a retry
    // loop against a misconfigured project helps nobody.
    return new Response("bot not configured", { status: 503 });
  }
  try {
    return await handleUpdate(req);
  } catch (e) {
    console.error("zad-telegram-bot error:", e);
    return new Response("error", { status: 500 });
  }
});
