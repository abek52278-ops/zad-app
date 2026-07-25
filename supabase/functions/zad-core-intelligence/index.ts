// deno-lint-ignore-file
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.6";

// ── Provider chain (rewritten): Groq (multi-key pool) primary, Gemini secondary ──────────
//
// GROQ_API_KEY (singular, no suffix) stays reserved for transcribeAudio() and
// callCompoundSearch() further down — untouched by this refactor. Their retry/TPM-budget
// behavior was live-diagnosed and is documented in detail at each call site; rotating keys
// under them wasn't asked for here and risks regressing tuning that took real production
// incidents to get right. If 429s show up there too, that's a follow-up, not this change.
const GROQ_API_KEY = Deno.env.get("GROQ_API_KEY");

// GROQ_KEYS is the pool for callTextModel/callJsonModel/callVisionModel: GROQ_API_KEY_1
// (falls back to the original singular GROQ_API_KEY so existing deployments with only one
// key keep working unmodified) plus GROQ_API_KEY_2. Add GROQ_API_KEY_3 etc. below if the
// pool ever needs to grow — nextGroqKey() already loops over whatever length GROQ_KEYS is.
const GROQ_KEYS: string[] = [
  Deno.env.get("GROQ_API_KEY_1") || Deno.env.get("GROQ_API_KEY"),
  Deno.env.get("GROQ_API_KEY_2"),
].filter((k): k is string => !!k);
const GROQ_CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";
// Model IDs are env-configurable, not hardcoded — Groq's model catalog (especially vision)
// has churned before (Llama vision models were pulled from Groq's catalog previously over
// licensing). A wrong/deprecated slug becomes a secret update, not a redeploy.
const GROQ_TEXT_MODEL = Deno.env.get("ZAD_GROQ_TEXT_MODEL") || "llama-3.3-70b-versatile";
const GROQ_VISION_MODEL = Deno.env.get("ZAD_GROQ_VISION_MODEL") || "llama-3.2-11b-vision-instruct";

// Round-robin pointer across warm invocations of this isolate — "alternate" per the pool,
// not a fresh random pick every call (steadier load distribution across N keys than pure
// random, and still spreads load the same way pure alternation would).
let groqKeyCursor = 0;
function nextGroqKeyIndex(): number {
  const i = groqKeyCursor % Math.max(GROQ_KEYS.length, 1);
  groqKeyCursor = (groqKeyCursor + 1) % Math.max(GROQ_KEYS.length, 1);
  return i;
}

// GEMINI_API_KEY is the SAME project secret CLAUDE.md documents — shared across every edge
// function in this project, not a new/separate key. Now called via Gemini's OpenAI-
// compatible endpoint (not the native generateContent REST shape used elsewhere in this
// file previously) specifically so it can share callOpenAICompatibleChat() with the Groq
// pool instead of a third near-duplicate fetch/parse implementation.
const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const GEMINI_OPENAI_URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
const GEMINI_FALLBACK_MODEL = Deno.env.get("ZAD_GEMINI_FALLBACK_MODEL") || "gemini-2.0-flash";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const supabase = createClient(supabaseUrl, supabaseKey);

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "apikey, x-client-info, Content-Type, Authorization",
    "Content-Type": "application/json",
  };
}

function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders() });
}

// openai/gpt-oss-20b:free is a reasoning model — without reasoning:{effort:"low"}
// and a token budget that covers both the hidden reasoning trace and the
// final answer, it burns the whole max_tokens budget "thinking" and returns
// content:null (finish_reason:"length"). Verified directly: 50 tokens ->
// null content; 500 tokens + effort:"low" -> correct content every time.
// 25s upstream timeout, kept under the Android client's 30s HttpURLConnection
// timeout (SupabaseRepo.kt callEdgeFunction) — without this, a stalled
// OpenRouter free-tier response left the request hanging up to the Deno
// platform's own execution limit, which read to users as "stuck forever"
// (e.g. the Chef Zad recipe screen).
const UPSTREAM_TIMEOUT_MS = 25000;

