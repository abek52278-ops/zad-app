// Failover chain tests. The whole point of this chain is that it only ever runs while
// something is already broken, so it is the code least likely to be exercised by hand and
// the most likely to rot silently — it gets tests, not a comment promising it works.
//
// Env has to be set before the module is imported: GEMINI_KEY_POOL / GROQ_KEY_POOL are
// module-level consts, so a dynamic import after Deno.env.set is the only way to observe
// a configured pool.
import { assert, assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";

Deno.env.set("ZAD_PROVIDER", "gemini");
Deno.env.set("ZAD_API_KEY_1", "k1");
Deno.env.set("ZAD_API_KEY_2", "k2");
Deno.env.set("GROQ_API_KEY_1", "groq1");
Deno.env.set("ZAD_MODEL_FALLBACKS", "model-b,model-c");

const { callModel } = await import("./callModel.ts");

const BASE = {
  model: "model-a",
  system: "s",
  tools: [{ name: "t", description: "d", input_schema: { type: "object", properties: {} } }],
  history: [{ role: "user" as const, text: "hi" }],
};

/** Records every outbound call so a test can assert on the order models were tried in. */
function stubFetch(handler: (url: string, body: any) => Response) {
  const calls: Array<{ url: string; body: any }> = [];
  const original = globalThis.fetch;
  globalThis.fetch = ((input: string | URL | Request, init?: RequestInit) => {
    const url = String(input);
    const body = init?.body ? JSON.parse(String(init.body)) : null;
    calls.push({ url, body });
    return Promise.resolve(handler(url, body));
  }) as typeof fetch;
  return { calls, restore: () => { globalThis.fetch = original; } };
}

const quota429 = () =>
  new Response(
    JSON.stringify({
      error: {
        code: 429,
        details: [{
          "@type": "type.googleapis.com/google.rpc.QuotaFailure",
          violations: [{ quotaId: "GenerateRequestsPerDayPerProjectPerModel-FreeTier", quotaValue: "20" }],
        }],
      },
    }),
    { status: 429 },
  );

const overloaded503 = () =>
  new Response(JSON.stringify({ error: { code: 503, status: "UNAVAILABLE" } }), { status: 503 });

const geminiOk = () =>
  new Response(
    JSON.stringify({
      candidates: [{ content: { parts: [{ text: "تمام" }] } }],
      usageMetadata: { promptTokenCount: 5, candidatesTokenCount: 2 },
    }),
    { status: 200 },
  );

function modelOf(url: string): string {
  return url.match(/models\/([^:]+):/)?.[1] ?? "";
}

Deno.test("429 على كل المفاتيح بينتقل للموديل اللي بعده مش بيفشل", async () => {
  const s = stubFetch((url) => (modelOf(url) === "model-a" ? quota429() : geminiOk()));
  try {
    const reply = await callModel({ ...BASE });
    assertEquals(reply.text, "تمام");
    // كل مفاتيح model-a اتجرّبت الأول، وبعدين model-b نجح من أول مفتاح
    const tried = s.calls.map((c) => modelOf(c.url));
    assertEquals(tried.filter((m) => m === "model-a").length, 2, "لازم يدوّر على المفتاحين");
    assertEquals(tried[tried.length - 1], "model-b");
  } finally {
    s.restore();
  }
});

Deno.test("503 بينتقل للموديل التالي من غير ما يحرق باقي المفاتيح", async () => {
  const s = stubFetch((url) => (modelOf(url) === "model-a" ? overloaded503() : geminiOk()));
  try {
    const reply = await callModel({ ...BASE });
    assertEquals(reply.text, "تمام");
    const tried = s.calls.map((c) => modelOf(c.url));
    // مفتاح واحد بس على model-a: الـ 503 حِمل على الموديل، مش throttling على المفتاح
    assertEquals(tried.filter((m) => m === "model-a").length, 1);
    assertEquals(tried[tried.length - 1], "model-b");
  } finally {
    s.restore();
  }
});

Deno.test("سلسلة جيميناي كلها مقفولة → بيقع على Groq", async () => {
  const s = stubFetch((url) => {
    if (url.includes("api.groq.com")) {
      return new Response(
        JSON.stringify({
          choices: [{ message: { content: "من جروك", tool_calls: [] } }],
          usage: { prompt_tokens: 3, completion_tokens: 1 },
        }),
        { status: 200 },
      );
    }
    return quota429();
  });
  try {
    const reply = await callModel({ ...BASE });
    assertEquals(reply.text, "من جروك");
    const groqCall = s.calls.find((c) => c.url.includes("api.groq.com"));
    assert(groqCall, "المفروض ينادي جروك");
    assertEquals(groqCall!.body.model, "openai/gpt-oss-120b");
    // الأدوات لازم توصل جروك كمان، وإلا الـ agent loop بيرجع نص بس ومبينفّذش حاجة
    assertEquals(groqCall!.body.tools.length, 1);
  } finally {
    s.restore();
  }
});

Deno.test("التفكير مقفول افتراضياً ومفتوح لما يتطلب صراحة", async () => {
  const s = stubFetch(() => geminiOk());
  try {
    await callModel({ ...BASE });
    assertEquals(s.calls[0].body.generationConfig.thinkingConfig, { thinkingBudget: 0 });
    await callModel({ ...BASE, thinking: true });
    assertEquals(s.calls[1].body.generationConfig.thinkingConfig, undefined);
  } finally {
    s.restore();
  }
});

Deno.test("400 إعدادات غلط بيفضل يفشل فوراً — مابيتخبّاش وراء السلسلة", async () => {
  const s = stubFetch(() => new Response("bad request", { status: 400 }));
  try {
    let threw = false;
    try {
      await callModel({ ...BASE });
    } catch (e) {
      threw = true;
      assert(String(e).includes("400"), `المفروض يفضح الـ 400، جه: ${e}`);
    }
    assert(threw, "400 المفروض يرمي مش يكمّل السلسلة");
    // محاولتين بالظبط على model-a: الأولى بـ thinkingConfig، والتانية من غيره بعد ما
    // الـ 400 خلّى الموديل يتعلّم إنه مش بيقبل الحقل. وبعدين بيرمي — مفيش انتقال
    // لموديل تاني، لأن خطأ إعدادات بيتصلح مش بيتلف حواليه.
    assertEquals(s.calls.map((c) => modelOf(c.url)), ["model-a", "model-a"]);
    assertEquals(s.calls[0].body.generationConfig.thinkingConfig, { thinkingBudget: 0 });
    assertEquals(s.calls[1].body.generationConfig.thinkingConfig, undefined);
  } finally {
    s.restore();
  }
});

Deno.test("موديل بيرفض thinkingConfig بيتعاد عليه من غيره وينجح", async () => {
  // بالظبط سلوك gemini-3.5-flash-lite: 400 لما thinkingConfig موجود، 200 من غيره.
  const s = stubFetch((_url, body) =>
    body?.generationConfig?.thinkingConfig ? new Response("invalid argument", { status: 400 }) : geminiOk()
  );
  try {
    const reply = await callModel({ ...BASE, model: "picky-model" });
    assertEquals(reply.text, "تمام");
    assertEquals(s.calls.length, 2, "محاولة بالحقل ومحاولة من غيره");

    // والمرة الجاية على نفس الموديل بيبعت من غير الحقل من أول مرة — الدرس اتحفظ.
    const before = s.calls.length;
    await callModel({ ...BASE, model: "picky-model" });
    assertEquals(s.calls.length - before, 1, "مفيش هدر محاولة تانية بعد ما اتعلّم");
    assertEquals(s.calls[before].body.generationConfig.thinkingConfig, undefined);
  } finally {
    s.restore();
  }
});
