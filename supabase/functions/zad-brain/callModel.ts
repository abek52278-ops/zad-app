// ============================================================
// ZAD — callModel(): provider-agnostic model adapter
//
// المسار:  supabase/functions/zad-brain/callModel.ts
//
// الغرض: المزوّد يبقى قيمة في الإعدادات، مش مكتوب في الكود.
// الـ SYSTEM والأدوات وطبقة التحقق وحلقة think() مايتغيروش حرف.
//
// الإعدادات (supabase secrets set):
//   ZAD_PROVIDER=gemini | anthropic | openai_compatible
//   ZAD_MODEL_ROUTINE=gemini-2.5-flash
//   ZAD_MODEL_CHAT=gemini-2.5-pro
//   ZAD_API_KEY=...
//   ZAD_BASE_URL=...            (لـ openai_compatible بس: OpenRouter / Groq)
//
// ⚠️ الكود ده مكتوب من مواصفات الـ APIs ومجرّبش على نداء حي.
//    أول حاجة تعملها: نادِ كل مزوّد مرة واحدة وأكّد إن نداء الأدوات بيرجع
//    صح. صيغة مخططات الأدوات في جيميناي بالتحديد هي أكتر حاجة معرضة للتغيير.
// ============================================================

// ------------------------------------------------------------
// شكل محايد للمحادثة — مش بنخزن بلوكات خاصة بمزوّد معين.
// كل محوّل بيترجم الشكل ده لصيغته في كل نداء، فتبديل المزوّد
// وسط محادثة يبقى ممكن.
// ------------------------------------------------------------
export type ToolCall = { id: string; name: string; input: any };

export type Turn =
  | { role: "user"; text: string }
  | { role: "assistant"; text?: string; toolCalls?: ToolCall[] }
  | { role: "tool"; results: Array<{ id: string; name: string; content: string }> };

export type ToolDef = {
  name: string;
  description: string;
  input_schema: any;      // JSON Schema — نفس الشكل الموجود في TOOLS
};

export type ModelReply = {
  text: string;
  toolCalls: ToolCall[];
  usage: { inTok: number; outTok: number };
};

export type Provider = "anthropic" | "gemini" | "openai_compatible";

// Same ZAD_API_KEY_1..5 pool zad-core-intelligence reads — shared deliberately, not a
// naming collision. Only consulted for provider "gemini"; anthropic/openai_compatible keep
// using the single ZAD_API_KEY exactly as before (separate auth mechanisms, and a pool was
// never asked for on them).
//
// Falls back to the legacy singular ZAD_API_KEY when none of the five are set, so a
// half-migrated project doesn't lose Gemini access outright.
const GEMINI_KEY_POOL: string[] = [1, 2, 3, 4, 5]
  .map((n) => Deno.env.get(`ZAD_API_KEY_${n}`))
  .filter((k): k is string => !!k);
if (GEMINI_KEY_POOL.length === 0) {
  const legacy = Deno.env.get("ZAD_API_KEY");
  if (legacy) GEMINI_KEY_POOL.push(legacy);
}

// Round-robin starting point across warm invocations, so consecutive requests don't all
// hammer key 1 first. sendGemini() walks the whole pool from here on a 429.
let geminiKeyCursor = 0;
function nextGeminiKeyIndex(): number {
  const i = geminiKeyCursor % Math.max(GEMINI_KEY_POOL.length, 1);
  geminiKeyCursor = (geminiKeyCursor + 1) % Math.max(GEMINI_KEY_POOL.length, 1);
  return i;
}

const cfg = () => {
  const provider = (Deno.env.get("ZAD_PROVIDER") ?? "anthropic") as Provider;
  return {
    provider,
    // Gemini's key is chosen per attempt inside sendGemini, not here — this stays for the
    // other two providers.
    key: Deno.env.get("ZAD_API_KEY")!,
    baseUrl: Deno.env.get("ZAD_BASE_URL") ?? "",
  };
};