// Generic OpenAI-compatible chat-completions caller, shared by the Groq pool and the Gemini
// fallback (Gemini's OpenAI-compat endpoint speaks the same shape) — one fetch/parse
// implementation instead of three near-duplicates. `content` accepts either a plain string
// (text/JSON actions) or OpenAI's multimodal content-block array (vision).
async function callOpenAICompatibleChat(opts: {
  baseUrl: string;
  apiKey: string;
  model: string;
  systemPrompt: string;
  content: string | Array<Record<string, unknown>>;
  temperature?: number;
  maxTokens?: number;
  jsonMode?: boolean;
}): Promise<{ content: string | null; status: number; ok: boolean; raw: unknown }> {
  try {
    const body: Record<string, unknown> = {
      model: opts.model,
      messages: [
        { role: "system", content: opts.systemPrompt },
        { role: "user", content: opts.content },
      ],
      temperature: opts.temperature ?? 0.3,
      max_tokens: Math.max(opts.maxTokens ?? 1000, 300),
    };
    if (opts.jsonMode) body.response_format = { type: "json_object" };
    const resp = await fetch(opts.baseUrl, {
      method: "POST",
      headers: { "Authorization": "Bearer " + opts.apiKey, "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
    });
    const data = await resp.json();
    return { content: data.choices?.[0]?.message?.content || null, status: resp.status, ok: resp.ok, raw: data };
  } catch (e) {
    return { content: null, status: 0, ok: false, raw: { error: e.message } };
  }
}

// Tries every key in GROQ_KEYS, starting from the round-robin pointer, before giving up.
// A 429 on one key immediately retries the SAME request on the next key (per spec 2a) — this
// is not a per-incoming-request rotation, it's exhausting the whole pool within one call
// before ever falling through to Gemini. Any other failure (HTTP error, timeout, empty
// content) also advances to the next key rather than failing fast, since a bad key or a
// transient upstream blip shouldn't cost the whole pool.
async function callGroqPool(opts: {
  model: string;
  systemPrompt: string;
  content: string | Array<Record<string, unknown>>;
  temperature?: number;
  maxTokens?: number;
  jsonMode?: boolean;
}): Promise<{ content: string | null; ok: boolean }> {
  if (GROQ_KEYS.length === 0) return { content: null, ok: false };
  const start = nextGroqKeyIndex();
  for (let i = 0; i < GROQ_KEYS.length; i++) {
    const keyIndex = (start + i) % GROQ_KEYS.length;
    const result = await callOpenAICompatibleChat({ baseUrl: GROQ_CHAT_URL, apiKey: GROQ_KEYS[keyIndex], ...opts });
    if (result.ok && result.content) return { content: result.content, ok: true };
    if (result.status === 429) {
      console.warn(`[CoreIntel] Groq key ${keyIndex + 1} hit 429, switching to next Groq key...`);
    } else {
      console.error(`[CoreIntel] Groq key ${keyIndex + 1} failed (status ${result.status}):`, JSON.stringify(result.raw));
    }
  }
  console.warn("[CoreIntel] All Groq keys exhausted, falling back to Gemini Direct");
  return { content: null, ok: false };
}

async function callGeminiFallback(opts: {
  systemPrompt: string;
  content: string | Array<Record<string, unknown>>;
  temperature?: number;
  maxTokens?: number;
  jsonMode?: boolean;
}): Promise<string | null> {
  if (!GEMINI_API_KEY) {
    console.error("[CoreIntel] Gemini fallback unavailable: GEMINI_API_KEY not set");
    return null;
  }
  const result = await callOpenAICompatibleChat({ baseUrl: GEMINI_OPENAI_URL, apiKey: GEMINI_API_KEY, model: GEMINI_FALLBACK_MODEL, ...opts });
  if (!result.ok || !result.content) {
    console.error("[CoreIntel] Gemini fallback failed:", result.status, JSON.stringify(result.raw));
    return null;
  }
  return result.content;
}

async function callTextModel(systemPrompt: string, userPrompt: string, maxTokens = 1000, temperature = 0.7) {
  const groq = await callGroqPool({ model: GROQ_TEXT_MODEL, systemPrompt, content: userPrompt, temperature, maxTokens });
  if (groq.ok) return groq.content;
  return await callGeminiFallback({ systemPrompt, content: userPrompt, temperature, maxTokens });
}

async function callJsonModel(systemPrompt: string, userPrompt: string, maxTokens = 1500) {
  const groq = await callGroqPool({ model: GROQ_TEXT_MODEL, systemPrompt, content: userPrompt, temperature: 0.2, maxTokens, jsonMode: true });
  const raw = groq.ok ? groq.content : await callGeminiFallback({ systemPrompt, content: userPrompt, temperature: 0.2, maxTokens, jsonMode: true });
  if (!raw) return null;
  try { return JSON.parse(raw); } catch (e) {
    console.error("[CoreIntel] callJsonModel: JSON.parse failed:", e.message, "raw:", raw);
    return null;
  }
}

// Vision: Groq primary (GROQ_VISION_MODEL, whichever key answers first), Gemini fallback —
// same pool/fallback order as text, per spec section 3 ("If Groq Vision fails, route
// directly to Gemini"). image_url + base64 data URI is the OpenAI-compatible multimodal
// shape both Groq and Gemini's compat endpoint accept, so the same content-block array
// works unchanged across both.
async function callVisionModel(systemPrompt: string, userPrompt: string, imageBase64: string, mimeType: string) {
  const content = [
    { type: "text", text: userPrompt },
    { type: "image_url", image_url: { url: "data:" + mimeType + ";base64," + imageBase64 } },
  ];
  const groq = await callGroqPool({ model: GROQ_VISION_MODEL, systemPrompt, content, temperature: 0.2, maxTokens: 2000 });
  if (groq.ok) return groq.content;
  return await callGeminiFallback({ systemPrompt, content, temperature: 0.2, maxTokens: 2000 });
}

async function transcribeAudio(audioBase64: string, mimeType: string) {
  if (!GROQ_API_KEY) return { text: null, raw: { error: "GROQ_API_KEY not set" }, ok: false, status: 0 };
  try {
    const binary = atob(audioBase64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const ext = mimeType.includes("mp3") ? "mp3" : mimeType.includes("wav") ? "wav" : mimeType.includes("ogg") ? "ogg" : "m4a";
    const form = new FormData();
    form.append("file", new Blob([bytes], { type: mimeType }), `audio.${ext}`);
    form.append("model", "whisper-large-v3-turbo");
    form.append("language", "ar");
    form.append("response_format", "json");
    const resp = await fetch("https://api.groq.com/openai/v1/audio/transcriptions", {
      method: "POST",
      headers: { "Authorization": "Bearer " + GROQ_API_KEY },
      body: form,
    });
    const data = await resp.json();
    console.log("[CoreIntel] Whisper transcription raw response:", JSON.stringify(data));
    if (!resp.ok) {
      console.error("[CoreIntel] Whisper HTTP error:", resp.status, JSON.stringify(data));
    }
    return { text: data.text || null, raw: data, ok: resp.ok, status: resp.status };
  } catch (e) {
    console.error("[CoreIntel] transcribeAudio failed:", e.message);
    return { text: null, raw: { error: e.message }, ok: false, status: 0 };
  }
}

// groq/compound-mini is Groq's lighter agentic system with a built-in,
// Tavily-backed web_search tool — used ONLY for the two live-search actions
// below so results are grounded in real pages, never invented. Reuses
// GROQ_API_KEY (already provisioned for Whisper), no new secret needed.
// The full groq/compound model (not -mini) consistently hit Groq's free-tier
// 6000 TPM cap in a single call — verified live: 413 request_too_large on
// every call regardless of our small max_tokens, because compound's internal
// multi-hop tool orchestration burns tokens we don't control. compound-mini's
// lighter footprint fits under that cap; verified live with real store/price
// results. Compound sometimes wraps its JSON answer in prose/markdown fences
// despite instructions, so we extract leniently like callJsonModel() does.
// `ok` distinguishes a hard failure (missing key, HTTP error, timeout, unparsable
// response) from a genuinely successful search that just found nothing — callers
// used to collapse both into the same empty array, so real outages looked
// identical to "no deals right now" in the UI.
//
// Diagnosed live (2026-07-24) via a temporary debug build: compound-mini's
// web_search tool DOES get invoked and DOES find real pages with real prices
// (confirmed live: a fetch_live_deals call returned real Saudi supermarket
// rice prices in executed_tools output) — but the model sometimes drops that
// found data when formatting its final JSON answer, returning `[]` despite
// having real numbers in front of it. Non-deterministic per call, not a
// missing-key/HTTP/parsing bug. fetch_live_market_prices's one internal
// retry-on-empty (see that case below) is the mitigation for this specific
// action — a second attempt has real odds of succeeding where the first one
// found data but failed to extract it.
async function callCompoundSearch(systemPrompt: string, userPrompt: string, maxTokens = 1500) {
  if (!GROQ_API_KEY) return { parsed: null, executedTools: [], ok: false };
  // groq/compound-mini runs on a shared org-level TPM budget (8000/min on this
  // account's tier) that a single agentic call can consume most of — a second
  // call landing in the same window gets a 429 with a sub-second suggested
  // retry ("Please try again in 37.5ms"), verified live. One short-delay retry
  // absorbs that without surfacing a false failure to the user.
  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const resp = await fetch("https://api.groq.com/openai/v1/chat/completions", {
        method: "POST",
        headers: {
          "Authorization": "Bearer " + GROQ_API_KEY,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: "groq/compound-mini",
          messages: [
            { role: "system", content: systemPrompt },
            { role: "user", content: userPrompt },
          ],
          temperature: 0.2,
          max_tokens: Math.max(maxTokens, 300),
        }),
        signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
      });
      const data = await resp.json();
      if (!resp.ok) {
        console.error("[CoreIntel] Groq compound HTTP error:", resp.status, JSON.stringify(data));
        if (resp.status === 429 && attempt === 0) {
          await new Promise((r) => setTimeout(r, 800));
          continue;
        }
        return { parsed: null, executedTools: [], ok: false };
      }
      const text = data.choices?.[0]?.message?.content || "";
      const executedTools = data.choices?.[0]?.message?.executed_tools || [];
      console.log("[CoreIntel] Groq compound executed_tools:", JSON.stringify(executedTools));
      const arrayMatch = text.match(/\[[\s\S]*\]/);
      const objectMatch = text.match(/\{[\s\S]*\}/);
      let parsed: unknown = null;
      let parseFailed = false;
      try {
        if (arrayMatch) parsed = JSON.parse(arrayMatch[0]);
        else if (objectMatch) parsed = JSON.parse(objectMatch[0]);
      } catch (e) {
        parseFailed = true;
        console.error("[CoreIntel] callCompoundSearch: JSON.parse failed:", e.message, "raw:", text);
      }
      // no JSON found/parseable in the model's reply is a real failure, not "no results"
      return { parsed, executedTools, ok: !parseFailed };
    } catch (e) {
      console.error("[CoreIntel] callCompoundSearch failed/timed out:", e.message);
      return { parsed: null, executedTools: [], ok: false };
    }
  }
  return { parsed: null, executedTools: [], ok: false };
}

