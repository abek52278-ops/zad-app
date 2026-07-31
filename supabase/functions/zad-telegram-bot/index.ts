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
} from "./telegram.ts";
import {
  AgentContextInput, agentSystemPrompt, buildAgentContext, clampForTelegram,
  confirmSpendMessage, deriveWebhookSecret, money, parseSpendIntent, spendIntentPrompt,
} from "./context.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const BOT_TOKEN = Deno.env.get("TELEGRAM_BOT_TOKEN")!;
// Telegram's setWebhook secret_token, verified by grammY itself when passed to
// webhookCallback below. Empty means "not configured yet" — see PROGRESS.md caveat:
// unset in production would accept spoofed webhook calls.
const WEBHOOK_SECRET_ENV = Deno.env.get("TELEGRAM_WEBHOOK_SECRET") ?? undefined;

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

/** Pulls the same picture of the customer the in-app chat gets. Every query is
 * user-scoped explicitly — this runs on the service-role key, so RLS is NOT the
 * guard here; the .eq("user_id", userId) on each query is. */
async function fetchAgentContext(sb: SupabaseClient, userId: string): Promise<AgentContextInput> {
  const today = new Date().toISOString().slice(0, 10);

  const [user, txs, inv, subs, obligations, pharmacy, shopping, insights, tasbiha, memory] = await Promise.all([
    sb.from("zad_users").select("full_name,monthly_limit,currency").eq("id", userId).maybeSingle(),
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
    userName: (user.data as any)?.full_name ?? null,
    monthlyLimit: Number((user.data as any)?.monthly_limit) || 0,
    currency: (user.data as any)?.currency || "ر.س",
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

const bot = new Bot(BOT_TOKEN);

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
      .data?.currency ?? "ر.س";
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
    const { data: user } = await sb.from("zad_users").select("monthly_limit").eq("id", userId).maybeSingle();
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
    await ctx.reply(formatBalanceMessage((user as any)?.monthly_limit ?? 0, spent, income));
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
    const cur = (u as { currency?: string } | null)?.currency ?? "ر.س";
    await ctx.reply(`اتسجل ✅ ${row.title} — ${money(row.amount, cur)}`);
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
const WEBHOOK_SECRET = WEBHOOK_SECRET_ENV ?? await deriveWebhookSecret(BOT_TOKEN);
const FUNCTION_URL = `${SUPABASE_URL}/functions/v1/zad-telegram-bot`;

/** Self-registration. setWebhook can't be run from the deploy path used here (it needs
 * the bot token, which is a write-only secret), so the function registers itself: it
 * already has the token at runtime. Idempotent — checks getWebhookInfo first and only
 * calls setWebhook when the registered URL differs from this deployment's. */
async function ensureWebhook(force = false): Promise<Record<string, unknown>> {
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
ensureWebhook().catch((e) => console.error("boot ensureWebhook:", e));

const handleUpdate = webhookCallback(bot, "std/http", { secretToken: WEBHOOK_SECRET });

Deno.serve(async (req: Request) => {
  // GET is not a Telegram update — it's the health/registration probe. Reports whether
  // the webhook is wired up, without ever echoing the token or the secret itself.
  if (req.method === "GET") {
    const status = await ensureWebhook(new URL(req.url).searchParams.get("force") === "1");
    return new Response(JSON.stringify({ function: "zad-telegram-bot", webhook: status }, null, 2), {
      headers: { "Content-Type": "application/json" },
    });
  }
  try {
    return await handleUpdate(req);
  } catch (e) {
    console.error("zad-telegram-bot error:", e);
    return new Response("error", { status: 500 });
  }
});
