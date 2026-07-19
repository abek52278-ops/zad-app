// deno-lint-ignore-file

const GROQ_API_KEY = Deno.env.get("GROQ_API_KEY");
const GROQ_MODEL = "llama-3.3-70b-versatile";
const GROQ_VISION_MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

interface AIRequest {
  request_type: string;
  payload: Record<string, unknown>;
}

interface GroqMessage {
  role: "system" | "user" | "assistant";
  content: string | GroqContentPart[];
}

interface GroqContentPart {
  type: "text" | "image_url";
  text?: string;
  image_url?: { url: string };
}

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

function errorResponse(message: string, status = 400) {
  return jsonResponse({ error: message }, status);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders() });
  if (req.method !== "POST") return errorResponse("Method not allowed", 405);

  try {
    const { request_type, payload } = (await req.json()) as AIRequest;
    console.log(`[ZadAI] type=${request_type}`);
    if (!GROQ_API_KEY) return errorResponse("GROQ_API_KEY missing", 500);

    switch (request_type) {
      case "meal_suggestions": return await handleMealSuggestions(payload);
      case "receipt_analysis": return await handleReceiptAnalysis(payload);
      case "spending_insights": return await handleSpendingInsights(payload);
      case "bank_sms_parsing": return await handleBankSMS(payload);
      case "subscription_detection": return await handleSubscriptionDetection(payload);
      case "grocery_suggestions": return await handleGrocerySuggestions(payload);
      case "chat": return await handleChat(payload);
      case "inventory_scan": return await handleInventoryScan(payload);
      case "recipe_details": return await handleRecipeDetails(payload);
      case "brain_evaluate": return await handleBrainEvaluate(payload);
      case "ai_text": return await handleAiText(payload);
      case "price_estimation": return await handlePriceEstimation(payload);
      case "expense_prediction": return await handleExpensePrediction(payload);
      case "bill_classification": return await handleBillClassification(payload);
      case "agent_summary": return await handleAgentSummary(payload);
      case "text_analysis": return await handleTextAnalysis(payload);
      case "voice_transcription": return await handleVoiceTranscription(payload);
      case "family_analysis": return await handleFamilyAnalysis(payload);
      case "auto_suggest": return await handleAutoSuggest(payload);
      case "family_goals_suggest": return await handleFamilyGoalsSuggest(payload);
      case "behavior_learning": return await handleBehaviorLearning(payload);
      default: return errorResponse(`Unknown: ${request_type}`);
    }
  } catch (err: any) {
    console.error("[ZadAI] Error:", err.message);
    return errorResponse(err.message || "Internal error", 500);
  }
});