// ============================================================
// المدخل الموحد
// ============================================================
export async function callModel(opts: {
  model: string;
  system: string;
  tools: ToolDef[];
  history: Turn[];
  maxTokens?: number;
}): Promise<ModelReply> {
  const { provider } = cfg();
  const send =
    provider === "gemini" ? sendGemini :
    provider === "openai_compatible" ? sendOpenAICompatible :
    sendAnthropic;

  return await withRetry(() => send(opts));
}

// ============================================================
// إعادة المحاولة — مشتركة بين كل المزوّدين
// 400/401/403 أخطاء إعدادات، الإعادة بتخبّيها بس
// ============================================================
class ConfigError extends Error {}
class RetryableError extends Error {
  constructor(msg: string, public retryAfterMs?: number) { super(msg); }
}

async function withRetry<T>(fn: () => Promise<T>, attempt = 0): Promise<T> {
  try {
    return await fn();
  } catch (e) {
    if (e instanceof ConfigError) throw e;
    if (attempt >= 2) throw e;

    const base = e instanceof RetryableError && e.retryAfterMs
      ? e.retryAfterMs
      : 1000 * Math.pow(4, attempt);
    // jitter: لو كرون بينادي لكل العملاء في نفس اللحظة، ماينفعش
    // كلهم يعيدوا المحاولة في نفس اللحظة كمان
    await new Promise((r) => setTimeout(r, base + Math.random() * 500));
    return await withRetry(fn, attempt + 1);
  }
}

async function checkResponse(res: Response, who: string) {
  if (res.ok) return;
  const body = await res.text();
  if (res.status === 400 || res.status === 401 || res.status === 403) {
    throw new ConfigError(`${who} ${res.status}: ${body}`);
  }
  const ra = Number(res.headers.get("retry-after"));
  throw new RetryableError(`${who} ${res.status}: ${body}`,
                           isFinite(ra) && ra > 0 ? ra * 1000 : undefined);
}

// ============================================================
// 1) Anthropic
// ============================================================
async function sendAnthropic(o: {
  model: string; system: string; tools: ToolDef[]; history: Turn[]; maxTokens?: number;
}): Promise<ModelReply> {
  const { key } = cfg();

  const messages = o.history.map((t) => {
    if (t.role === "user") return { role: "user", content: t.text };
    if (t.role === "assistant") {
      const content: any[] = [];
      if (t.text) content.push({ type: "text", text: t.text });
      for (const c of t.toolCalls ?? [])
        content.push({ type: "tool_use", id: c.id, name: c.name, input: c.input });
      return { role: "assistant", content };
    }
    return {
      role: "user",
      content: t.results.map((r) => ({
        type: "tool_result", tool_use_id: r.id, content: r.content,
      })),
    };
  });

  const res = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": key,
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify({
      model: o.model,
      max_tokens: o.maxTokens ?? 1500,
      system: o.system,
      tools: o.tools.map((t) => ({
        name: t.name, description: t.description, input_schema: t.input_schema,
      })),
      messages,
    }),
  });
  await checkResponse(res, "anthropic");
  const d = await res.json();

  return {
    text: d.content.filter((b: any) => b.type === "text").map((b: any) => b.text).join(""),
    toolCalls: d.content.filter((b: any) => b.type === "tool_use")
      .map((b: any) => ({ id: b.id, name: b.name, input: b.input })),
    usage: { inTok: d.usage?.input_tokens ?? 0, outTok: d.usage?.output_tokens ?? 0 },
  };
}

// ============================================================
// 2) Gemini
//
// فروق لازم تتعامل معاها:
//   • systemInstruction منفصل عن contents
//   • الأدوار "user" و "model" (مش assistant)
//   • الأدوات جوه tools[0].functionDeclarations
//   • نداء الأداة عندهم مالوش id — بنولّد ids من الاسم والترتيب
//   • المخطط لازم يبقى مجموعة فرعية من OpenAPI: بنشيل المفاتيح المش مدعومة
// ============================================================

