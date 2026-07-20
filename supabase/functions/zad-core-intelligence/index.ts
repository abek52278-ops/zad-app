// deno-lint-ignore-file
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.6";

const GROQ_API_KEY = Deno.env.get("GROQ_API_KEY");
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const TEXT_MODEL = "llama-3.3-70b-versatile";
const VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";
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


async function callGroqText(systemPrompt: string, userPrompt: string, maxTokens = 1000, temperature = 0.7) {
  if (!GROQ_API_KEY) return null;
  const groqResp = await fetch(GROQ_URL, {
    method: "POST",
    headers: { "Authorization": "Bearer " + GROQ_API_KEY, "Content-Type": "application/json" },
    body: JSON.stringify({
      model: TEXT_MODEL,
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: userPrompt },
      ],
      temperature,
      max_tokens: maxTokens,
    }),
  });
  const data = await groqResp.json();
  return data.choices?.[0]?.message?.content || null;
}

async function callGroqVision(systemPrompt: string, userPrompt: string, imageBase64: string, mimeType: string) {
  if (!GROQ_API_KEY) return null;
  const groqResp = await fetch(GROQ_URL, {
    method: "POST",
    headers: { "Authorization": "Bearer " + GROQ_API_KEY, "Content-Type": "application/json" },
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
  });
  const data = await groqResp.json();
  console.log("[CoreIntel] Groq vision raw response:", JSON.stringify(data));
  if (!groqResp.ok) {
    console.error("[CoreIntel] Groq vision HTTP error:", groqResp.status, JSON.stringify(data));
  }
  return data.choices?.[0]?.message?.content || null;
}