async function callGroq(messages: GroqMessage[], model = GROQ_MODEL, jsonMode = false): Promise<string> {
  const body: Record<string, any> = { model, messages };
  if (jsonMode) body.response_format = { type: "json_object" };
  const res = await fetch(GROQ_URL, {
    method: "POST",
    headers: { "Authorization": `Bearer ${GROQ_API_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await res.json();
  if (!res.ok) throw new Error(`Groq API ${res.status}: ${data.error?.message || JSON.stringify(data)}`);
  return data.choices?.[0]?.message?.content?.trim() || "";
}

async function callGroqText(prompt: string, systemPrompt?: string, jsonMode = false): Promise<string> {
  const messages: GroqMessage[] = [];
  if (systemPrompt) messages.push({ role: "system", content: systemPrompt });
  messages.push({ role: "user", content: prompt });
  return callGroq(messages, GROQ_MODEL, jsonMode);
}

function buildVisionContent(text: string, imageBase64: string, mimeType: string): GroqContentPart[] {
  return [
    { type: "text", text },
    { type: "image_url", image_url: { url: `data:${mimeType};base64,${imageBase64}` } },
  ];
}

async function callGroqVision(text: string, imageBase64: string, mimeType: string, systemPrompt?: string): Promise<string> {
  const messages: GroqMessage[] = [];
  if (systemPrompt) messages.push({ role: "system", content: systemPrompt });
  messages.push({ role: "user", content: buildVisionContent(text, imageBase64, mimeType) });
  return callGroq(messages, GROQ_VISION_MODEL);
}

function parseJson(text: string): any {
  const cleaned = text.replace(/```json|```/g, "").trim();
  const start = cleaned.indexOf("{");
  const end = cleaned.lastIndexOf("}");
  return JSON.parse(start !== -1 && end >= start ? cleaned.substring(start, end + 1) : cleaned);
}

function extractArray(obj: any): any[] {
  if (Array.isArray(obj)) return obj;
  for (const key of Object.keys(obj)) {
    if (Array.isArray(obj[key])) return obj[key];
  }
  return [];
}

async function handleMealSuggestions(payload: Record<string, unknown>) {
  const items = (payload.items as string) || "";
  if (!items.trim()) return jsonResponse({ text: "لا يوجد مخزون كافٍ لاقتراح وجبات." });
  const prompt = `انت مساعد طبخ ذكي. المستخدم لديه:\n${items}\nاقترح 3 وجبات مختصرة باللغة العربية.`;
  try {
    const text = await callGroqText(prompt);
    return jsonResponse({ text });
  } catch (err: any) {
    return errorResponse("meal error: " + err.message, 500);
  }
}

async function handleReceiptAnalysis(payload: Record<string, unknown>) {
  const imageBase64 = payload.image_base64 as string;
  const mimeType = (payload.mime_type as string) || "image/jpeg";
  if (!imageBase64) return errorResponse("Missing image_base64");
  const prompt = `Analyze this receipt. Return ONLY valid JSON: {"total":<number>,"storeName":<string>,"category":<string>,"items":[{"name":<string>,"price":<number>,"quantity":<number>,"unit":<string>}]}`;
  try {
    const text = await callGroqVision(prompt, imageBase64, mimeType);
    const parsed = parseJson(text);
    return jsonResponse({
      total: parsed.total || 0,
      storeName: parsed.storeName || "",
      category: parsed.category || "Other",
      items: parsed.items || []
    });
  } catch (err: any) {
    return errorResponse("receipt error: " + err.message, 500);
  }
}

async function handleSpendingInsights(payload: Record<string, unknown>) {
  const transactions = (payload.transactions as string[]) || [];
  const budget = (payload.budget as number) || 3500;
  if (transactions.length === 0) return jsonResponse({ insights: [] });
  const prompt = `مستشار مالي. ميزانية: ${budget} ريال. معاملات:\n${transactions.join("\n")}\nأعطِ 3 رؤى. JSON فقط:\n[{"title":"...","description":"...","type":"Tip|Warning|Prediction"}]`;
  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse({ insights: extractArray(JSON.parse(text)) });
  } catch (err: any) {
    return errorResponse("insights error: " + err.message, 500);
  }
}

async function handleBankSMS(payload: Record<string, unknown>) {
  const bank = (payload.bank as string) || "";
  const sms = (payload.sms_text as string) || "";
  const prompt = `محلل رسائل بنكية. بنك: "${bank}". الرسالة:\n${sms}\nأعد JSON فقط: {"amount":<number>,"title":<string>,"is_expense":<boolean>,"category":<string>}`;
  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse(JSON.parse(text));
  } catch (err: any) {
    return errorResponse("sms error: " + err.message, 500);
  }
}

async function handleSubscriptionDetection(payload: Record<string, unknown>) {
  const txs = (payload.transactions as any[]) || [];
  if (txs.length === 0) return jsonResponse({ subscriptions: [] });
  const prompt = `محلل مالي. اكتشف الاشتراكات المتكررة.\nJSON فقط: [{"name":<str>,"amount":<num>,"frequency":"monthly|yearly","confidence":<0-1>}]\nمعاملات: ${JSON.stringify(txs)}`;
  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse({ subscriptions: extractArray(JSON.parse(text)) });
  } catch (err: any) {
    return errorResponse("subscription error: " + err.message, 500);
  }
}

async function handleGrocerySuggestions(payload: Record<string, unknown>) {
  const inventory = (payload.inventory as string) || "";
  const familySize = (payload.family_size as number) || 4;
  const prompt = `مساعد تسوق. عائلة ${familySize} أشخاص. مخزون:\n${inventory}\nاقترح المشتريات الناقصة. JSON فقط: [{"name":"...","quantity":"...","reason":"..."}]`;
  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse({ suggestions: extractArray(JSON.parse(text)) });
  } catch (err: any) {
    return errorResponse("grocery error: " + err.message, 500);
  }
}

async function handleChat(payload: Record<string, unknown>) {
  const message = (payload.message as string) || "";
  if (!message.trim()) return jsonResponse({ text: "اهلاً! كيف اقدر اساعدك؟" });
  const sysPrompt = `انت "زاد"، مساعد ذكي لادارة المنزل. اجب بالعربية بايجاز وود.`;
  try {
    const text = await callGroqText(message, sysPrompt);
    return jsonResponse({ text });
  } catch (err: any) {
    return errorResponse("chat error: " + err.message, 500);
  }
}

async function handleInventoryScan(payload: Record<string, unknown>) {
  const imageBase64 = payload.image_base64 as string;
  const mimeType = (payload.mime_type as string) || "image/jpeg";
  if (!imageBase64) return errorResponse("Missing image_base64");
  const prompt = `You are an AI inventory scanner for a Saudi family. Extract all food and household items visible in this image. Return ONLY valid JSON with no markdown: {"items":[{"name":"<Arabic name>","quantity":<number>,"unit":"<unit string like حبة/كجم/لتر>","category":"<category like بقالة/خضار/فواكه/لحوم/ألبان>"}]}. Be thorough — list every item you can see. For each item, estimate the quantity visible. If you can't identify specific items, return {"items":[]} and nothing else.`;
  try {
    const text = await callGroqVision(prompt, imageBase64, mimeType);
    const cleaned = text.replace(/```json|```/g, "").trim();
    const start = cleaned.indexOf("{");
    const end = cleaned.lastIndexOf("}");
    if (start === -1 || end < start) return jsonResponse({ items: [], error: "Groq returned unparseable response" });
    const parsed = JSON.parse(cleaned.substring(start, end + 1));
    const items = parsed.items || [];
    return jsonResponse({ items, count: items.length });
  } catch (err: any) {
    console.error("[InventoryScan] Groq error:", err.message);
    return jsonResponse({ items: [], error: err.message, hint: "حاول التصوير بإضاءة أقوى أو من زاوية مختلفة" });
  }
}
}

async function handleRecipeDetails(payload: Record<string, unknown>) {
  const recipeName = (payload.recipe_name as string) || "";
  const inventory = (payload.inventory as string) || "لا يوجد مخزون حاليا";
  if (!recipeName.trim()) return errorResponse("Missing recipe_name");
  const prompt = `You are a chef. Recipe for "${recipeName}" in Arabic. Ingredients (mark available from: ${inventory}) and steps. Return JSON with key 'text'.`;
  try {
    const text = await callGroqText(prompt, undefined, true);
    const parsed = JSON.parse(text);
    return jsonResponse({ text: parsed.text || text });
  } catch (err: any) {
    return errorResponse("recipe_details error: " + err.message, 500);
  }
}

async function handleBrainEvaluate(payload: Record<string, unknown>) {
  const systemPrompt = (payload.system_prompt as string) || "";
  const userPrompt = (payload.user_prompt as string) || "";
  try {
    const text = await callGroqText(userPrompt, systemPrompt || undefined);
    return jsonResponse({ text });
  } catch (err: any) {
    return errorResponse("brain_evaluate error: " + err.message, 500);
  }
}

async function handlePriceEstimation(payload: Record<string, unknown>) {
  const item = (payload.item_name as string) || "";
  const store = (payload.store as string) || "";
  if (!item.trim()) return errorResponse("Missing item_name");
  const context = store ? ` at ${store}` : "";
  const prompt = `You are a Saudi market price expert. Estimate the current price for "${item}"${context} in SAR.
Return ONLY valid JSON with no markdown: {"item_name":"${item}","low_price":<number>,"avg_price":<number>,"high_price":<number>,"store":<string>,"currency":"SAR","last_updated":"today"}
Keep prices realistic for Saudi Arabia.`;
  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse(JSON.parse(text));
  } catch (err: any) {
    return errorResponse("price error: " + err.message, 500);
  }
}

async function handleExpensePrediction(payload: Record<string, unknown>) {
  const transactions = (payload.transactions as any[]) || [];
  const budget = (payload.budget as number) || 0;
  const patterns = (payload.patterns as any[]) || [];
  if (transactions.length === 0) return errorResponse("Not enough data");
  const prompt = `You are a financial AI. Analyze spending patterns to predict next month's expenses.
Current budget: ${budget} SAR
Recent transactions: ${JSON.stringify(transactions.slice(-30))}
Behavior patterns: ${JSON.stringify(patterns)}

Return ONLY valid JSON:
{
  "predicted_total": <number>,
  "confidence": <0-1>,
  "breakdown": [{"category": <string>, "predicted": <number>, "avg_monthly": <number>}],
  "warnings": [<string>],
  "tips": [<string>]
}
Predict next month's total spending in SAR based on historical patterns.`;
  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse(JSON.parse(text));
  } catch (err: any) {
    return errorResponse("prediction error: " + err.message, 500);
  }
}