/** جيميناي بيرفض مفاتيح JSON Schema اللي مش في مواصفته. */
function sanitizeSchema(s: any): any {
  if (!s || typeof s !== "object") return s;
  if (Array.isArray(s)) return s.map(sanitizeSchema);

  const allowed = ["type", "description", "enum", "properties", "required",
                   "items", "nullable", "format"];
  const out: any = {};
  for (const [k, v] of Object.entries(s)) {
    if (!allowed.includes(k)) continue;                 // additionalProperties, $schema, ...
    out[k] = (k === "properties")
      ? Object.fromEntries(Object.entries(v as any).map(([pk, pv]) => [pk, sanitizeSchema(pv)]))
      : (k === "items" ? sanitizeSchema(v) : v);
  }
  return out;
}

async function sendGemini(o: {
  model: string; system: string; tools: ToolDef[]; history: Turn[]; maxTokens?: number;
}): Promise<ModelReply> {
  const contents: any[] = [];
  for (const t of o.history) {
    if (t.role === "user") {
      contents.push({ role: "user", parts: [{ text: t.text }] });
    } else if (t.role === "assistant") {
      const parts: any[] = [];
      if (t.text) parts.push({ text: t.text });
      for (const c of t.toolCalls ?? [])
        parts.push({ functionCall: { name: c.name, args: c.input } });
      contents.push({ role: "model", parts });
    } else {
      // ردود الأدوات بترجع بدور "user" في جيميناي، والربط بالاسم مش بـ id
      contents.push({
        role: "user",
        parts: t.results.map((r) => ({
          functionResponse: { name: r.name, response: { result: r.content } },
        })),
      });
    }
  }

  const body = JSON.stringify({
    systemInstruction: { parts: [{ text: o.system }] },
    contents,
    tools: [{
      functionDeclarations: o.tools.map((t) => ({
        name: t.name,
        description: t.description,
        parameters: sanitizeSchema(t.input_schema),
      })),
    }],
    generationConfig: { maxOutputTokens: o.maxTokens ?? 1500, temperature: 0.4 },
  });

  // Same contract as zad-core-intelligence's callGeminiPool: one request exhausts the whole
  // key pool before reporting failure. A 429 on key N retries the SAME request on key N+1
  // immediately — quota failover inside a single call, which is a different thing from
  // withRetry()'s backoff (that exists for transient upstream faults and still wraps this).
  //
  // Non-429 failures are NOT rotated past: checkResponse classifies 400/401/403 as
  // ConfigError, and retrying a malformed request or a bad-auth response on four more keys
  // just burns them and buries the real error.
  if (GEMINI_KEY_POOL.length === 0) {
    throw new ConfigError("gemini: no key configured (ZAD_API_KEY_1..5 / ZAD_API_KEY all unset)");
  }

  let res: Response | null = null;
  let lastQuotaBody = "";
  const start = nextGeminiKeyIndex();
  for (let i = 0; i < GEMINI_KEY_POOL.length; i++) {
    const keyIndex = (start + i) % GEMINI_KEY_POOL.length;
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${o.model}` +
      `:generateContent?key=${encodeURIComponent(GEMINI_KEY_POOL[keyIndex])}`;
    const attempt = await fetch(url, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body,
    });
    if (attempt.status === 429) {
      lastQuotaBody = await attempt.text();
      console.warn(`[zad-brain] Gemini key ${keyIndex + 1} hit 429/quota, switching to next key...`);
      continue;
    }
    res = attempt;
    break;
  }

  if (!res) {
    // Every key is rate-limited. Surface it as retryable so withRetry's backoff gets a shot
    // at a window where quota has recovered, rather than failing the whole brain run.
    throw new RetryableError(`gemini 429 (all ${GEMINI_KEY_POOL.length} keys exhausted): ${lastQuotaBody}`);
  }

  await checkResponse(res, "gemini");
  const d = await res.json();

  const parts = d.candidates?.[0]?.content?.parts ?? [];
  const calls: ToolCall[] = [];
  let text = "";
  parts.forEach((p: any, i: number) => {
    if (p.text) text += p.text;
    if (p.functionCall) {
      calls.push({
        // جيميناي مش بيرجع id — بنولّده عشان باقي الكود يشتغل بنفس الشكل
        id: `gem_${p.functionCall.name}_${i}`,
        name: p.functionCall.name,
        input: p.functionCall.args ?? {},
      });
    }
  });

  return {
    text,
    toolCalls: calls,
    usage: {
      inTok: d.usageMetadata?.promptTokenCount ?? 0,
      outTok: d.usageMetadata?.candidatesTokenCount ?? 0,
    },
  };
}

// ============================================================
// 3) OpenAI-compatible — OpenRouter و Groq وأغلب الباقي
//
// أهم فرق: arguments بترجع كـ **نص JSON** مش كائن، وساعات بتكون
// مكسورة. مابنرميش خطأ — بنرجّع السبب للموديل زي أي رفض تحقق تاني.
// ============================================================
async function sendOpenAICompatible(o: {
  model: string; system: string; tools: ToolDef[]; history: Turn[]; maxTokens?: number;
}): Promise<ModelReply> {
  const { key, baseUrl } = cfg();
  if (!baseUrl) throw new ConfigError("ZAD_BASE_URL مطلوب لـ openai_compatible");

  const messages: any[] = [{ role: "system", content: o.system }];
  for (const t of o.history) {
    if (t.role === "user") {
      messages.push({ role: "user", content: t.text });
    } else if (t.role === "assistant") {
      messages.push({
        role: "assistant",
        content: t.text ?? null,
        tool_calls: (t.toolCalls ?? []).map((c) => ({
          id: c.id, type: "function",
          function: { name: c.name, arguments: JSON.stringify(c.input) },
        })),
      });
    } else {
      for (const r of t.results)
        messages.push({ role: "tool", tool_call_id: r.id, content: r.content });
    }
  }

  const res = await fetch(`${baseUrl.replace(/\/$/, "")}/chat/completions`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "authorization": `Bearer ${key}`,
    },
    body: JSON.stringify({
      model: o.model,
      max_tokens: o.maxTokens ?? 1500,
      temperature: 0.4,
      tools: o.tools.map((t) => ({
        type: "function",
        function: {
          name: t.name, description: t.description, parameters: t.input_schema,
        },
      })),
      messages,
    }),
  });
  await checkResponse(res, "openai_compatible");
  const d = await res.json();

  const msg = d.choices?.[0]?.message ?? {};
  const calls: ToolCall[] = [];
  for (const c of msg.tool_calls ?? []) {
    let input: any;
    try {
      input = JSON.parse(c.function.arguments || "{}");
    } catch {
      // JSON مكسور — نمرّره كعلامة، وطبقة التحقق ترفضه وترجّع السبب
      // للموديل عشان يعيد الأداة صح. أنضف من إن الجلسة كلها تقع.
      input = { __malformed: true, __raw: c.function.arguments };
    }
    calls.push({ id: c.id, name: c.function.name, input });
  }

  return {
    text: msg.content ?? "",
    toolCalls: calls,
    usage: {
      inTok: d.usage?.prompt_tokens ?? 0,
      outTok: d.usage?.completion_tokens ?? 0,
    },
  };
}

// ============================================================
// اختبار سريع لكل مزوّد قبل أي حاجة تانية.
// نادِ الدالة دي مرة يدوي وأكّد إن toolCalls فيها عنصر واحد.
// لو رجعت نص بدل نداء أداة، يبقى مخطط الأدوات مش متقبّل — إصلح ده
// قبل ما تبني حاجة فوقه.
// ============================================================
export async function smokeTestTools(model: string) {
  const reply = await callModel({
    model,
    system: "إنت بتجرب نداء الأدوات. استخدم الأداة المتاحة فوراً.",
    tools: [{
      name: "ping",
      description: "بيرجع رسالة تجريبية",
      input_schema: {
        type: "object",
        properties: { message: { type: "string", description: "أي نص" } },
        required: ["message"],
      },
    }],
    history: [{ role: "user", text: "نادِ أداة ping بالرسالة: تمام" }],
    maxTokens: 200,
  });

  return {
    ok: reply.toolCalls.length === 1 && reply.toolCalls[0].name === "ping",
    toolCalls: reply.toolCalls,
    text: reply.text,
    usage: reply.usage,
  };
}
