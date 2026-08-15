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
export type ToolCall = { id: string; name: string; input: any; thoughtSignature?: string };

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

// ── Model failover chain ───────────────────────────────────────────────────────
// The 429 body this project actually returns names its quota
// `GenerateRequestsPerDayPerProjectPerModel-FreeTier`, value 20. Read that id carefully:
// the bucket is per **model**, not per key. That is why rotating five keys bought nothing
// on 2026-08-15 (14 of 26 brain runs failed, every key reporting the same limit=20) — the
// pool was rotating inside one exhausted bucket. A second model is a second bucket, so
// model failover is the multiplier the key pool was never able to be.
//
// The chain below is not guesswork. Probed live against this project's own key 1 on
// 2026-08-15, every entry answering 200 **with functionDeclarations attached and actually
// emitting a functionCall** — a model that chats but won't call tools is useless to the
// agent loop and is not in this list:
//
//   gemini-3.5-flash-lite    200  tool ✅  thinking 0 tok    0.57s
//   gemini-3.1-flash-lite    200  tool ✅  thinking 0 tok    0.56s
//   gemini-flash-lite-latest 200  tool ✅  thinking 0 tok    0.57s
//   gemini-3-flash-preview   200  tool ✅  thinking 56 tok   1.05s
//   gemini-flash-latest      200  tool ✅  thinking 111 tok  1.29s
//   gemini-3.5-flash         200  tool ✅  thinking 231 tok  2.28s  ← finishReason MAX_TOKENS
//   gemini-3.6-flash         200  tool ✅  thinking 76 tok   7.28s
//   gemini-3.7-flash         503  — currently overloaded, deliberately excluded
//
// Retired on this project (all 404, verified the same day — do not "restore" them):
// gemini-1.5-flash, gemini-2.0-flash, gemini-2.0-flash-lite, gemini-2.5-flash,
// gemini-2.5-flash-lite.
const DEFAULT_MODEL_CHAIN = [
  "gemini-3.5-flash-lite",
  "gemini-3.1-flash-lite",
  "gemini-flash-lite-latest",
  "gemini-3-flash-preview",
  "gemini-flash-latest",
  "gemini-3.5-flash",
];

/** The caller's model first (it is whatever ZAD_MODEL_ROUTINE/BRAIN is set to, and the
 *  operator's choice outranks this file's), then the rest of the chain, deduped. */
function modelChain(primary: string): string[] {
  const extra = (Deno.env.get("ZAD_MODEL_FALLBACKS") ?? "")
    .split(",").map((s) => s.trim()).filter(Boolean);
  const chain = extra.length > 0 ? extra : DEFAULT_MODEL_CHAIN;
  return [...new Set([primary, ...chain])];
}

// Last leg of the chain. Groq is a different vendor with a different quota, so it survives
// a total Gemini outage. Probed the same day: llama-3.3-70b-versatile returns 200 and does
// emit tool_calls, so it can carry the agent loop rather than only plain text.
const GROQ_KEY_POOL: string[] = [1, 2]
  .map((n) => Deno.env.get(`GROQ_API_KEY_${n}`))
  .filter((k): k is string => !!k);