async function handleBillClassification(payload: Record<string, unknown>) {
  const title = (payload.title as string) || "";
  const amount = (payload.amount as number) || 0;
  if (!title.trim()) return errorResponse("Missing title");
  const prompt = `Classify this financial transaction into a bill/subscription category.
Transaction: "${title}", Amount: ${amount} SAR

Return ONLY JSON:
{
  "type": "subscription" | "utility" | "installment" | "insurance" | "tax" | "other",
  "provider": <string or null>,
  "category": <string>,
  "confidence": <0-1>,
  "is_recurring": <boolean>,
  "suggested_frequency_days": <number or null>
}
Providers examples: STC, Mobily, Zain, SEC (electricity), NWC (water), Netflix, Shahid, Spotify, Bank, Tawuniya, etc.`;
  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse(JSON.parse(text));
  } catch (err: any) {
    return jsonResponse({ type: "other", provider: null, category: "عام", confidence: 0, is_recurring: false, suggested_frequency_days: null });
  }
}

async function handleAgentSummary(payload: Record<string, unknown>) {
  const inventory = (payload.inventory as string) || "فارغ";
  const transactions = (payload.transactions as string) || "لا توجد";
  const subscriptions = (payload.subscriptions as string) || "لا توجد";
  const budget = (payload.budget as number) || 0;
  const shopping = (payload.shopping as string) || "فارغ";
  const patterns = (payload.patterns as string) || "لا توجد";

  const prompt = `أنت 'زاد' الوكيل العائلي الذكي. حلل البيانات التالية واكتب تقريراً مختصراً وبصيغة ودية.

الميزانية: ${budget} ريال
المخزون: ${inventory}
آخر المعاملات: ${transactions}
الاشتراكات: ${subscriptions}
قائمة التسوق: ${shopping}
الأنماط السلوكية: ${patterns}

أعد JSON فقط بهذا الشكل:
{
  "summary": "ملخص عام مختصر (جملة أو جملتين)",
  "alerts": [{"type":"warning|info|success","title":"...","description":"..."}],
  "suggestions": [{"action":"add_to_shopping|check_budget|cook_meal|review_subscription","item":"...","reason":"..."}],
  "stats": {"inventory_count": <number>,"expiring_soon": <number>,"subscriptions_active": <number>,"days_until_budget_end": <number>}
}
كن دقيقاً ومختصراً.`;
  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse(JSON.parse(text));
  } catch (err: any) {
    return jsonResponse({
      summary: "زاد يجهز تحليلك...",
      alerts: [],
      suggestions: [],
      stats: { inventory_count: 0, expiring_soon: 0, subscriptions_active: 0, days_until_budget_end: 30 }
    });
  }
}

