// deno-lint-ignore-file
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.6";

// GROQ_API_KEY is now used ONLY for audio transcription (transcribeAudio) —
// OpenRouter has no free-tier audio endpoint (confirmed: 404 on
// /v1/audio/transcriptions, and chat-based audio input models require a
// paid balance even on ":free"-suffixed models). Every other AI call in
// this file — text, JSON, vision — runs on the unified OPENROUTER_API_KEY.
const GROQ_API_KEY = Deno.env.get("GROQ_API_KEY");

const OPENROUTER_API_KEY = Deno.env.get("OPENROUTER_API_KEY");
const OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
const TEXT_MODEL = "openai/gpt-oss-20b:free";
const VISION_MODEL = "nvidia/nemotron-nano-12b-v2-vl:free";
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

function classifyTransaction(title: string, amount: number, category?: string): string {
  const t = title?.toLowerCase() || "";
  const knownPatterns: Record<string, string[]> = {
    food: [
      "restaurant", "grocery", "supermarket", "panda", "carrefour",
      "مطعم", "مطاعم", "بقالة", "هايبر", "اسواق", "سوبرماركت", "طعام", "أكل",
      "كافيه", "كافيتيريا", "وجبة", "ساندويتش", "بيتزا", "برجر", "دجاج"
    ],
    transport: [
      "uber", "taxi", "fuel", "gas", "careem",
      "وقود", "بنزين", "سيارة", "نقل", "توصيل", "موصل", "باص", "حافلة",
      "تاكسي", "رحلة", "أوبر", "كريم"
    ],
    telecom: [
      "stc", "mobily", "zain", "mobile", "phone",
      "اتصالات", "موبايلي", "زين", "اتصال", "انترنت", "شبكة", "هاتف"
    ],
    subscription: [
      "monthly", "subscription", "netflix", "spotify", "shahid",
      "اشتراك", "شهري", "سنوي", "نتفليكس", "شاهد", "يوتيوب", "ابل", "قوقل"
    ],
    rent: [
      "rent", "apartment", "housing",
      "إيجار", "سكن", "شقة", "فيلا", "استئجار"
    ],
    health: [
      "hospital", "doctor", "clinic", "pharmacy", "medicine",
      "مستشفى", "دكتور", "طبيب", "صيدلية", "دواء", "علاج", "عيادة", "صحة"
    ],
  };

  const categoryMap: Record<string, string> = {
    food: "طعام",
    transport: "مواصلات",
    telecom: "اتصالات",
    subscription: "اشتراكات",
    rent: "إيجار",
    health: "صحة",
  };

  if (category && Object.keys(categoryMap).some(k => categoryMap[k] === category)) return category;
  for (const [key, keywords] of Object.entries(knownPatterns)) {
    for (const kw of keywords) {
      if (t.includes(kw.toLowerCase())) return categoryMap[key];
    }
  }
  return "";
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

async function callTextModel(systemPrompt: string, userPrompt: string, maxTokens = 1000, temperature = 0.7) {
  if (!OPENROUTER_API_KEY) return null;
  try {
    const resp = await fetch(OPENROUTER_URL, {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + OPENROUTER_API_KEY,
        "Content-Type": "application/json",
        "HTTP-Referer": "https://zad-app.com",
        "X-Title": "Zad",
      },
      body: JSON.stringify({
        model: TEXT_MODEL,
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: userPrompt },
        ],
        temperature,
        max_tokens: Math.max(maxTokens, 300),
        reasoning: { effort: "low" },
      }),
      signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
    });
    const data = await resp.json();
    if (!resp.ok) {
      console.error("[CoreIntel] OpenRouter text HTTP error:", resp.status, JSON.stringify(data));
    }
    return data.choices?.[0]?.message?.content || null;
  } catch (e) {
    console.error("[CoreIntel] callTextModel failed/timed out:", e.message);
    return null;
  }
}