// Server-side response cache (table `ai_response_cache`, mirrors market_price_cache's shape/RLS)
// for actions where the same normalized input genuinely produces the same answer: same
// inventory snapshot -> same meal/grocery suggestion, same item name -> same price estimate.
// Global/shared cache, not user-scoped — cache_key already encodes every input that affects
// the answer (including dialect, for the two dialect-prefixed actions), so a hit is safe to
// serve to any user with that exact input. Checked before ever calling the LLM.
const AI_CACHE_TTL_MS = 6 * 60 * 60 * 1000; // 6h — short enough that prices/suggestions don't go stale

async function getCachedAiResponse(cacheKey: string): Promise<Record<string, unknown> | null> {
  try {
    const { data } = await supabase.from("ai_response_cache").select("response, created_at").eq("cache_key", cacheKey).maybeSingle();
    if (data?.created_at && Date.now() - new Date(data.created_at).getTime() < AI_CACHE_TTL_MS) {
      return data.response as Record<string, unknown>;
    }
  } catch (e) {
    console.error("[CoreIntel] getCachedAiResponse failed:", e.message);
  }
  return null;
}

async function setCachedAiResponse(cacheKey: string, action: string, response: Record<string, unknown>) {
  try {
    await supabase.from("ai_response_cache").upsert({ cache_key: cacheKey, action, response, created_at: new Date().toISOString() });
  } catch (e) {
    console.error("[CoreIntel] setCachedAiResponse failed:", e.message);
  }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders() });
  if (req.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);

  try {
    const { action, user_id, payload, dialect } = await req.json();
    console.log(`[CoreIntel] action=${action}, user=${user_id}`);

    // توجيه اللهجة/اللغة القادم من MarketPrefs على الجهاز (سعودي/مصري/تركي) —
    // يُحقن قبل أي system prompt نصي عشان الرد يطابق لهجة/لغة بلد المستخدم.
    const dialectPrefix = dialect ? dialect + " " : "";

    let profile = null;
    if (user_id) {
      const { data } = await supabase.from("user_behavior_profile").select("*").eq("user_id", user_id).maybeSingle();
      profile = data;
    }

    switch (action) {

      // ══════════════════════════════════════════════
      // NEW ACTIONS
      // ══════════════════════════════════════════════

      // ──────────────────────────────────────────────
      // MEAL_SUGGESTIONS — Suggest meals from inventory
      // ──────────────────────────────────────────────
      case "meal_suggestions": {
        const { items } = payload || {};
        const cacheKey = "meal_suggestions:" + dialectPrefix + ":" + (items || "");
        const cached = await getCachedAiResponse(cacheKey);
        if (cached) return jsonResponse(cached);

        const systemPrompt = dialectPrefix + "أنت مساعد طبخ ذكي. بناءً على المخزون المتوفر، اقترح وجبات يمكن تحضيرها. أجب بصيغة JSON: {\"text\": \"...\"}";
        const userPrompt = "المخزون: " + (items || "لا يوجد مخزون");
        const result = await callJsonModel(systemPrompt, userPrompt);
        // same honest-failure contract as recipe_details: null/ok:false on a genuine upstream
        // failure instead of baking in Arabic text that looks like a real AI reply. The Kotlin
        // client (ZadAiRepository.suggestMeals) already falls back to its own "لم أتمكن..."
        // string when text is null, so no client change needed.
        const response = { text: result?.text || null, ok: !!result?.text };
        if (response.ok) await setCachedAiResponse(cacheKey, "meal_suggestions", response);
        return jsonResponse(response);
      }

      // ──────────────────────────────────────────────
      // GROCERY_SUGGESTIONS — Suggest groceries to buy
      // ──────────────────────────────────────────────
      case "grocery_suggestions": {
        const { inventory, family_size } = payload || {};
        const cacheKey = "grocery_suggestions:" + dialectPrefix + ":" + (inventory || "") + ":" + (family_size || 4);
        const cached = await getCachedAiResponse(cacheKey);
        if (cached) return jsonResponse(cached);

        const systemPrompt = dialectPrefix + "أنت مساعد تسوق ذكي. بناءً على المخزون الحالي وحجم العائلة، اقترح مشتريات يحتاجها المنزل. أجب بصيغة JSON: {\"suggestions\":[{\"name\":\"\",\"quantity\":\"\",\"reason\":\"\"}]}";
        const userPrompt = "المخزون: " + (inventory || "لا يوجد") + ", حجم العائلة: " + (family_size || 4);
        const result = await callJsonModel(systemPrompt, userPrompt, 2000);
        const response = { suggestions: result?.suggestions || [] };
        if (response.suggestions.length > 0) await setCachedAiResponse(cacheKey, "grocery_suggestions", response);
        return jsonResponse(response);
      }

      // ──────────────────────────────────────────────
      // SPENDING_INSIGHTS — Analyze transactions for patterns
      // ──────────────────────────────────────────────
      case "spending_insights": {
        const { transactions, budget } = payload || {};
        const systemPrompt = dialectPrefix + "أنت محلل مالي. حلل المعاملات المالية وقدم رؤى وتوصيات. أجب بصيغة JSON: {\"insights\":[{\"title\":\"\",\"description\":\"\",\"type\":\"Tip|Prediction|Alert\"}]}";
        const userPrompt = "المعاملات: " + (transactions || "لا توجد") + ", الميزانية: " + (budget || 3500);
        const result = await callJsonModel(systemPrompt, userPrompt, 2000);
        return jsonResponse({ insights: result?.insights || [] });
      }

      // ──────────────────────────────────────────────
      // AGENT_SUMMARY — Full summary of all user data
      // ──────────────────────────────────────────────
      case "agent_summary": {
        const data = payload || {};
        const systemPrompt = dialectPrefix + "أنت وكيل زاد الذكي. حلل بيانات المستخدم بالكامل وقدّم ملخصاً شاملاً. أجب بصيغة JSON: {\"summary\":\"\",\"alerts\":[{\"type\":\"\",\"title\":\"\",\"description\":\"\"}],\"suggestions\":[{\"action\":\"\",\"item\":\"\",\"reason\":\"\"}],\"stats\":{\"inventory_count\":0,\"expiring_soon\":0,\"subscriptions_active\":0,\"days_until_budget_end\":30}}";
        const userPrompt = "المخزون: " + (data.inventory || "") + " | المعاملات: " + (data.transactions || "") + " | الاشتراكات: " + (data.subscriptions || "") + " | الميزانية: " + (data.budget || 0) + " | التسوق: " + (data.shopping || "") + " | الأنماط: " + (data.patterns || "");
        const result = await callJsonModel(systemPrompt, userPrompt, 2500);
        return jsonResponse({
          summary: result?.summary || "",
          alerts: result?.alerts || [],
          suggestions: result?.suggestions || [],
          stats: result?.stats || { inventory_count: 0, expiring_soon: 0, subscriptions_active: 0, days_until_budget_end: 30 },
        });
      }

      // ──────────────────────────────────────────────
      // ANALYZE_BANK_NOTIFICATION — Parse bank SMS
      // ──────────────────────────────────────────────
      case "analyze_bank_notification": {
        const { bank, sms_text } = payload || {};
        const systemPrompt = "أنت محلل رسائل بنكية. استخرج معلومات المعاملة من نص الإشعار البنكي. أجب بصيغة JSON: {\"amount\":0.0,\"title\":\"\",\"is_expense\":true,\"category\":\"\"}";
        const userPrompt = "البنك: " + (bank || "") + " | النص: " + (sms_text || "");
        const result = await callJsonModel(systemPrompt, userPrompt);
        return jsonResponse({
          amount: result?.amount || 0,
          title: result?.title || "",
          is_expense: result?.is_expense ?? true,
          category: result?.category || "عام",
        });
      }

      // ──────────────────────────────────────────────
      // ANALYZE_INVENTORY_IMAGE — Vision: analyze fridge contents
      // ──────────────────────────────────────────────
      case "analyze_inventory_image": {
        const { image_base64, mime_type } = payload || {};
        if (!image_base64) return jsonResponse({ items: [] });
        const systemPrompt = "You are a vision AI. Analyze the image of refrigerator/pantry contents. Identify every food item visible. Return ONLY JSON: {\"items\":[{\"name\":\"\",\"quantity\":1.0,\"unit\":\"قطعة\",\"category\":\"عام\"}]}";
        const userPrompt = "List all food items visible in this image with estimated quantity, unit, and category.";
        // callVisionModel already tries every Groq key then falls back to Gemini internally.
        const visionResult = await callVisionModel(systemPrompt, userPrompt, image_base64, mime_type || "image/jpeg");
        if (!visionResult) {
          console.error("[CoreIntel] analyze_inventory_image: Groq pool and Gemini fallback both returned null content");
          return jsonResponse({ items: [] });
        }
        const objectMatch = visionResult.match(/\{[\s\S]*\}/);
        if (objectMatch) {
          try {
            const parsed = JSON.parse(objectMatch[0]);
            return jsonResponse({ items: parsed.items || [] });
          } catch (e) {
            console.error("[CoreIntel] analyze_inventory_image: JSON.parse (object) failed:", e.message, "raw match:", objectMatch[0]);
          }
        }
        // Smaller free vision models sometimes ignore the {"items":[...]} instruction
        // and reply with a bare array instead — accept that shape too.
        const arrayMatch = visionResult.match(/\[[\s\S]*\]/);
        if (arrayMatch) {
          try {
            const parsed = JSON.parse(arrayMatch[0]);
            return jsonResponse({ items: Array.isArray(parsed) ? parsed : [] });
          } catch (e) {
            console.error("[CoreIntel] analyze_inventory_image: JSON.parse (array) failed:", e.message, "raw match:", arrayMatch[0]);
          }
        }
        console.error("[CoreIntel] analyze_inventory_image: no JSON object or array found in response:", visionResult);
        return jsonResponse({ items: [] });
      }

      // ──────────────────────────────────────────────
      // ANALYZE_RECEIPT — Vision: analyze receipt image
      // ──────────────────────────────────────────────
      case "analyze_receipt": {
        const { image_base64, mime_type } = payload || {};
        if (!image_base64) return jsonResponse({ total: 0, category: "", storeName: "", items: [] });
        const systemPrompt = "You are a receipt scanning AI. Extract all information from this receipt image. Return ONLY JSON: {\"total\":0.0,\"category\":\"\",\"storeName\":\"\",\"items\":[{\"name\":\"\",\"price\":0.0,\"quantity\":1.0,\"unit\":\"قطعة\",\"category\":\"عام\"}]}";
        const userPrompt = "Extract the total amount, store name, category, and all line items from this receipt.";
        // callVisionModel already tries every Groq key then falls back to Gemini internally.
        const visionResult = await callVisionModel(systemPrompt, userPrompt, image_base64, mime_type || "image/jpeg");
        if (visionResult) {
          const jsonMatch = visionResult.match(/\{[\s\S]*\}/);
          if (jsonMatch) {
            try {
              const parsed = JSON.parse(jsonMatch[0]);
              return jsonResponse({
                total: parsed.total || 0,
                category: parsed.category || "",
                storeName: parsed.storeName || "",
                items: parsed.items || [],
              });
            } catch { /* fall through */ }
          }
        }
        return jsonResponse({ total: 0, category: "", storeName: "", items: [] });
      }

      // ──────────────────────────────────────────────
      // FAMILY_ASSISTANT — Family chat AI
      // ──────────────────────────────────────────────
      case "family_assistant": {
        const { message, role } = payload || {};
        if (!message) return jsonResponse({ text: "الرجاء كتابة رسالة." });
        // role=="child" يجي من حساب طفل حقيقي (family_members.role) — برومبت مختلف
        // تماماً يقتصر على وجبات خفيفة صحية ونصائح مصروف بسيطة، ويرفض أي سؤال عن
        // أرقام مالية عائلية (ميزانية، أرصدة، معاملات) بدل ما يجاوب عليه
        const systemPrompt = role === "child"
          ? dialectPrefix + "أنت 'زاد الصغير'، مساعد مرح وودود لطفل في عائلة سعودية. " +
            "تتكلم بأسلوب بسيط وممتع مليان إيموجي. مهمتك فقط: اقتراح وجبات خفيفة وصحية، " +
            "نصائح بسيطة عن توفير المصروف الشخصي، والتشجيع على المهام والادخار. " +
            "لو الطفل سأل عن ميزانية العائلة، أرصدة، معاملات بنكية، أو أي أرقام مالية للعائلة أو لأي فرد فيها، " +
            "اعتذر بلطف وحوّل الموضوع لحاجة ممتعة بدل ما تجاوب — دي بيانات خاصة بالأهل بس."
          : dialectPrefix + "أنت مساعد عائلي ذكي. تجيب بود واختصار. تساعد في إدارة شؤون المنزل، الوصفات، الميزانية، والتسوق.";
        const result = await callTextModel(systemPrompt, message);
        // same honest-failure contract as meal_suggestions/recipe_details: null/ok:false on a
        // genuine upstream failure (rate limit/timeout) instead of baking in Arabic text that
        // reads like a real AI reply — the Kotlin client supplies its own accurate message.
        return jsonResponse({ text: result, ok: result !== null });
      }

      // ──────────────────────────────────────────────
      // ESTIMATE_PRICE — Price estimation for a product
      // ──────────────────────────────────────────────
      case "estimate_price": {
        const { item_name, store } = payload || {};
        const cacheKey = "estimate_price:" + (item_name || "") + ":" + (store || "");
        const cached = await getCachedAiResponse(cacheKey);
        if (cached) return jsonResponse(cached);

        const systemPrompt = "أنت خبير أسعار في السعودية. قدّر سعر المنتج بناءً على اسمه والمتجر (إن وجد). أجب بصيغة JSON: {\"item_name\":\"\",\"low_price\":0.0,\"avg_price\":0.0,\"high_price\":0.0,\"store\":\"\",\"currency\":\"SAR\"}";
        const userPrompt = "المنتج: " + (item_name || "") + ", المتجر: " + (store || "غير محدد");
        const result = await callJsonModel(systemPrompt, userPrompt);
        const response = {
          item_name: result?.item_name || item_name || "",
          low_price: result?.low_price || 0,
          avg_price: result?.avg_price || 0,
          high_price: result?.high_price || 0,
          store: result?.store || store || null,
          currency: "SAR",
        };
        if (result != null) await setCachedAiResponse(cacheKey, "estimate_price", response);
        return jsonResponse(response);
      }

      // ──────────────────────────────────────────────
      // DETECT_SUBSCRIPTIONS — Find subscriptions in transactions
      // ──────────────────────────────────────────────
      case "detect_subscriptions": {
        const { transactions } = payload || {};
        if (!transactions || transactions.length === 0) return jsonResponse({ subscriptions: [] });
        const systemPrompt = "أنت محلل اشتراكات. حلل قائمة المعاملات وحدد أي منها قد يكون اشتراكاً شهرياً أو سنوياً. أجب بصيغة JSON: {\"subscriptions\":[{\"name\":\"\",\"amount\":0.0,\"frequency\":\"monthly\",\"confidence\":0.0,\"next_billing_date\":\"\"}]}";
        const userPrompt = "المعاملات: " + JSON.stringify(transactions);
        const result = await callJsonModel(systemPrompt, userPrompt, 2000);
        return jsonResponse({ subscriptions: result?.subscriptions || [] });
      }

      // ──────────────────────────────────────────────
      // RECIPE_DETAILS — Get detailed recipe
      // ──────────────────────────────────────────────
      case "recipe_details": {
        const { recipe_name, inventory } = payload || {};
        const systemPrompt = dialectPrefix + "أنت شيف عربي محترف. قدم وصفة مفصلة تشمل المكونات والخطوات. أجب بصيغة JSON: {\"text\":\"...\"}";
        const userPrompt = "الوصفة: " + (recipe_name || "") + " | المخزون المتوفر: " + (inventory || "لا يوجد");
        const result = await callJsonModel(systemPrompt, userPrompt);
        // no baked-in Arabic fallback here anymore — a null/missing text means the upstream
        // call genuinely failed (timeout/HTTP error/bad JSON), and the client needs to know
        // that so it can show a retry affordance instead of rendering this as a real recipe.
        return jsonResponse({ text: result?.text || null, ok: !!result?.text });
      }

      // ──────────────────────────────────────────────
      // BEHAVIOR_ANALYSIS — Analyze behavior patterns
      // ──────────────────────────────────────────────
      case "behavior_analysis": {
        const { category, transactions, current_patterns } = payload || {};
        const systemPrompt = dialectPrefix + "أنت محلل سلوك مالي. حلل نمط الإنفاق في فئة معينة وقدّم توقعات ونصائح. أجب بصيغة JSON: {\"insight\":\"\",\"avg_spending\":0.0,\"trend\":\"stable\",\"tip\":\"\",\"predicted_next\":0.0,\"confidence\":0.0}";
        const userPrompt = "الفئة: " + (category || "") + " | المعاملات: " + (transactions || "لا توجد") + " | الأنماط الحالية: " + (current_patterns || "");
        const result = await callJsonModel(systemPrompt, userPrompt);
        return jsonResponse({
          insight: result?.insight || "",
          avg_spending: result?.avg_spending || 0,
          trend: result?.trend || "stable",
          tip: result?.tip || "",
          predicted_next: result?.predicted_next || 0,
          confidence: result?.confidence || 0,
        });
      }

      // ──────────────────────────────────────────────
      // EXPENSE_PREDICTION — Predict future expenses
      // ──────────────────────────────────────────────
      case "expense_prediction": {
        const { transactions, budget, patterns } = payload || {};
        const systemPrompt = dialectPrefix + "أنت خبير توقعات مالية. بناءً على المعاملات السابقة والأنماط، توقع المصروفات القادمة. أجب بصيغة JSON: {\"predicted_total\":0.0,\"confidence\":0.0,\"breakdown\":[{\"category\":\"\",\"predicted\":0.0,\"avg_monthly\":0.0}],\"warnings\":[],\"tips\":[]}";
        const userPrompt = "المعاملات: " + JSON.stringify(transactions || []) + " | الميزانية: " + (budget || 0) + " | الأنماط: " + JSON.stringify(patterns || []);
        const result = await callJsonModel(systemPrompt, userPrompt, 2500);
        return jsonResponse({
          predicted_total: result?.predicted_total || 0,
          confidence: result?.confidence || 0,
          breakdown: result?.breakdown || [],
          warnings: result?.warnings || [],
          tips: result?.tips || [],
        });
      }

      // ──────────────────────────────────────────────
      // BILL_CLASSIFICATION — Classify a bill/payment
      // ──────────────────────────────────────────────
      case "bill_classification": {
        const { title, amount } = payload || {};
        const systemPrompt = "أنت مصنف فواتير. صنف هذه الفاتورة بناءً على عنوانها ومبلغها. أجب بصيغة JSON: {\"type\":\"\",\"provider\":\"\",\"category\":\"\",\"confidence\":0.0,\"is_recurring\":false,\"suggested_frequency_days\":null}";
        const userPrompt = "العنوان: " + (title || "") + " | المبلغ: " + (amount || 0);
        const result = await callJsonModel(systemPrompt, userPrompt);
        return jsonResponse({
          type: result?.type || "other",
          provider: result?.provider || null,
          category: result?.category || "عام",
          confidence: result?.confidence || 0,
          is_recurring: result?.is_recurring || false,
          suggested_frequency_days: result?.suggested_frequency_days || null,
        });
      }

      // ──────────────────────────────────────────────
      // FAMILY_ANALYSIS — Analyze family data
      // ──────────────────────────────────────────────
      case "family_analysis": {
        const { members, tasks, goals, tasbiha, transactions } = payload || {};
        const systemPrompt = dialectPrefix + "أنت محلل عائلي. حلل بيانات العائلة وقدّم ملخصاً شاملاً وتوصيات. أجب بصيغة JSON: {\"family_summary\":\"\",\"member_highlights\":[{\"name\":\"\",\"achievement\":\"\",\"suggestion\":\"\"}],\"family_health_score\":50,\"suggested_goal\":\"\",\"fun_fact\":\"\"}";
        const userPrompt = "الأعضاء: " + (members || "") + " | المهام: " + (tasks || "") + " | الأهداف: " + (goals || "") + " | التسبيحات: " + (tasbiha || "") + " | المعاملات: " + (transactions || "");
        const result = await callJsonModel(systemPrompt, userPrompt, 2000);
        return jsonResponse({
          family_summary: result?.family_summary || "",
          member_highlights: result?.member_highlights || [],
          family_health_score: result?.family_health_score || 50,
          suggested_goal: result?.suggested_goal || "",
          fun_fact: result?.fun_fact || "",
        });
      }

      // ──────────────────────────────────────────────
      // AUTO_SUGGEST — Generate smart suggestions
      // ──────────────────────────────────────────────
      case "auto_suggest": {
        const { context, inventory, transactions, patterns } = payload || {};
        const systemPrompt = dialectPrefix + "أنت مساعد اقتراحات ذكي. بناءً على سياق المستخدم، اقترح إجراءات مفيدة. أجب بصيغة JSON: {\"suggestions\":[{\"action\":\"\",\"title\":\"\",\"description\":\"\",\"priority\":\"medium\",\"emoji\":\"\"}]}";
        const userPrompt = "السياق: " + (context || "") + " | المخزون: " + (inventory || "") + " | المعاملات: " + (transactions || "") + " | الأنماط: " + (patterns || "");
        const result = await callJsonModel(systemPrompt, userPrompt, 2000);
        return jsonResponse({ suggestions: result?.suggestions || [] });
      }

      // ──────────────────────────────────────────────
      // FAMILY_GOALS_SUGGEST — Suggest family savings goal
      // ──────────────────────────────────────────────
      case "family_goals_suggest": {
        const { members, total_balance, completed_tasks, tasbiha_score } = payload || {};
        const systemPrompt = dialectPrefix + "أنت مستشار أهداف عائلية. بناءً على بيانات العائلة، اقترح هدف ادخار مناسب. أجب بصيغة JSON: {\"goal_title\":\"\",\"target_amount\":0.0,\"reward_suggestion\":\"\",\"duration_days\":30,\"emoji\":\"\"}";
        const userPrompt = "الأعضاء: " + (members || "") + " | الرصيد: " + (total_balance || 0) + " | المهام المنجزة: " + (completed_tasks || 0) + " | التسبيحات: " + (tasbiha_score || 0);
        const result = await callJsonModel(systemPrompt, userPrompt);
        return jsonResponse({
          goal_title: result?.goal_title || "",
          target_amount: result?.target_amount || 0,
          reward_suggestion: result?.reward_suggestion || "",
          duration_days: result?.duration_days || 30,
          emoji: result?.emoji || "",
        });
      }

      // ══════════════════════════════════════════════
      // LIVE WEB SEARCH ACTIONS — groq/compound only, zero mock data
      // ══════════════════════════════════════════════

      // ──────────────────────────────────────────────
      // FETCH_LIVE_DEALS — real store promotions for shortage items
      // (Deal Matcher)
      // ──────────────────────────────────────────────
      case "fetch_live_deals": {
        const { items, location } = payload || {};
        if (!items || items.length === 0) return jsonResponse({ deals: [] });
        const systemPrompt = "أنت باحث عروض تسوق حقيقي. ابحث في الويب عن أحدث العروض والتخفيضات الفعلية المتاحة الآن من متاجر ومحلات سوبرماركت معروفة في المنطقة المحددة للأصناف المطلوبة. لا تخترع أي متجر أو سعر أو نسبة خصم أبداً — إذا لم تجد عرضاً حقيقياً موثقاً لصنف معين، تجاهله تماماً. أجب فقط بمصفوفة JSON بدون أي نص إضافي بالشكل: [{\"item\":\"\",\"store\":\"\",\"price\":0.0,\"discount_percent\":0.0,\"note\":\"\"}]. إذا لم تجد أي عروض حقيقية لأي صنف، أرجع مصفوفة فارغة [].";
        const userPrompt = "المنطقة: " + (location || "السعودية") + " | الأصناف المطلوب البحث عن عروض لها: " + (Array.isArray(items) ? items.join("، ") : items);
        const result = await callCompoundSearch(systemPrompt, userPrompt);
        const deals = Array.isArray(result?.parsed) ? result.parsed : [];
        return jsonResponse({ deals, sources: result?.executedTools || [], ok: result?.ok !== false });
      }

      // ──────────────────────────────────────────────
      // FETCH_PRICE_SHOCK_WARNINGS — real inflation/price-trend news
      // (Price Shock Predictor)
      // ──────────────────────────────────────────────
      case "fetch_price_shock_warnings": {
        const { categories, location } = payload || {};
        if (!categories || categories.length === 0) return jsonResponse({ warnings: [] });
        const systemPrompt = "أنت محلل اقتصادي يعتمد على مصادر إخبارية حقيقية فقط. ابحث في الويب عن آخر الأخبار والتقارير الاقتصادية الموثوقة (خلال آخر أسبوعين فقط) عن اتجاهات أسعار السلع والتضخم في المنطقة المحددة للفئات المطلوبة. لا تخترع أي نسبة أو خبر أبداً — إذا لم تجد تقريراً حقيقياً حديثاً وموثوقاً عن فئة معينة، تجاهلها تماماً. أجب فقط بمصفوفة JSON بدون أي نص إضافي بالشكل: [{\"category\":\"\",\"expected_change_pct\":0.0,\"direction\":\"up|down\",\"reasoning\":\"\",\"source_note\":\"\"}]. إذا لم تجد أي تقارير حقيقية حديثة، أرجع مصفوفة فارغة [].";
        const userPrompt = "المنطقة: " + (location || "السعودية") + " | الفئات المطلوب تحليل اتجاه أسعارها: " + (Array.isArray(categories) ? categories.join("، ") : categories);
        const result = await callCompoundSearch(systemPrompt, userPrompt);
        const warnings = Array.isArray(result?.parsed) ? result.parsed : [];
        return jsonResponse({ warnings, sources: result?.executedTools || [], ok: result?.ok !== false });
      }

      // ──────────────────────────────────────────────
      // FETCH_LIVE_MARKET_PRICES — Zad Live Market Ticker: real daily prices
      // for essential commodities (fuel, produce, gold...), 12h server cache
      // keyed by market/region to keep the home-screen ticker fast and avoid
      // burning the shared groq/compound-mini TPM budget on every app open.
      // ──────────────────────────────────────────────
      case "fetch_live_market_prices": {
        const { location } = payload || {};
        const marketLoc = location || "السعودية";
        const CACHE_TTL_MS = 12 * 60 * 60 * 1000;

        const { data: cached } = await supabase
          .from("market_price_cache")
          .select("prices, updated_at")
          .eq("market", marketLoc)
          .maybeSingle();

        if (cached?.updated_at && Date.now() - new Date(cached.updated_at).getTime() < CACHE_TTL_MS) {
          return jsonResponse({ prices: cached.prices || [], cached: true, ok: true });
        }

        // Narrowed from an earlier 6-item basket (fuel, tomato, gold, sugar, rice, chicken) —
        // fuel and gold are nationally regulated/single-quoted prices published daily by Saudi
        // outlets (Aramco monthly fuel pricing, gold-price trackers), genuinely searchable as
        // one canonical number, unlike per-store retail produce prices (that's what
        // fetch_live_deals covers instead). A concrete JSON example (few-shot) improves format
        // adherence on smaller agentic models.
        //
        // Live-diagnosed (2026-07-24, see callCompoundSearch comment above): compound-mini's
        // web_search DOES run and DOES find real pages, but the model sometimes drops the found
        // data when writing its final JSON — a non-deterministic extraction miss, not a missing
        // search. The explicit "do not return an empty array if you found real data" line below
        // plus one retry-on-empty here (cheap: this whole action is itself gated by a 12h cache,
        // so a second compound-mini call only ever happens once per market per 12h, not per
        // app-open) meaningfully raises the odds of a real result over a single attempt.
        const systemPrompt = "أنت باحث أسعار سلع حقيقي. يجب عليك استخدام أداة البحث في الويب (web_search) فعلياً الآن لهذا الطلب — لا تجاوب من معرفتك السابقة أبداً. ابحث عن آخر أسعار البنزين (91 و95) وسعر جرام الذهب (عيار 21 وعيار 24) اليوم في المنطقة المحددة، من مصادر إخبارية أو مواقع أسعار موثوقة. لا تخترع أي رقم أبداً — إذا لم تجد سعراً حقيقياً موثقاً لصنف معين، تجاهله تماماً ولا تدرجه. مهم جداً: لو نتائج البحث فيها سعر حقيقي واضح، لازم تستخرجه وتحطه في الـ JSON — ممنوع ترجع مصفوفة فارغة وعندك بيانات حقيقية قدامك من البحث. أجب فقط بمصفوفة JSON صالحة بدون أي نص أو شرح أو markdown إضافي. مثال على الشكل المطلوب بالضبط:\n[{\"symbol\":\"بنزين 91\",\"price\":2.18,\"unit\":\"لتر\",\"change_percent\":0.0,\"trend\":\"flat\"},{\"symbol\":\"ذهب عيار 21\",\"price\":298.5,\"unit\":\"جرام\",\"change_percent\":1.2,\"trend\":\"up\"}]\nإذا لم تجد أي سعر حقيقي لأي صنف بعد بحث فعلي، أرجع مصفوفة فارغة [].";
        const userPrompt = "ابحث الآن في الويب عن: سعر بنزين 91، سعر بنزين 95، سعر جرام الذهب عيار 21، سعر جرام الذهب عيار 24 — في: " + marketLoc + " اليوم.";

        let result = await callCompoundSearch(systemPrompt, userPrompt);
        let prices = Array.isArray(result?.parsed) ? result.parsed : [];
        // one retry when the first attempt came back genuinely empty (ok:true, zero items) —
        // see comment above on why this is a real, non-deterministic extraction miss worth retrying
        if (result?.ok !== false && prices.length === 0) {
          result = await callCompoundSearch(systemPrompt, userPrompt);
          prices = Array.isArray(result?.parsed) ? result.parsed : [];
        }

        if (result?.ok !== false && prices.length > 0) {
          await supabase.from("market_price_cache").upsert({
            market: marketLoc,
            prices,
            updated_at: new Date().toISOString(),
          });
        }

        return jsonResponse({ prices, sources: result?.executedTools || [], ok: result?.ok !== false, cached: false });
      }

      // ──────────────────────────────────────────────
      // AI_TEXT — Generic text generation
      // ──────────────────────────────────────────────
      case "ai_text": {
        const { system_prompt, user_prompt, response_mime_type } = payload || {};
        if (response_mime_type === "application/json") {
          const result = await callJsonModel(system_prompt || "", user_prompt || "");
          return jsonResponse({ text: JSON.stringify(result) });
        }
        const result = await callTextModel(system_prompt || "", user_prompt || "");
        // same honest-failure contract — null/ok:false on genuine upstream failure, no baked
        // Arabic fallback text (was previously blaming "الاتصال" for what's actually an
        // OpenRouter free-tier rate limit/timeout, not a real connectivity failure).
        return jsonResponse({ text: result, ok: result !== null });
      }

      // ──────────────────────────────────────────────
      // BRAIN_EVALUATE — Evaluate state and decide actions
      // ──────────────────────────────────────────────
      case "brain_evaluate": {
        const { system_prompt, user_prompt } = payload || {};
        const result = await callTextModel(system_prompt || "", user_prompt || "", 2000, 0.3);
        return jsonResponse({ text: result, ok: result !== null });
      }

      // ──────────────────────────────────────────────
      // VOICE_AGENT — Process voice command
      // ──────────────────────────────────────────────
      case "voice_agent": {
        const { audio_base64, mime_type } = payload || {};
        if (!audio_base64) return jsonResponse({ action: "chat", message: "", data: null });

        const sttResult = await transcribeAudio(audio_base64, mime_type || "audio/m4a");
        const transcript = sttResult.text;
        if (!transcript) {
          console.error("[CoreIntel] voice_agent: transcription failed or empty");
          return jsonResponse({ action: "chat", message: "لم أتمكن من فهم الصوت، حاول مرة أخرى.", data: null });
        }
        console.log("[CoreIntel] voice_agent transcript:", transcript);

        const systemPrompt = dialectPrefix + "You are a voice command processor for a family finance app (ZAD). " +
          "The user spoke a command, possibly with local dialect and colloquial number words. " +
          "Determine the intent and extract structured data. Parse spoken amounts (e.g. \"خمسين ريال\" = 50, \"مية وعشرين\" = 120) into a numeric value. " +
          "Return ONLY JSON: {\"action\":\"chat|add_expense|add_income|check_budget|add_inventory|log_pharmacy_dose\",\"message\":\"short confirmation reply matching the requested dialect/language\",\"data\":{\"amount\":0,\"title\":\"\",\"category\":\"\"}}. " +
          "Use action=\"add_expense\" when the user says they spent/paid money, \"add_income\" when they received money, \"check_budget\" when they ask about their budget/balance, \"add_inventory\" when they mention buying/adding a physical item to track, " +
          "\"log_pharmacy_dose\" when the user says they took/used a medication or pill (put the medication name in data.title, leave data.amount as 0), otherwise \"chat\".";
        const result = await callJsonModel(systemPrompt, transcript);
        return jsonResponse({
          action: result?.action || "chat",
          message: result?.message || "",
          data: result?.data || null,
          transcript,
        });
      }

      // ──────────────────────────────────────────────
      // SEASONAL_FORECAST — Predict upcoming seasonal/event spending from
      // the family's own transaction history (statistical, not LLM-guessed
      // numbers). zad_transactions has no family-scoped RLS read policy
      // (only auth.uid() = user_id), so the cross-member aggregation runs
      // here via SECURITY DEFINER RPCs on the service-role client, not in
      // the Kotlin client. The LLM is used only to phrase a tip on top of
      // the already-computed numbers, same pattern as expense_prediction.
      // ──────────────────────────────────────────────
      case "seasonal_forecast": {
        const { events } = payload || {};
        const { data: fm } = await supabase
          .from("family_members")
          .select("family_id")
          .eq("user_id", user_id)
          .maybeSingle();

        if (!fm?.family_id || !Array.isArray(events) || events.length === 0) {
          return jsonResponse({ forecasts: [] });
        }
        const familyId = fm.family_id;

        const { data: statsRows } = await supabase.rpc("get_family_category_monthly_stats", { p_family_id: familyId });
        const baseline: Record<string, { avgMonthly: number; monthCount: number }> = {};
        for (const row of statsRows || []) {
          baseline[row.category] = { avgMonthly: Number(row.avg_monthly) || 0, monthCount: Number(row.month_count) || 0 };
        }

        // slug -> category -> multiplier, used only when there's not enough
        // of the family's own history for that category to trust a ratio.
        const FALLBACK_MULTIPLIERS: Record<string, Record<string, number>> = {
          ramadan: { "طعام": 1.35, "مطاعم": 1.3 },
          eid_al_fitr: { "تسوق": 1.6, "مطاعم": 1.3 },
          eid_al_adha: { "طعام": 1.4, "تسوق": 1.2 },
          back_to_school: { "تسوق": 1.8 },
        };
        const DEFAULT_MULTIPLIER = 1.15;

        const now = Date.now();
        const forecasts = [];
        for (const ev of events) {
          const categoryTags: string[] = Array.isArray(ev?.category_tags) ? ev.category_tags : [];
          const eventStart = new Date(ev?.event_start);
          const eventEnd = new Date(ev?.event_end);
          if (isNaN(eventStart.getTime()) || isNaN(eventEnd.getTime())) continue;
          const windowDays = Math.max(1, Math.round((eventEnd.getTime() - eventStart.getTime()) / 86400000));
          const daysUntil = Math.ceil((eventStart.getTime() - now) / 86400000);

          // Find last year's occurrence of this event to compare actual
          // window spend against the family's monthly baseline.
          let historyWindowSpend: Record<string, number> | null = null;
          if (ev?.id) {
            const { data: pastWindows } = await supabase
              .from("seasonal_event_windows")
              .select("start_date, end_date")
              .eq("event_id", ev.id)
              .lt("start_date", new Date(now).toISOString())
              .order("start_date", { ascending: false })
              .limit(1);
            const pastWindow = pastWindows?.[0];
            if (pastWindow) {
              const { data: spendRows } = await supabase.rpc("get_family_event_window_spend", {
                p_family_id: familyId,
                p_start: pastWindow.start_date,
                p_end: pastWindow.end_date,
              });
              historyWindowSpend = {};
              for (const row of spendRows || []) historyWindowSpend[row.category] = Number(row.total_amount) || 0;
            }
          }

          let predictedTotal = 0;
          let confidenceSum = 0;
          const breakdown = [];
          for (const category of categoryTags) {
            const base = baseline[category];
            const baseAvgMonthly = base?.avgMonthly || 0;
            const dailyBaseline = baseAvgMonthly / 30;
            let multiplier = FALLBACK_MULTIPLIERS[ev.slug]?.[category] || DEFAULT_MULTIPLIER;
            let source = "fallback";
            let confidence = 0.4;

            const historySpend = historyWindowSpend?.[category] || 0;
            if (historySpend > 0 && base && base.monthCount >= 2 && dailyBaseline > 0) {
              const historyMultiplier = historySpend / windowDays / dailyBaseline;
              multiplier = Math.min(3.0, Math.max(1.0, historyMultiplier));
              source = "history";
              confidence = 0.75;
            }

            const predicted = dailyBaseline * windowDays * multiplier;
            predictedTotal += predicted;
            confidenceSum += confidence;
            breakdown.push({
              category,
              predicted: Math.round(predicted * 100) / 100,
              baseline_monthly_avg: baseAvgMonthly,
              multiplier_used: Math.round(multiplier * 100) / 100,
              source,
            });
          }
          if (breakdown.length === 0) continue;

          forecasts.push({
            event_id: ev.id || "",
            slug: ev.slug || null,
            days_until: daysUntil,
            predicted_total: Math.round(predictedTotal * 100) / 100,
            confidence: Math.round((confidenceSum / breakdown.length) * 100) / 100,
            breakdown,
            tip: "",
          });
        }

        if (forecasts.length > 0) {
          const systemPrompt = dialectPrefix + "أنت مستشار مالي عائلي. لديك تنبؤات مصاريف محسوبة إحصائياً لمناسبات قادمة. اكتب نصيحة عملية قصيرة (جملة واحدة) لكل مناسبة تساعد العائلة تستعد مالياً. أجب بصيغة JSON فقط: {\"tips\":[{\"event_id\":\"\",\"tip\":\"\"}]}";
          const userPrompt = "المناسبات: " + JSON.stringify(forecasts.map((f) => ({ event_id: f.event_id, slug: f.slug, days_until: f.days_until, predicted_total: f.predicted_total, breakdown: f.breakdown })));
          const narrated = await callJsonModel(systemPrompt, userPrompt, 1200);
          const tipsByEvent: Record<string, string> = {};
          for (const t of narrated?.tips || []) {
            if (t?.event_id) tipsByEvent[t.event_id] = t.tip || "";
          }
          for (const f of forecasts) f.tip = tipsByEvent[f.event_id] || "";
        }

        return jsonResponse({ forecasts });
      }

      // ──────────────────────────────────────────────
      // DEFAULT
      // ──────────────────────────────────────────────
      default:
        return jsonResponse({ error: "Unknown action: " + action }, 400);
    }
  } catch (e) {
    console.error("[CoreIntel] Error: " + e.message);
    return jsonResponse({ error: e.message }, 500);
  }
});