async function handleAiText(payload: Record<string, unknown>) {
  const systemPrompt = (payload.system_prompt as string) || "";
  const userPrompt = (payload.user_prompt as string) || "";
  const responseMimeType = (payload.response_mime_type as string) || "text/plain";
  try {
    const text = await callGroqText(userPrompt, systemPrompt || undefined, responseMimeType === "application/json");
    return jsonResponse({ text });
  } catch (err: any) {
    return errorResponse("ai_text error: " + err.message, 500);
  }
}

async function handleTextAnalysis(payload: Record<string, unknown>) {
  const text = (payload.text as string) || "";
  if (!text.trim()) return errorResponse("Missing text");
  const intent = (payload.intent as string) || "general";
  const sysPromptMap: Record<string, string> = {
    "poll": "You generate poll/survey questions in Arabic. Return ONLY JSON: {'question':'...','options':['...','...']}. Keep it fun and family-friendly.",
    "pin_summary": "Summarize why this family chat message is important enough to pin. Return ONLY JSON: {'summary':'...'}",
    "general": "Analyze the sentiment and intent of this Arabic message. Return ONLY JSON: {'sentiment':'positive|neutral|negative','intent':'...','key_points':['...']}"
  };
  const sysPrompt = sysPromptMap[intent] || sysPromptMap["general"];
  try {
    const text = await callGroqText(text, sysPrompt, true);
    return jsonResponse(JSON.parse(text));
  } catch (err: any) {
    return errorResponse("text_analysis error: " + err.message, 500);
  }
}