async function callVisionModel(systemPrompt: string, userPrompt: string, imageBase64: string, mimeType: string) {
  if (!OPENROUTER_API_KEY) return { content: null, raw: { error: "OPENROUTER_API_KEY not set" }, ok: false, status: 0 };
  try {
    const resp = await fetch(OPENROUTER_URL, {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + OPENROUTER_API_KEY,
        "Content-Type": "application/json",
        "HTTP-Referer": "https://zad-app.com",
        "X-Title": "Zad",
      },
      body: JSON.stringify({
        model: VISION_MODEL,
        messages: [
          { role: "system", content: systemPrompt },
          {
            role: "user",
            content: [
              { type: "text", text: userPrompt },
              { type: "image_url", image_url: { url: "data:" + mimeType + ";base64," + imageBase64 } },
            ],
          },
        ],
        temperature: 0.2,
        max_tokens: 2000,
      }),
      signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
    });
    const data = await resp.json();
    console.log("[CoreIntel] OpenRouter vision raw response:", JSON.stringify(data));
    if (!resp.ok) {
      console.error("[CoreIntel] OpenRouter vision HTTP error:", resp.status, JSON.stringify(data));
    }
    return { content: data.choices?.[0]?.message?.content || null, raw: data, ok: resp.ok, status: resp.status };
  } catch (e) {
    console.error("[CoreIntel] callVisionModel failed/timed out:", e.message);
    return { content: null, raw: { error: e.message }, ok: false, status: 0 };
  }
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

async function callJsonModel(systemPrompt: string, userPrompt: string, maxTokens = 1500) {
  if (!OPENROUTER_API_KEY) return null;
  try {
    const resp = await fetch(OPENROUTER_URL, {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + OPENROUTER_API_KEY,
        "Content-Type": "application/json",
        "HTTP-Referer": "https://zad-app.com",
        "X-Title": "Zad",
      },
      body: JSON.stringify({
        model: TEXT_MODEL,
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: userPrompt },
        ],
        response_format: { type: "json_object" },
        temperature: 0.2,
        max_tokens: Math.max(maxTokens, 300),
        reasoning: { effort: "low" },
      }),
      signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
    });
    const data = await resp.json();
    if (!resp.ok) {
      console.error("[CoreIntel] OpenRouter json HTTP error:", resp.status, JSON.stringify(data));
    }
    const text = data.choices?.[0]?.message?.content || "{}";
    try { return JSON.parse(text); } catch (e) {
      console.error("[CoreIntel] callJsonModel: JSON.parse failed:", e.message, "raw:", text);
      return null;
    }
  } catch (e) {
    console.error("[CoreIntel] callJsonModel failed/timed out:", e.message);
    return null;
  }
}

