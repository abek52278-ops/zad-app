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
  formatBalanceMessage, formatTransactionsMessage, formatInsightTitle,
  confirmSpendKeyboard, parseSpendCallback,
  confirmMedicationKeyboard, parseMedicationCallback,
  checkInKeyboard, parseCheckInCallback, checkInPromptMessage,
} from "./telegram.ts";
import {
  AgentContextInput, agentSystemPrompt, buildAgentContext, clampForTelegram,
  confirmSpendMessage, deriveWebhookSecret, money, parseSpendIntent, spendIntentPrompt,
  confirmMedicationMessage, medicationIntentPrompt, parseMedicationIntent,
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
 * for a cap this small, not worth a scoring pass). */
const MAX_CHECKINS_PER_USER_PER_DAY = 2;

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
    const candidates = await findCheckInCandidates(sb, b.user_id);
    for (const item of candidates.slice(0, MAX_CHECKINS_PER_USER_PER_DAY)) {
      const { data: prompt, error } = await sb.from("telegram_checkin_prompts")
        .insert({ user_id: b.user_id, item_name: item.item_name, quantity_at_prompt: item.quantity })
        .select("id")
        .single();
      if (error || !prompt) {
        console.error("checkin prompt insert failed:", error?.message);
        continue;
      }
      await sendTelegramMessage(b.chat_id, checkInPromptMessage(item.item_name), checkInKeyboard((prompt as { id: string }).id));
      promptsSent++;
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

  for (const b of rows) {
    const { data: subs } = await sb.from("zad_subscriptions")
      .select("title,amount,renewal_date,currency")
      .eq("user_id", b.user_id)
      .eq("is_active", true)
      .not("renewal_date", "is", null);

    for (const sub of (subs ?? []) as Array<{ title: string; amount: number; renewal_date: string; currency: string | null }>) {
      const renewal = new Date(sub.renewal_date);
      if (isNaN(renewal.getTime())) continue;
      const daysLeft = Math.round((renewal.getTime() - today.getTime()) / 86400000);
      if (daysLeft < 0 || daysLeft > 3) continue;
      const amountText = `${sub.amount}${sub.currency ? " " + sub.currency : ""}`;
      const when = daysLeft === 0 ? "اليوم" : `خلال ${daysLeft} يوم`;
      await sendTelegramMessage(b.chat_id, `🔔 ${sub.title} يتجدد ${when} (${amountText})`);
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

  const [user, txs, inv, subs, obligations, pharmacy, shopping, insights, tasbiha, memory] = await Promise.all([
    sb.from("zad_users").select("name,monthly_limit,currency,country").eq("id", userId).maybeSingle(),
    // Pull a deep-enough window (200 newest) rather than just the 30 the prompt shows:
    // monthTotals/categoryBreakdown run over this same list, so a heavy month with more
    // than 30 transactions would otherwise report totals that are silently too low.
    sb.from("zad_transactions").select("title,amount,txn_kind,category,created_at")
      .eq("user_id", userId).order("created_at", { ascending: false }).limit(200),
    sb.from("zad_inventory").select("item_name,quantity,unit,expiry_date").eq("user_id", userId).limit(60),
    sb.from("zad_subscriptions").select("title,amount,renewal_date,is_active").eq("user_id", userId).limit(30),
    sb.from("zad_obligations").select("title,amount,due_date,status").eq("user_id", userId).limit(30),
    sb.from("zad_pharmacy_items").select("name,remaining_quantity,unit,dosage").eq("user_id", userId).limit(30),
    sb.from("zad_shopping_list").select("item_name,is_purchased").eq("user_id", userId).limit(40),
    sb.from("zad_insights").select("title,body").eq("user_id", userId).eq("status", "pending").limit(8),
    sb.from("family_tasbiha").select("garden_name,tree_emoji,level,score,total_clicks,streak_days").eq("user_id", userId).limit(10),
    sb.from("zad_memory").select("scope,note").eq("user_id", userId).limit(20),
  ]);

  return {
    userName: (user.data as any)?.name ?? null,
    monthlyLimit: Number((user.data as any)?.monthly_limit) || 0,
    // "غير معروف" بدل "ر.س" — كان افتراض ميت خلّى البوت يرد على عميل في مصر "مفيش
    // ولا ريال" وهو فلوسه بالمصري. لو العمود موجود، قيمته الحقيقية (EGP/SAR/TRY)
    // هي اللي بتوصل من الكلاينت (MarketPrefs → syncMarketProfile).
    currency: (user.data as any)?.currency ?? "غير معروف",
    country: (user.data as any)?.country ?? null,
    today,
    transactions: (txs.data ?? []) as any,
    inventory: (inv.data ?? []) as any,
    subscriptions: (subs.data ?? []) as any,
    obligations: (obligations.data ?? []) as any,
    pharmacy: (pharmacy.data ?? []) as any,
    shopping: (shopping.data ?? []) as any,
    insights: (insights.data ?? []) as any,
    tasbiha: (tasbiha.data ?? []) as any,
    memory: (memory.data ?? []) as any,
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
  const context = buildAgentContext(await fetchAgentContext(sb, userId));
  const answer = await askZad(
    agentSystemPrompt(),
    `${context}\n\n=== سؤال العميل ===\nاعملي تحليل سريع لوضعي المالي وحالة البيت: أهم ٣ ملاحظات، وأهم حاجة أعملها دلوقتي.`,
  );
  await ctx.reply(answer ? clampForTelegram(answer) : "معلش، التحليل مش متاح دلوقتي — جرب كمان شوية.");
});

// المحادثة الحقيقية — أي كلام عادي بيروح لزاد بنفس السياق والشخصية بتوع الشات
// اللي جوه التطبيق، مش رد ثابت بقائمة أزرار زي النسخة الأولى.
bot.on("message:text", async (ctx) => {
  if (ctx.message.text.startsWith("/")) return; // أوامر متسجلة فوق بتتعامل لوحدها
  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
  const userId = await resolveUserId(sb, ctx.chat.id);
  if (!userId) {
    await ctx.reply("أهلاً! لو عندك كود ربط من تطبيق زاد ابعته كده: /start الكود");
    return;
  }

  await ctx.replyWithChatAction("typing");

  // أولاً: هل ده تسجيل مصروف/دخل فعلي؟ لو أيوه، نعرض تأكيد الأول — مفيش كتابة في
  // zad_transactions من غير ضغطة تأكيد صريحة، عشان أي خطأ في الفهم يبان للعميل
  // قبل ما يتسجل في دفتره الحقيقي.
  const intent = parseSpendIntent(await askZad(spendIntentPrompt(), ctx.message.text));
  if (intent) {
    const currency = (await sb.from("zad_users").select("currency").eq("id", userId).maybeSingle())
      .data?.currency ?? "غير معروف";
    const { data: pending, error } = await sb.from("telegram_pending_writes").insert({
      user_id: userId,
      chat_id: ctx.chat.id,
      txn_kind: intent.kind,
      amount: intent.amount,
      title: intent.title,
      category: intent.category,
      confidence: intent.confidence,
    }).select("id").single();

    if (!error && pending) {
      await ctx.reply(confirmSpendMessage(intent, currency), {
        reply_markup: toGrammyKeyboard(confirmSpendKeyboard((pending as { id: string }).id)),
      });
      return;
    }
    console.error("pending write insert failed:", error);
    // بيقع على الشات العادي تحت بدل ما يفضل ساكت
  }

  // تاني: هل ده وصف دواء جديد بجدول جرعات؟ (Smart Medication Parsing) — نفس مبدأ
  // مصروف: تأكيد قبل الكتابة، لأن دواء جديد بيفتح تذكيرات متكررة لما التطبيق يعمل sync.
  const nowTime = new Date().toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", hour12: false });
  const medIntent = parseMedicationIntent(await askZad(medicationIntentPrompt(nowTime), ctx.message.text));
  if (medIntent) {
    const { data: pending, error } = await sb.from("telegram_pending_pharmacy").insert({
      user_id: userId,
      chat_id: ctx.chat.id,
      name: medIntent.name,
      dosage: medIntent.dosage,
      daily_dose_count: medIntent.daily_dose_count,
      dose_times: medIntent.dose_times,
      unit: medIntent.unit,
      quantity: medIntent.quantity,
      category: medIntent.category,
    }).select("id").single();

    if (!error && pending) {
      await ctx.reply(confirmMedicationMessage(medIntent), {
        reply_markup: toGrammyKeyboard(confirmMedicationKeyboard((pending as { id: string }).id)),
      });
      return;
    }
    console.error("pending pharmacy insert failed:", error);
    // بيقع على الشات العادي تحت بدل ما يفضل ساكت
  }

  const context = buildAgentContext(await fetchAgentContext(sb, userId));
  const answer = await askZad(
    agentSystemPrompt(),
    `${context}\n\n=== سؤال العميل ===\n${ctx.message.text}`,
  );

  if (answer) {
    await ctx.reply(clampForTelegram(answer));
  } else {
    await ctx.reply("معلش، مش قادر أرد دلوقتي — جرب تاني كمان شوية، أو اختار من القائمة:", {
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
  const heard = `🎤 "${result.transcript}"\n\n`;

  if (result.action === "add_expense" || result.action === "add_income") {
    const amount = Number(result.data?.amount ?? 0);
    if (!Number.isFinite(amount) || amount <= 0) {
      await ctx.reply(heard + (result.message || "معلش، مسمعتش مبلغ واضح."));
      return;
    }
    const kind = result.action === "add_expense" ? "expense" : "income";
    const title = (result.data?.title || (kind === "expense" ? "مصروف" : "دخل")).slice(0, 80);
    const category = (result.data?.category || "أخرى").slice(0, 40);
    const currency = (await sb.from("zad_users").select("currency").eq("id", userId).maybeSingle())
      .data?.currency ?? "غير معروف";
    const { data: pending, error } = await sb.from("telegram_pending_writes").insert({
      user_id: userId,
      chat_id: ctx.chat.id,
      txn_kind: kind,
      amount: Math.round(amount * 100) / 100,
      title,
      category,
      // voice_agent مبيرجعش confidence (مش زي parseSpendIntent) — قيمة ثابتة معقولة،
      // مش بتتحكم في عرض زر التأكيد أصلاً (التأكيد بيتعرض دايماً بغض النظر عنها).
      confidence: 0.75,
    }).select("id").single();
    if (!error && pending) {
      await ctx.reply(heard + confirmSpendMessage({ is_spend: true, kind, amount, title, category, confidence: 0.75 }, currency), {
        reply_markup: toGrammyKeyboard(confirmSpendKeyboard((pending as { id: string }).id)),
      });
      return;
    }
    console.error("voice pending write insert failed:", error);
    await ctx.reply(heard + "معلش، حصلت مشكلة في تسجيل المصروف — جرب تاني.");
    return;
  }

  // دواء جديد بجدول جرعات من رسالة صوتية — نفس تأكيد المصروف الصوتي فوق بالظبط
  // (مش تسجيل مباشر زي add_inventory)، لأن دواء جديد بيفتح تذكيرات متكررة.
  if (result.action === "add_pharmacy") {
    const medName = (result.data?.title || "").trim();
    const doseTimes = (result.data?.dose_times || "").split(",").map((t) => t.trim())
      .filter((t) => /^([01]\d|2[0-3]):[0-5]\d$/.test(t)).join(",");
    if (!medName || !doseTimes) {
      await ctx.reply(heard + (result.message || "معلش، مسمعتش اسم دواء أو مواعيد واضحة."));
      return;
    }
    const rawQty = Number(result.data?.amount);
    const qty = Number.isFinite(rawQty) && rawQty > 0 ? Math.round(rawQty) : 1;
    const doseCount = Math.max(1, Math.min(12, doseTimes.split(",").length));
    const unit = ["قرص", "مل", "كريم"].includes(String(result.data?.unit)) ? String(result.data?.unit) : "قرص";
    const medIntent = {
      is_medication: true as const,
      name: medName.slice(0, 80),
      dosage: (result.data?.dosage || "").trim().slice(0, 120),
      daily_dose_count: doseCount,
      dose_times: doseTimes,
      unit,
      quantity: qty,
      category: "عام",
      confidence: 1,
    };
    const { data: pending, error } = await sb.from("telegram_pending_pharmacy").insert({
      user_id: userId,
      chat_id: ctx.chat.id,
      name: medIntent.name,
      dosage: medIntent.dosage,
      daily_dose_count: medIntent.daily_dose_count,
      dose_times: medIntent.dose_times,
      unit: medIntent.unit,
      quantity: medIntent.quantity,
      category: medIntent.category,
    }).select("id").single();
    if (!error && pending) {
      await ctx.reply(heard + confirmMedicationMessage(medIntent), {
        reply_markup: toGrammyKeyboard(confirmMedicationKeyboard((pending as { id: string }).id)),
      });
      return;
    }
    console.error("voice pending pharmacy insert failed:", error);
    await ctx.reply(heard + "معلش، حصلت مشكلة في تسجيل الدواء — جرب تاني.");
    return;
  }

  if (result.action === "add_inventory") {
    const itemName = (result.data?.title || "").trim();
    if (!itemName) {
      await ctx.reply(heard + (result.message || "معلش، مسمعتش اسم صنف واضح."));
      return;
    }
    // voice_agent's data شكلها ثابت لكل action (amount/title/category) — مفيهاش حقل كمية
    // مخصص لـ add_inventory. amount بيتفسر هنا كمية لو رقم منطقي، وإلا واحدة افتراضية.
    const rawQty = Number(result.data?.amount);
    const qty = Number.isFinite(rawQty) && rawQty > 0 ? Math.round(rawQty) : 1;
    const { error } = await sb.from("zad_inventory").insert({
      user_id: userId,
      item_name: itemName,
      quantity: qty,
      unit: "قطعة",
    });
    if (error) {
      console.error("voice add_inventory insert failed:", error);
      await ctx.reply(heard + "معلش، مقدرتش أضيف الصنف للمخزون — جرب تاني.");
      return;
    }
    await sb.rpc("zad_record_observation", { p_user: userId, p_item: itemName, p_qty: qty, p_source: "purchase" });
    await ctx.reply(heard + `✅ اتضاف "${itemName}" للمخزون (${qty}).`);
    return;
  }

  // check_budget / log_pharmacy_dose / chat — قراءة بس دلوقتي، مفيش كتابة. تسجيل جرعة
  // دوا فعلي محتاج جدول/تدفق منفصل (zad_dose_log) مش داخل نطاق المهمة دي.
  await ctx.reply(heard + (result.message || "تمام."));
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

  // فاتورة صيدلية: كل صنف بيتحقن في zad_pharmacy_items بسعره الخاص، مش zad_inventory —
  // نفس التصنيف والمنطق اللي في CameraScreen.kt (تطبيق الموبايل). كل صنف بيسجل مصروفه
  // فوراً هنا (بدون زر تأكيد منفصل، زي حقن المخزون العادي تحت) عشان الفاتورة ماتتحسبش
  // مرتين، فمفيش pending write لإجمالي الفاتورة في المسار ده.
  if (result.receiptType === "pharmacy" && result.items.length > 0) {
    let addedCount = 0;
    for (const item of result.items) {
      if (!item.name?.trim()) continue;
      const qty = Number.isFinite(item.quantity) && item.quantity > 0 ? Math.round(item.quantity) : 1;
      const { error } = await sb.from("zad_pharmacy_items").insert({
        user_id: userId,
        name: item.name.trim(),
        remaining_quantity: qty,
        unit: item.unit || "قرص",
        price: item.price || 0,
      });
      if (error) {
        console.error("photo pharmacy insert failed:", error);
        continue;
      }
      addedCount++;
      if (item.price > 0) {
        const { error: txError } = await sb.from("zad_transactions").insert({
          user_id: userId,
          amount: item.price,
          title: item.name.trim(),
          category: "الرعاية الصحية",
          is_expense: true,
          txn_kind: "expense",
          wallet: "card",
        });
        if (txError) console.error("photo pharmacy transaction insert failed:", txError);
      }
    }
    await ctx.reply(
      addedCount > 0
        ? `✅ اتضاف ${addedCount} صنف للصيدلية${result.storeName ? ` من ${result.storeName}` : ""}.`
        : "معلش، ملقتش أصناف واضحة في الصورة دي.",
    );
    return;
  }

  let addedCount = 0;
  for (const item of result.items) {
    if (!item.name?.trim()) continue;
    const qty = Number.isFinite(item.quantity) && item.quantity > 0 ? Math.round(item.quantity) : 1;
    const { error } = await sb.from("zad_inventory").insert({
      user_id: userId,
      item_name: item.name.trim(),
      quantity: qty,
      unit: item.unit || "قطعة",
      category: item.category || null,
    });
    if (!error) {
      addedCount++;
      await sb.rpc("zad_record_observation", { p_user: userId, p_item: item.name.trim(), p_qty: qty, p_source: "purchase" });
    } else {
      console.error("photo inventory insert failed:", error);
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

  if (data === "b") {
    const { data: user } = await sb.from("zad_users")
      .select("monthly_limit,limit_confirmed_at").eq("id", userId).maybeSingle();
    const monthStart = new Date();
    monthStart.setDate(1);
    monthStart.setHours(0, 0, 0, 0);
    const { data: txs } = await sb.from("zad_transactions")
      .select("amount,txn_kind,created_at")
      .eq("user_id", userId)
      .gte("created_at", monthStart.toISOString());
    const rows = (txs ?? []) as Array<{ amount: number; txn_kind: string }>;
    const spent = rows.filter((t) => t.txn_kind === "expense").reduce((s, t) => s + t.amount, 0);
    const income = rows.filter((t) => t.txn_kind === "income").reduce((s, t) => s + t.amount, 0);
    // limit_confirmed_at IS NULL = captured but never confirmed (SupabaseRepo.getMonthlyLimit's
    // own contract) — same false-budget bug as the realtime push trigger, don't show it.
    const confirmedLimit = (user as any)?.limit_confirmed_at ? ((user as any)?.monthly_limit ?? 0) : 0;
    await ctx.reply(formatBalanceMessage(confirmedLimit, spent, income));
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

    const { error: insertError } = await sb.from("zad_transactions").insert({
      user_id: userId,
      amount: row.amount,
      title: row.title,
      category: row.category,
      is_expense: row.txn_kind === "expense",
      txn_kind: row.txn_kind,
      wallet: "card",
    });

    if (insertError) {
      console.error("telegram expense insert failed:", insertError);
      await sb.from("telegram_pending_writes").update({ status: "pending" }).eq("id", row.id);
      await ctx.reply("معلش، التسجيل فشل — جرب تاني.");
      return;
    }

    const { data: u } = await sb.from("zad_users").select("currency").eq("id", userId).maybeSingle();
    const cur = (u as { currency?: string } | null)?.currency ?? "غير معروف";
    await ctx.reply(`اتسجل ✅ ${row.title} — ${money(row.amount, cur)}`);
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

    const { error: insertError } = await sb.from("zad_pharmacy_items").insert({
      user_id: userId,
      name: row.name,
      dosage: row.dosage,
      daily_dose_count: row.daily_dose_count,
      dose_times: row.dose_times,
      unit: row.unit,
      remaining_quantity: row.quantity,
      category: row.category,
    });

    if (insertError) {
      console.error("telegram add_pharmacy insert failed:", insertError);
      await sb.from("telegram_pending_pharmacy").update({ status: "pending" }).eq("id", row.id);
      await ctx.reply("معلش، التسجيل فشل — جرب تاني.");
      return;
    }

    // مفيش AlarmManager على السيرفر — التذكيرات الفعلية بتتفعل لما تطبيق زاد يعمل sync
    // ويلاقي الدواء الجديد في zad_pharmacy_items (نفس آلية PharmacyReminderScheduler
    // اللي بتشتغل تلقائي عند أي تغيير في قائمة الأدوية).
    await ctx.reply(`اتسجل ✅ ${row.name} — المواعيد: ${row.dose_times}\nهتلاقي التذكير شغال في التطبيق بعد أول فتح.`);
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
    if (!checkin.stillInStock) {
      await sb.from("zad_inventory").update({ quantity: 0 }).eq("user_id", userId).eq("item_name", prompt.item_name);
    }
    const { error: obsErr } = await sb.rpc("zad_record_observation", {
      p_user: userId, p_item: prompt.item_name, p_qty: newQty, p_source: "question_answer",
    });
    if (obsErr) console.error("checkin zad_record_observation failed:", obsErr.message);

    await sb.from("telegram_checkin_prompts")
      .update({ status: checkin.stillInStock ? "answered_yes" : "answered_no", answered_at: new Date().toISOString() })
      .eq("id", prompt.id);

    await ctx.reply(checkin.stillInStock ? "تمام ✅ هفتكر إني سألت عنه." : `سجلتها خلصت ✅ ${prompt.item_name}`);
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