async function handleFamilyAnalysis(payload: Record<string, unknown>) {
  const members = (payload.members as string) || "لا يوجد أعضاء";
  const tasks = (payload.tasks as string) || "لا توجد مهام";
  const goals = (payload.goals as string) || "لا توجد أهداف";
  const tasbiha = (payload.tasbiha as string) || "لا توجد";
  const transactions = (payload.transactions as string) || "لا توجد";

  const prompt = `أنت محلل عائلي ذكي. حلل بيانات العائلة التالية وقدم تقريراً مختصراً.

أفراد العائلة: ${members}
المهام: ${tasks}
الأهداف المالية: ${goals}
التسبيحة: ${tasbiha}
المعاملات: ${transactions}

أعد JSON فقط:
{
  "family_summary": "جملتين عن وضع العائلة",
  "member_highlights": [{"name":"...","achievement":"...","suggestion":"..."}],
  "family_health_score": <number 0-100>,
  "suggested_goal": "اقتراح هدف جديد للعائلة",
  "fun_fact": "حقيقة لطيفة عن العائلة"
}`;

  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse(JSON.parse(text));
  } catch (err: any) {
    return jsonResponse({
      family_summary: "زاد يحلل بيانات العائلة...",
      member_highlights: [],
      family_health_score: 50,
      suggested_goal: "حدد هدفاً عائلياً جديداً!",
      fun_fact: "العائلة المتماسكة تسبح معاً 🌸"
    });
  }
}