// groq/compound is Groq's agentic system with a built-in, Tavily-backed
// web_search tool — used ONLY for the two live-search actions below so
// results are grounded in real pages, never invented. Reuses GROQ_API_KEY
// (already provisioned for Whisper), no new secret needed. Compound
// sometimes wraps its JSON answer in prose/markdown fences despite
// instructions, so we extract leniently like callJsonModel() does.
// `ok` distinguishes a hard failure (missing key, HTTP error, timeout, unparsable
// response) from a genuinely successful search that just found nothing — callers
// used to collapse both into the same empty array, so real outages looked
// identical to "no deals right now" in the UI.
async function callCompoundSearch(systemPrompt: string, userPrompt: string, maxTokens = 1500) {
  if (!GROQ_API_KEY) return { parsed: null, executedTools: [], ok: false };
  try {
    const resp = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + GROQ_API_KEY,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "groq/compound",
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

      // ──────────────────────────────────────────────
      // CHAT — General AI chat
      // ──────────────────────────────────────────────
      case "chat": {
        const { message, history } = payload || {};
        if (!message) return jsonResponse({ error: "Message required" });
        const systemPrompt = dialectPrefix + "You are ZAD, smart assistant for home and budget management." + (profile
          ? " User profile: weekly avg " + profile.avg_weekly_spending + " SAR, top categories " + JSON.stringify(profile.top_spending_categories) + ", subscriptions " + profile.subscription_load_monthly + " SAR"
          : " New user - no behavior data yet."
        ) + " Be helpful and accurate. Give financial advice.";
        if (!OPENROUTER_API_KEY) return jsonResponse({ reply: "AI not available." });
        const chatResp = await fetch(OPENROUTER_URL, {
          method: "POST",
          headers: {
            "Authorization": "Bearer " + OPENROUTER_API_KEY,
            "Content-Type": "application/json",
            "HTTP-Referer": "https://zad-app.com",
            "X-Title": "Zad",
          },
          body: JSON.stringify({
            model: TEXT_MODEL,
            messages: [
              { role: "system", content: systemPrompt },
              ...(history || []).slice(-10),
              { role: "user", content: message },
            ],
            temperature: 0.7,
            max_tokens: 1000,
            reasoning: { effort: "low" },
          }),
        });
        const chatData = await chatResp.json();
        if (!chatResp.ok) console.error("[CoreIntel] chat OpenRouter error:", chatResp.status, JSON.stringify(chatData));
        return jsonResponse({ reply: chatData.choices?.[0]?.message?.content || "Sorry, could not respond." });
      }

      // ──────────────────────────────────────────────
      // CLASSIFY — Bill classification (rule-based + AI)
      // ──────────────────────────────────────────────
      case "classify": {
        const { title, amount, category } = payload || {};
        const ruleResult = classifyTransaction(title, amount, category);
        if (ruleResult) return jsonResponse({ category: ruleResult, method: "rules" });
        const result = await callJsonModel(
          "Classify transaction into one of: food, transport, telecom, subscription, rent, health, entertainment, education, clothing, other. Respond JSON with key category.",
          "Title: " + title + " Amount: " + amount + " SAR",
          200
        );
        return jsonResponse({ category: result?.category || "other", method: result ? "ai" : "fallback" });
      }

      // ──────────────────────────────────────────────
      // INSIGHT — Behavior profile insight
      // ──────────────────────────────────────────────
      case "insight": {
        if (!profile) return jsonResponse({ insight: "Use the app for a few days to generate insights." });
        const insightText = "Financial Summary: Weekly avg " + profile.avg_weekly_spending + " SAR. Top category: " + (profile.top_spending_categories?.[0]?.category || "-") + ". Monthly subscriptions: " + profile.subscription_load_monthly + " SAR. Advice: " + (profile.subscription_load_monthly > profile.avg_weekly_spending * 0.5 ? "Subscriptions are high. Review and save." : "Good job! Keep tracking.");
        return jsonResponse({ insight: insightText });
      }

      // ──────────────────────────────────────────────
      // PREDICTION — Expense prediction based on profile
      // ──────────────────────────────────────────────
      case "prediction": {
        if (!profile) return jsonResponse({ prediction: null });
        const weeklyAvg = profile.avg_weekly_spending || 0;
        const subLoad = profile.subscription_load_monthly || 0;
        const predictedWeekly = Math.round((weeklyAvg + subLoad / 4) * 100) / 100;
        return jsonResponse({
          prediction: {
            next_week: predictedWeekly,
            next_month: Math.round(predictedWeekly * 4 * 100) / 100,
            based_on: "Based on weekly avg " + weeklyAvg + " SAR and monthly subscriptions " + subLoad + " SAR.",
          },
        });
      }

      // ══════════════════════════════════════════════
      // NEW ACTIONS
      // ══════════════════════════════════════════════

      // ──────────────────────────────────────────────
      // MEAL_SUGGESTIONS — Suggest meals from inventory
      // ──────────────────────────────────────────────
      case "meal_suggestions": {
        const { items } = payload || {};
        const systemPrompt = dialectPrefix + "أنت مساعد طبخ ذكي. بناءً على المخزون المتوفر، اقترح وجبات يمكن تحضيرها. أجب بصيغة JSON: {\"text\": \"...\"}";
        const userPrompt = "المخزون: " + (items || "لا يوجد مخزون");
        const result = await callJsonModel(systemPrompt, userPrompt);
        return jsonResponse({ text: result?.text || "لم أتمكن من إيجاد اقتراحات حالياً." });
      }

      // ──────────────────────────────────────────────
      // GROCERY_SUGGESTIONS — Suggest groceries to buy
      // ──────────────────────────────────────────────
      case "grocery_suggestions": {
        const { inventory, family_size } = payload || {};
        const systemPrompt = dialectPrefix + "أنت مساعد تسوق ذكي. بناءً على المخزون الحالي وحجم العائلة، اقترح مشتريات يحتاجها المنزل. أجب بصيغة JSON: {\"suggestions\":[{\"name\":\"\",\"quantity\":\"\",\"reason\":\"\"}]}";
        const userPrompt = "المخزون: " + (inventory || "لا يوجد") + ", حجم العائلة: " + (family_size || 4);
        const result = await callJsonModel(systemPrompt, userPrompt, 2000);
        return jsonResponse({ suggestions: result?.suggestions || [] });
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
        const visionResult = (await callVisionModel(systemPrompt, userPrompt, image_base64, mime_type || "image/jpeg")).content;
        if (!visionResult) {
          console.error("[CoreIntel] analyze_inventory_image: callVisionModel returned null content");
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
        const visionResult = (await callVisionModel(systemPrompt, userPrompt, image_base64, mime_type || "image/jpeg")).content;
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
        const { message } = payload || {};
        if (!message) return jsonResponse({ text: "الرجاء كتابة رسالة." });
        const systemPrompt = dialectPrefix + "أنت مساعد عائلي ذكي. تجيب بود واختصار. تساعد في إدارة شؤون المنزل، الوصفات، الميزانية، والتسوق.";
        const result = await callTextModel(systemPrompt, message);
        return jsonResponse({ text: result || "عفواً، تعذر الاتصال." });
      }

      // ──────────────────────────────────────────────
      // ESTIMATE_PRICE — Price estimation for a product
      // ──────────────────────────────────────────────
      case "estimate_price": {
        const { item_name, store } = payload || {};
        const systemPrompt = "أنت خبير أسعار في السعودية. قدّر سعر المنتج بناءً على اسمه والمتجر (إن وجد). أجب بصيغة JSON: {\"item_name\":\"\",\"low_price\":0.0,\"avg_price\":0.0,\"high_price\":0.0,\"store\":\"\",\"currency\":\"SAR\"}";
        const userPrompt = "المنتج: " + (item_name || "") + ", المتجر: " + (store || "غير محدد");
        const result = await callJsonModel(systemPrompt, userPrompt);
        return jsonResponse({
          item_name: result?.item_name || item_name || "",
          low_price: result?.low_price || 0,
          avg_price: result?.avg_price || 0,
          high_price: result?.high_price || 0,
          store: result?.store || store || null,
          currency: "SAR",
        });
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
      // AI_TEXT — Generic text generation (used by callGeminiText)
      // ──────────────────────────────────────────────
      case "ai_text": {
        const { system_prompt, user_prompt, response_mime_type } = payload || {};
        if (response_mime_type === "application/json") {
          const result = await callJsonModel(system_prompt || "", user_prompt || "");
          return jsonResponse({ text: JSON.stringify(result) });
        }
        const result = await callTextModel(system_prompt || "", user_prompt || "");
        return jsonResponse({ text: result || "تعذر الاتصال بالذكاء الاصطناعي." });
      }

      // ──────────────────────────────────────────────
      // BRAIN_EVALUATE — Evaluate state and decide actions
      // ──────────────────────────────────────────────
      case "brain_evaluate": {
        const { system_prompt, user_prompt } = payload || {};
        const result = await callTextModel(system_prompt || "", user_prompt || "", 2000, 0.3);
        return jsonResponse({ text: result || "تعذر التقييم." });
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
          "Return ONLY JSON: {\"action\":\"chat|add_expense|add_income|check_budget|add_inventory\",\"message\":\"short confirmation reply matching the requested dialect/language\",\"data\":{\"amount\":0,\"title\":\"\",\"category\":\"\"}}. " +
          "Use action=\"add_expense\" when the user says they spent/paid money, \"add_income\" when they received money, \"check_budget\" when they ask about their budget/balance, \"add_inventory\" when they mention buying/adding a physical item to track, otherwise \"chat\".";
        const result = await callJsonModel(systemPrompt, transcript);
        return jsonResponse({
          action: result?.action || "chat",
          message: result?.message || "",
          data: result?.data || null,
          transcript,
        });
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