async function callGroqJson(systemPrompt: string, userPrompt: string, maxTokens = 1500) {
  if (!GROQ_API_KEY) return null;
  const groqResp = await fetch(GROQ_URL, {
    method: "POST",
    headers: { "Authorization": "Bearer " + GROQ_API_KEY, "Content-Type": "application/json" },
    body: JSON.stringify({
      model: TEXT_MODEL,
      messages: [
        { role: "system", content: systemPrompt },
        { role: "user", content: userPrompt },
      ],
      response_format: { type: "json_object" },
      temperature: 0.2,
      max_tokens: maxTokens,
    }),
  });
  const data = await groqResp.json();
  const text = data.choices?.[0]?.message?.content || "{}";
  try { return JSON.parse(text); } catch { return null; }
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders() });
  if (req.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);

  try {
    const { action, user_id, payload } = await req.json();
    console.log(`[CoreIntel] action=${action}, user=${user_id}`);

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
        const systemPrompt = "You are ZAD, smart assistant for home and budget management." + (profile
          ? " User profile: weekly avg " + profile.avg_weekly_spending + " SAR, top categories " + JSON.stringify(profile.top_spending_categories) + ", subscriptions " + profile.subscription_load_monthly + " SAR"
          : " New user - no behavior data yet."
        ) + " Be helpful and accurate. Answer in Arabic. Give financial advice.";
        if (!GROQ_API_KEY) return jsonResponse({ reply: "AI not available." });
        const groqResp = await fetch(GROQ_URL, {
          method: "POST",
          headers: { "Authorization": "Bearer " + GROQ_API_KEY, "Content-Type": "application/json" },
          body: JSON.stringify({
            model: TEXT_MODEL,
            messages: [
              { role: "system", content: systemPrompt },
              ...(history || []).slice(-10),
              { role: "user", content: message },
            ],
            temperature: 0.7,
            max_tokens: 1000,
          }),
        });
        const groqData = await groqResp.json();
        return jsonResponse({ reply: groqData.choices?.[0]?.message?.content || "Sorry, could not respond." });
      }

      // ──────────────────────────────────────────────
      // CLASSIFY — Bill classification (rule-based + AI)
      // ──────────────────────────────────────────────
      case "classify": {
        const { title, amount, category } = payload || {};
        const ruleResult = classifyTransaction(title, amount, category);
        if (ruleResult) return jsonResponse({ category: ruleResult, method: "rules" });
        if (GROQ_API_KEY) {
          const groqResp = await fetch(GROQ_URL, {
            method: "POST",
            headers: { "Authorization": "Bearer " + GROQ_API_KEY, "Content-Type": "application/json" },
            body: JSON.stringify({
              model: TEXT_MODEL,
              messages: [
                { role: "system", content: "Classify transaction into one of: food, transport, telecom, subscription, rent, health, entertainment, education, clothing, other. Respond JSON with key category." },
                { role: "user", content: "Title: " + title + " Amount: " + amount + " SAR" },
              ],
              response_format: { type: "json_object" },
              temperature: 0.1,
              max_tokens: 50,
            }),
          });
          const data = await groqResp.json();
          const result = JSON.parse(data.choices?.[0]?.message?.content || "{}");
          return jsonResponse({ category: result.category || "other", method: "ai" });
        }
        return jsonResponse({ category: "other", method: "fallback" });
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
        const systemPrompt = "أنت مساعد طبخ ذكي. بناءً على المخزون المتوفر، اقترح وجبات يمكن تحضيرها. أجب بصيغة JSON: {\"text\": \"...\"}";
        const userPrompt = "المخزون: " + (items || "لا يوجد مخزون");
        const result = await callGroqJson(systemPrompt, userPrompt);
        return jsonResponse({ text: result?.text || "لم أتمكن من إيجاد اقتراحات حالياً." });
      }

      // ──────────────────────────────────────────────
      // GROCERY_SUGGESTIONS — Suggest groceries to buy
      // ──────────────────────────────────────────────
      case "grocery_suggestions": {
        const { inventory, family_size } = payload || {};
        const systemPrompt = "أنت مساعد تسوق ذكي. بناءً على المخزون الحالي وحجم العائلة، اقترح مشتريات يحتاجها المنزل. أجب بصيغة JSON: {\"suggestions\":[{\"name\":\"\",\"quantity\":\"\",\"reason\":\"\"}]}";
        const userPrompt = "المخزون: " + (inventory || "لا يوجد") + ", حجم العائلة: " + (family_size || 4);
        const result = await callGroqJson(systemPrompt, userPrompt, 2000);
        return jsonResponse({ suggestions: result?.suggestions || [] });
      }

      // ──────────────────────────────────────────────
      // SPENDING_INSIGHTS — Analyze transactions for patterns
      // ──────────────────────────────────────────────
      case "spending_insights": {
        const { transactions, budget } = payload || {};
        const systemPrompt = "أنت محلل مالي. حلل المعاملات المالية وقدم رؤى وتوصيات. أجب بصيغة JSON: {\"insights\":[{\"title\":\"\",\"description\":\"\",\"type\":\"Tip|Prediction|Alert\"}]}";
        const userPrompt = "المعاملات: " + (transactions || "لا توجد") + ", الميزانية: " + (budget || 3500);
        const result = await callGroqJson(systemPrompt, userPrompt, 2000);
        return jsonResponse({ insights: result?.insights || [] });
      }

      // ──────────────────────────────────────────────
      // AGENT_SUMMARY — Full summary of all user data
      // ──────────────────────────────────────────────
      case "agent_summary": {
        const data = payload || {};
        const systemPrompt = "أنت وكيل زاد الذكي. حلل بيانات المستخدم بالكامل وقدّم ملخصاً شاملاً. أجب بصيغة JSON: {\"summary\":\"\",\"alerts\":[{\"type\":\"\",\"title\":\"\",\"description\":\"\"}],\"suggestions\":[{\"action\":\"\",\"item\":\"\",\"reason\":\"\"}],\"stats\":{\"inventory_count\":0,\"expiring_soon\":0,\"subscriptions_active\":0,\"days_until_budget_end\":30}}";
        const userPrompt = "المخزون: " + (data.inventory || "") + " | المعاملات: " + (data.transactions || "") + " | الاشتراكات: " + (data.subscriptions || "") + " | الميزانية: " + (data.budget || 0) + " | التسوق: " + (data.shopping || "") + " | الأنماط: " + (data.patterns || "");
        const result = await callGroqJson(systemPrompt, userPrompt, 2500);
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
        const result = await callGroqJson(systemPrompt, userPrompt);
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
        const visionResult = await callGroqVision(systemPrompt, userPrompt, image_base64, mime_type || "image/jpeg");
        if (!visionResult) {
          console.error("[CoreIntel] analyze_inventory_image: callGroqVision returned null (missing GROQ_API_KEY or fetch/HTTP failure)");
          return jsonResponse({ items: [] });
        }
        const jsonMatch = visionResult.match(/\{[\s\S]*\}/);
        if (!jsonMatch) {
          console.error("[CoreIntel] analyze_inventory_image: no JSON object found in Groq response:", visionResult);
          return jsonResponse({ items: [] });
        }
        try {
          const parsed = JSON.parse(jsonMatch[0]);
          return jsonResponse({ items: parsed.items || [] });
        } catch (e) {
          console.error("[CoreIntel] analyze_inventory_image: JSON.parse failed:", e.message, "raw match:", jsonMatch[0]);
          return jsonResponse({ items: [] });
        }
      }

      // ──────────────────────────────────────────────
      // ANALYZE_RECEIPT — Vision: analyze receipt image
      // ──────────────────────────────────────────────
      case "analyze_receipt": {
        const { image_base64, mime_type } = payload || {};
        if (!image_base64) return jsonResponse({ total: 0, category: "", storeName: "", items: [] });
        const systemPrompt = "You are a receipt scanning AI. Extract all information from this receipt image. Return ONLY JSON: {\"total\":0.0,\"category\":\"\",\"storeName\":\"\",\"items\":[{\"name\":\"\",\"price\":0.0,\"quantity\":1.0,\"unit\":\"قطعة\",\"category\":\"عام\"}]}";
        const userPrompt = "Extract the total amount, store name, category, and all line items from this receipt.";
        const visionResult = await callGroqVision(systemPrompt, userPrompt, image_base64, mime_type || "image/jpeg");
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
        const systemPrompt = "أنت مساعد عائلي ذكي. تجيب باللغة العربية بود واختصار. تساعد في إدارة شؤون المنزل، الوصفات، الميزانية، والتسوق.";
        const result = await callGroqText(systemPrompt, message);
        return jsonResponse({ text: result || "عفواً، تعذر الاتصال." });
      }

      // ──────────────────────────────────────────────
      // ESTIMATE_PRICE — Price estimation for a product
      // ──────────────────────────────────────────────
      case "estimate_price": {
        const { item_name, store } = payload || {};
        const systemPrompt = "أنت خبير أسعار في السعودية. قدّر سعر المنتج بناءً على اسمه والمتجر (إن وجد). أجب بصيغة JSON: {\"item_name\":\"\",\"low_price\":0.0,\"avg_price\":0.0,\"high_price\":0.0,\"store\":\"\",\"currency\":\"SAR\"}";
        const userPrompt = "المنتج: " + (item_name || "") + ", المتجر: " + (store || "غير محدد");
        const result = await callGroqJson(systemPrompt, userPrompt);
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
        const result = await callGroqJson(systemPrompt, userPrompt, 2000);
        return jsonResponse({ subscriptions: result?.subscriptions || [] });
      }

      // ──────────────────────────────────────────────
      // RECIPE_DETAILS — Get detailed recipe
      // ──────────────────────────────────────────────
      case "recipe_details": {
        const { recipe_name, inventory } = payload || {};
        const systemPrompt = "أنت شيف عربي محترف. قدم وصفة مفصلة باللغة العربية تشمل المكونات والخطوات. أجب بصيغة JSON: {\"text\":\"...\"}";
        const userPrompt = "الوصفة: " + (recipe_name || "") + " | المخزون المتوفر: " + (inventory || "لا يوجد");
        const result = await callGroqJson(systemPrompt, userPrompt);
        return jsonResponse({ text: result?.text || "لم أتمكن من إيجاد تفاصيل الوصفة حالياً." });
      }

      // ──────────────────────────────────────────────
      // BEHAVIOR_ANALYSIS — Analyze behavior patterns
      // ──────────────────────────────────────────────
      case "behavior_analysis": {
        const { category, transactions, current_patterns } = payload || {};
        const systemPrompt = "أنت محلل سلوك مالي. حلل نمط الإنفاق في فئة معينة وقدّم توقعات ونصائح. أجب بصيغة JSON: {\"insight\":\"\",\"avg_spending\":0.0,\"trend\":\"stable\",\"tip\":\"\",\"predicted_next\":0.0,\"confidence\":0.0}";
        const userPrompt = "الفئة: " + (category || "") + " | المعاملات: " + (transactions || "لا توجد") + " | الأنماط الحالية: " + (current_patterns || "");
        const result = await callGroqJson(systemPrompt, userPrompt);
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
        const systemPrompt = "أنت خبير توقعات مالية. بناءً على المعاملات السابقة والأنماط، توقع المصروفات القادمة. أجب بصيغة JSON: {\"predicted_total\":0.0,\"confidence\":0.0,\"breakdown\":[{\"category\":\"\",\"predicted\":0.0,\"avg_monthly\":0.0}],\"warnings\":[],\"tips\":[]}";
        const userPrompt = "المعاملات: " + JSON.stringify(transactions || []) + " | الميزانية: " + (budget || 0) + " | الأنماط: " + JSON.stringify(patterns || []);
        const result = await callGroqJson(systemPrompt, userPrompt, 2500);
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
        const result = await callGroqJson(systemPrompt, userPrompt);
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
        const systemPrompt = "أنت محلل عائلي. حلل بيانات العائلة وقدّم ملخصاً شاملاً وتوصيات. أجب بصيغة JSON: {\"family_summary\":\"\",\"member_highlights\":[{\"name\":\"\",\"achievement\":\"\",\"suggestion\":\"\"}],\"family_health_score\":50,\"suggested_goal\":\"\",\"fun_fact\":\"\"}";
        const userPrompt = "الأعضاء: " + (members || "") + " | المهام: " + (tasks || "") + " | الأهداف: " + (goals || "") + " | التسبيحات: " + (tasbiha || "") + " | المعاملات: " + (transactions || "");
        const result = await callGroqJson(systemPrompt, userPrompt, 2000);
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
        const systemPrompt = "أنت مساعد اقتراحات ذكي. بناءً على سياق المستخدم، اقترح إجراءات مفيدة. أجب بصيغة JSON: {\"suggestions\":[{\"action\":\"\",\"title\":\"\",\"description\":\"\",\"priority\":\"medium\",\"emoji\":\"\"}]}";
        const userPrompt = "السياق: " + (context || "") + " | المخزون: " + (inventory || "") + " | المعاملات: " + (transactions || "") + " | الأنماط: " + (patterns || "");
        const result = await callGroqJson(systemPrompt, userPrompt, 2000);
        return jsonResponse({ suggestions: result?.suggestions || [] });
      }

      // ──────────────────────────────────────────────
      // FAMILY_GOALS_SUGGEST — Suggest family savings goal
      // ──────────────────────────────────────────────
      case "family_goals_suggest": {
        const { members, total_balance, completed_tasks, tasbiha_score } = payload || {};
        const systemPrompt = "أنت مستشار أهداف عائلية. بناءً على بيانات العائلة، اقترح هدف ادخار مناسب. أجب بصيغة JSON: {\"goal_title\":\"\",\"target_amount\":0.0,\"reward_suggestion\":\"\",\"duration_days\":30,\"emoji\":\"\"}";
        const userPrompt = "الأعضاء: " + (members || "") + " | الرصيد: " + (total_balance || 0) + " | المهام المنجزة: " + (completed_tasks || 0) + " | التسبيحات: " + (tasbiha_score || 0);
        const result = await callGroqJson(systemPrompt, userPrompt);
        return jsonResponse({
          goal_title: result?.goal_title || "",
          target_amount: result?.target_amount || 0,
          reward_suggestion: result?.reward_suggestion || "",
          duration_days: result?.duration_days || 30,
          emoji: result?.emoji || "",
        });
      }

      // ──────────────────────────────────────────────
      // AI_TEXT — Generic text generation (used by callGeminiText)
      // ──────────────────────────────────────────────
      case "ai_text": {
        const { system_prompt, user_prompt, response_mime_type } = payload || {};
        if (response_mime_type === "application/json") {
          const result = await callGroqJson(system_prompt || "", user_prompt || "");
          return jsonResponse({ text: JSON.stringify(result) });
        }
        const result = await callGroqText(system_prompt || "", user_prompt || "");
        return jsonResponse({ text: result || "تعذر الاتصال بالذكاء الاصطناعي." });
      }

      // ──────────────────────────────────────────────
      // BRAIN_EVALUATE — Evaluate state and decide actions
      // ──────────────────────────────────────────────
      case "brain_evaluate": {
        const { system_prompt, user_prompt } = payload || {};
        const result = await callGroqText(system_prompt || "", user_prompt || "", 2000, 0.3);
        return jsonResponse({ text: result || "تعذر التقييم." });
      }

      // ──────────────────────────────────────────────
      // VOICE_AGENT — Process voice command
      // ──────────────────────────────────────────────
      case "voice_agent": {
        const { audio_base64 } = payload || {};
        if (!audio_base64) return jsonResponse({ action: "chat", message: "", data: null });
        const systemPrompt = "You are a voice command processor for ZAD app. Analyze the transcribed text and determine the intent. Return JSON: {\"action\":\"chat|add_expense|add_income|check_budget|add_inventory\",\"message\":\"response in Arabic\",\"data\":{\"amount\":0,\"title\":\"\",\"category\":\"\"}}";
        const userPrompt = "Voice command transcript would be processed here. For now, return a default response.";
        const result = await callGroqJson(systemPrompt, userPrompt);
        return jsonResponse({
          action: result?.action || "chat",
          message: result?.message || "",
          data: result?.data || null,
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