async function handleAutoSuggest(payload: Record<string, unknown>) {
  const context = (payload.context as string) || "عام";
  const inventory = (payload.inventory as string) || "";
  const transactions = (payload.transactions as string) || "";
  const patterns = (payload.patterns as string) || "";

  const prompt = `أنت مساعد منزلي ذكي. بناءً على السياق التالي، اقترح 3 إجراءات ذكية.

السياق: ${context}
المخزون: ${inventory}
المعاملات: ${transactions}
الأنماط: ${patterns}

أعد JSON فقط:
[{"action":"add_to_shopping|cook_meal|review_budget|check_subscription|family_challenge|save_money","title":"العنوان","description":"الوصف","priority":"high|medium|low","emoji":"🌳|💰|🛒|📋|🎯"}]`;

  try {
    const text = await callGroqText(prompt, undefined, true);
    const arr = extractArray(JSON.parse(text));
    return jsonResponse({ suggestions: arr.slice(0, 3) });
  } catch (err: any) {
    return jsonResponse({ suggestions: [] });
  }
}

async function handleFamilyGoalsSuggest(payload: Record<string, unknown>) {
  const members = (payload.members as string) || "";
  const totalBalance = (payload.total_balance as number) || 0;
  const completedTasks = (payload.completed_tasks as number) || 0;
  const tasbihaScore = (payload.tasbiha_score as number) || 0;

  const prompt = `أنت مستشار عائلي. بناءً على:
الأعضاء: ${members}
الرصيد الإجمالي: ${totalBalance} ريال
المهام المنجزة: ${completedTasks}
التسبيحة: ${tasbihaScore}

اقترح هدفاً عائلياً والحافز المناسب.
أعد JSON فقط:
{"goal_title":"عنوان الهدف","target_amount":<number>,"reward_suggestion":"وصف المكافأة","duration_days":<number>,"emoji":"🎯"}`;

  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse(JSON.parse(text));
  } catch (err: any) {
    return jsonResponse({
      goal_title: "تحدي العائلة",
      target_amount: 500,
      reward_suggestion: "رحلة عائلية",
      duration_days: 30,
      emoji: "🎯"
    });
  }
}

async function handleBehaviorLearning(payload: Record<string, unknown>) {
  const category = (payload.category as string) || "";
  const transactions = (payload.transactions as string) || "";
  const currentPatterns = (payload.current_patterns as string) || "";

  const prompt = `أنت خبير تحليل سلوكي. حلل نمط الإنفاق التالي واقترح تحسينات.

الفئة: ${category}
المعاملات: ${transactions}
الأنماط الحالية: ${currentPatterns}

أعد JSON فقط:
{
  "insight": "تحليل مختصر للنمط",
  "avg_spending": <number>,
  "trend": "increasing|stable|decreasing",
  "tip": "نصيحة لتحسين الإنفاق",
  "predicted_next": <number>,
  "confidence": <0-1>
}`;

  try {
    const text = await callGroqText(prompt, undefined, true);
    return jsonResponse(JSON.parse(text));
  } catch (err: any) {
    return jsonResponse({
      insight: "بيانات غير كافية للتحليل",
      avg_spending: 0,
      trend: "stable",
      tip: "تابع إنفاقك لتحصل على تحليل دقيق",
      predicted_next: 0,
      confidence: 0
    });
  }
}

async function handleVoiceTranscription(payload: Record<string, unknown>) {
  const audioBase64 = (payload.audio_base64 as string) || "";
  if (!audioBase64) return errorResponse("Missing audio_base64");
  try {
    const binary = atob(audioBase64);
    const array = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) array[i] = binary.charCodeAt(i);
    const blob = new Blob([array], { type: "audio/m4a" });
    
    const formData = new FormData();
    formData.append("file", blob, "audio.m4a");
    formData.append("model", "whisper-large-v3");
    
    const res = await fetch("https://api.groq.com/openai/v1/audio/transcriptions", {
      method: "POST",
      headers: { "Authorization": `Bearer ${GROQ_API_KEY}` },
      body: formData
    });
    
    const data = await res.json();
    if (!res.ok) throw new Error(data.error?.message || "Transcription failed");
    
    return jsonResponse({ text: data.text || "" });
  } catch (err: any) {
    return errorResponse("voice error: " + err.message, 500);
  }
}