if (GROQ_KEY_POOL.length === 0) {
  const legacy = Deno.env.get("GROQ_API_KEY");
  if (legacy) GROQ_KEY_POOL.push(legacy);
}
const GROQ_MODEL = Deno.env.get("ZAD_GROQ_TEXT_MODEL") ?? "llama-3.3-70b-versatile";

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
  /** Thinking costs output budget, not a separate allowance: Gemini 2.5+ bills thought
   *  tokens against maxOutputTokens, so a thinking model can spend the whole budget and
   *  return a candidate with no text and no functionCall — a 200 that reads as a dead
   *  turn. Measured here on 2026-08-15: gemini-3.5-flash spent 231 thought tokens on
   *  "سجل 50 جنيه قهوة" and came back finishReason=MAX_TOKENS. Off unless a caller has a
   *  reason to want it. */
  thinking?: boolean;
}): Promise<ModelReply> {
  const { provider } = cfg();
  if (provider !== "gemini") {
    const send = provider === "openai_compatible" ? sendOpenAICompatible : sendAnthropic;
    return await withRetry(() => send(opts));
  }

  // Gemini: walk the model chain, then fall out to Groq. ProviderUnavailableError means
  // "this model cannot serve right now" (daily quota gone on every key, or the model is
  // overloaded) — retrying it with backoff just burns wall-clock inside an edge function
  // that has a request deadline, so it is not retried, it is stepped past immediately.
  const chain = modelChain(opts.model);
  const trail: string[] = [];
  for (const model of chain) {
    try {
      return await withRetry(() => sendGemini({ ...opts, model }));
    } catch (e) {
      if (e instanceof ConfigError) throw e;
      if (!(e instanceof ProviderUnavailableError)) throw e;
      trail.push(`${model}: ${e.short}`);
      console.warn(`[zad-brain] model ${model} unavailable (${e.short}); falling over`);
    }
  }

  if (GROQ_KEY_POOL.length > 0) {
    console.warn(`[zad-brain] whole Gemini chain unavailable [${trail.join(" | ")}]; falling back to Groq ${GROQ_MODEL}`);
    try {
      return await withRetry(() => sendGroq({ ...opts, model: GROQ_MODEL }));
    } catch (e) {
      throw new RetryableError(
        `every gemini model unavailable [${trail.join(" | ")}] and groq failed too: ${e instanceof Error ? e.message : String(e)}`,
      );
    }
  }
  throw new RetryableError(
    `every gemini model unavailable and no GROQ_API_KEY_1/GROQ_API_KEY to fall back to [${trail.join(" | ")}]`,
  );
}

// ============================================================
// إعادة المحاولة — مشتركة بين كل المزوّدين
// 400/401/403 أخطاء إعدادات، الإعادة بتخبّيها بس
// ============================================================
class ConfigError extends Error {}
class RetryableError extends Error {
  constructor(msg: string, public retryAfterMs?: number) { super(msg); }
}

/**
 * "This model can't serve, try a different one" — daily quota exhausted on every key, or
 * the model is overloaded (503 UNAVAILABLE). Distinct from RetryableError on purpose:
 * RetryableError means *the same call* is worth repeating after a pause, while this one
 * means repeating it is pointless and the chain should move on. `short` is the one-line
 * form that goes into the failover trail without dragging a whole JSON body along.
 */
class ProviderUnavailableError extends Error {
  constructor(msg: string, public short: string) { super(msg); }
}

async function withRetry<T>(fn: () => Promise<T>, attempt = 0): Promise<T> {
  try {
    return await fn();
  } catch (e) {
    if (e instanceof ConfigError) throw e;
    // Not retried here — callModel's chain walks to the next model instead.
    if (e instanceof ProviderUnavailableError) throw e;
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

/**
 * استخراج الحقائق اللي بتحدّد نوع الحد اللي اتضرب من جسم 429 بتاع جيميناي.
 *
 * الرسالة الخام بتتخزّن في `zad_brain_runs.error` كاملة، بس هي JSON طويل والمعلومة
 * الحاسمة مدفونة جوّاه — وده اللي خلّى سؤال "الخمس مفاتيح خلصوا كوتتهم إزاي؟" مفتوح.
 * `quotaId` بيقول الحد ده يومي (`...PerDay...`) ولا لحظي، و`retryDelay` بيأكد: تأخير
 * بالساعات = كوتة يوم خلصت، تأخير بالثواني = رشقة ضربت حد الدقيقة.
 */
export function summarizeQuota429(body: string): {
  quotaId: string;
  quotaValue: string;
  retryDelay: string;
} {
  try {
    const d = JSON.parse(body);
    const details: any[] = d?.error?.details ?? [];
    const violation = details
      .find((x) => String(x?.["@type"] ?? "").endsWith("QuotaFailure"))
      ?.violations?.[0];
    const retry = details.find((x) => String(x?.["@type"] ?? "").endsWith("RetryInfo"));
    return {
      quotaId: violation?.quotaId ?? "unknown",
      quotaValue: String(violation?.quotaValue ?? "?"),
      retryDelay: retry?.retryDelay ?? "?",
    };
  } catch {
    return { quotaId: "unparseable", quotaValue: "?", retryDelay: "?" };
  }
}

/**
 * تحويل الـ history لشكل `contents` بتاع جيميناي. متصدّرة عشان تتختبر لوحدها: الباج اللي
 * كانت هنا (مكان `thoughtSignature`) ما كانتش تتكشف بأي اختبار لأن الدالة كانت جوّه نداء شبكة.
 */
export function buildGeminiContents(history: Turn[]): any[] {
  const contents: any[] = [];
  for (const t of history) {
    if (t.role === "user") {
      contents.push({ role: "user", parts: [{ text: t.text }] });
    } else if (t.role === "assistant") {
      const parts: any[] = [];
      if (t.text) parts.push({ text: t.text });
      // `thoughtSignature` بتقعد على الـ **Part** نفسه، مش جوّه `functionCall`. كانت متحطة
      // جوّه، وجيميناي بيتجاهل الحقل المتداخل ده تمامًا ويعتبر إن التوقيع مش مبعوت — فكل
      // نداء تاني بعد استدعاء أداة كان بيرجع 400 (`missing a thought_signature ... position N`).
      // ده كان سبب ٢٢ من ٤١ تشغيلة فاشلة/معلّقة، ومعاه سبب إن الأداة تتنفّذ والتشغيلة
      // تتسجّل "failed": الأداة بتشتغل، وبعدين النداء اللي بيجيب الرد النهائي بيموت.
      for (const c of t.toolCalls ?? [])
        parts.push({
          functionCall: { name: c.name, args: c.input },
          ...(c.thoughtSignature ? { thoughtSignature: c.thoughtSignature } : {}),
        });
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
  return contents;
}

/**
 * Models observed to answer 400 INVALID_ARGUMENT when `thinkingConfig` is present at all.
 * Learned at runtime rather than hardcoded, because the list is not guessable from the
 * name: measured 2026-08-15, gemini-3.5-flash-lite and gemini-flash-lite-latest reject the
 * field, while gemini-3.1-flash-lite accepts it — same "-lite" suffix, opposite behaviour.
 * They are all already 0-thought-token models, so dropping the field costs nothing.
 */
const THINKING_CONFIG_UNSUPPORTED = new Set<string>();

async function sendGemini(o: {
  model: string; system: string; tools: ToolDef[]; history: Turn[]; maxTokens?: number;
  thinking?: boolean;
}, retriedWithoutThinkingConfig = false): Promise<ModelReply> {
  const contents = buildGeminiContents(o.history);
  const sendThinkingConfig = !o.thinking &&
    !retriedWithoutThinkingConfig &&
    !THINKING_CONFIG_UNSUPPORTED.has(o.model);

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
    generationConfig: {
      maxOutputTokens: o.maxTokens ?? 1500,
      temperature: 0.4,
      // See callModel's `thinking` docblock: thought tokens are billed against
      // maxOutputTokens, so leaving this unset let the model think itself out of an
      // answer. The lite models in the chain report 0 thought tokens either way; this is
      // what protects the thinking-capable ones when the chain falls through to them.
      ...(sendThinkingConfig ? { thinkingConfig: { thinkingBudget: 0 } } : {}),
    },
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
  const quotaTrail: string[] = [];
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
      const q = summarizeQuota429(lastQuotaBody);
      quotaTrail.push(`key${keyIndex + 1}:${q.quotaId}=${q.quotaValue}/retry=${q.retryDelay}`);
      console.warn(
        `[zad-brain] Gemini key ${keyIndex + 1}/${GEMINI_KEY_POOL.length} hit 429 — ` +
          `quotaId=${q.quotaId} limit=${q.quotaValue} retryDelay=${q.retryDelay}; switching to next key`,
      );
      continue;
    }
    // 503 UNAVAILABLE is the model being overloaded, not this key being throttled — every
    // other key would hit the identical wall, so rotating them wastes the round trips.
    // Hand it to the model chain instead. This is the error that produced the customer-
    // visible "gemini 503 … high demand" runs on 2026-08-15.
    if (attempt.status === 503) {
      const overloadBody = await attempt.text();
      throw new ProviderUnavailableError(
        `gemini 503 on ${o.model}: ${overloadBody}`,
        `503 overloaded`,
      );
    }
    // A model that rejects thinkingConfig outright answers 400 INVALID_ARGUMENT before it
    // looks at anything else. checkResponse would class that as a ConfigError and kill the
    // whole run — which is exactly what the first deploy of this change did. Retry once
    // without the field and remember, so the cost is one wasted call per model per
    // instance, not a dead brain.
    if (attempt.status === 400 && sendThinkingConfig) {
      THINKING_CONFIG_UNSUPPORTED.add(o.model);
      console.warn(`[zad-brain] ${o.model} rejects thinkingConfig; retrying without it`);
      return await sendGemini(o, true);
    }
    res = attempt;
    break;
  }

  if (!res) {
    // Every key is rate-limited on this model. The quota id in the body says whether that
    // is the per-day bucket or a burst limit — either way another *key* cannot help
    // (they share the per-project-per-model bucket), so this goes to the model chain.
    // الأثر بيتحط قبل جسم الرد عشان يفضل ظاهر في `zad_brain_runs.error` حتى لو الرسالة
    // اتقصّت. هو اللي بيفرّق بين "الكوتة اليومية خلصت" و"حد الدقيقة اتضرب برشقة": حد
    // يومي متضروب بيرجع retryDelay بالساعات، وحد الدقيقة بيرجعه بالثواني.
    throw new ProviderUnavailableError(
      `gemini 429 on ${o.model} (all ${GEMINI_KEY_POOL.length} keys exhausted) [${quotaTrail.join(" | ")}]: ${lastQuotaBody}`,
      `429 all ${GEMINI_KEY_POOL.length} keys`,
    );
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
        // 2.5+: التوقيع بيجي على الـ Part (`p.thoughtSignature`)، مش جوّه `p.functionCall`.
        // كان بيتقرا من المكان المتداخل الغلط فبيرجع undefined دايمًا. بنقرا الاتنين
        // احتياطًا لأي اختلاف في شكل الرد بين إصدارات الموديل.
        thoughtSignature: p.thoughtSignature ?? p.functionCall.thoughtSignature,
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
/**
 * The Groq leg of the failover chain. Groq speaks the same OpenAI-compatible dialect, so
 * this is [sendOpenAICompatible] pointed at Groq's endpoint with its own key pool rather
 * than a second copy of the message-shaping code — the two must not be allowed to drift,
 * since a chain that only gets exercised during a Gemini outage is exactly the code that
 * nobody notices has rotted.
 *
 * Note it reads GROQ_API_KEY_1/2 (falling back to the singular GROQ_API_KEY), NOT
 * ZAD_API_KEY: `cfg().key` is `Deno.env.get("ZAD_API_KEY")!` and that secret is not set on
 * this project at all, so routing Groq through cfg() would have failed with an undefined
 * bearer token the first time it was ever needed.
 */
function sendGroq(o: {
  model: string; system: string; tools: ToolDef[]; history: Turn[]; maxTokens?: number;
}): Promise<ModelReply> {
  const key = GROQ_KEY_POOL[groqKeyCursor % GROQ_KEY_POOL.length];
  groqKeyCursor = (groqKeyCursor + 1) % GROQ_KEY_POOL.length;
  return sendOpenAICompatible(o, { key, baseUrl: "https://api.groq.com/openai/v1", who: "groq" });
}
let groqKeyCursor = 0;

async function sendOpenAICompatible(o: {
  model: string; system: string; tools: ToolDef[]; history: Turn[]; maxTokens?: number;
}, override?: { key: string; baseUrl: string; who: string }): Promise<ModelReply> {
  const { key, baseUrl } = override ?? cfg();
  const who = override?.who ?? "openai_compatible";
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
  await checkResponse(res, who);
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
