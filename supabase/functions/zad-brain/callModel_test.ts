import { assertEquals } from "https://deno.land/std@0.224.0/assert/mod.ts";
import { buildGeminiContents, summarizeQuota429, type Turn } from "./callModel.ts";

/**
 * الباج اللي الاختبارات دي بتحرسها (اتشخّصت 2026-08-14 من `zad_brain_runs.error` الحيّة):
 *
 *   gemini 400 — "Function call is missing a thought_signature in functionCall parts …
 *                 function call `default_api:add_pharmacy_item`, position 2"
 *
 * `thoughtSignature` بتقعد على الـ **Part**، جنب `functionCall`، مش جوّاه. الكود كان
 * بيكتبها جوّه `functionCall` وبيقراها من جوّه برضه — فجيميناي كان بيشوفها كحقل مش معروف
 * ويعتبر التوقيع مش مبعوت، وكل نداء تاني بعد أي استدعاء أداة كان بيرجع 400.
 *
 * الأثر الفعلي على البيانات الحيّة: ٢٢ من ٤١ تشغيلة كانت failed/queued، وتشغيلات كتير
 * الأداة فيها اتنفّذت (الصف موجود في `agent_actions`) والتشغيلة نفسها اتسجّلت "failed" —
 * لأن الأداة بتشتغل الأول، وبعدين النداء اللي المفروض يجيب الرد النهائي بيموت.
 */

Deno.test("thoughtSignature يترجّع على الـ Part نفسه مش جوّه functionCall", () => {
  const history: Turn[] = [
    { role: "user", text: "ضيف بنادول" },
    {
      role: "assistant",
      toolCalls: [{
        id: "gem_add_pharmacy_item_0",
        name: "add_pharmacy_item",
        input: { name: "بنادول" },
        thoughtSignature: "SIG_ABC",
      }],
    },
  ];

  const contents = buildGeminiContents(history);
  const modelTurn = contents[1];
  const part = modelTurn.parts[0];

  assertEquals(modelTurn.role, "model");
  assertEquals(part.thoughtSignature, "SIG_ABC");
  // الشرط اللي كان مكسور: التوقيع لازم يبقى مش موجود جوّه functionCall
  assertEquals(part.functionCall.thoughtSignature, undefined);
  assertEquals(part.functionCall.name, "add_pharmacy_item");
  assertEquals(part.functionCall.args, { name: "بنادول" });
});

Deno.test("استدعاء أداة من غير توقيع مابيحطّش الحقل أصلاً", () => {
  const contents = buildGeminiContents([
    { role: "assistant", toolCalls: [{ id: "x", name: "emit_insight", input: {} }] },
  ]);
  const part = contents[0].parts[0];

  assertEquals("thoughtSignature" in part, false);
  assertEquals(part.functionCall.name, "emit_insight");
});

Deno.test("كل استدعاء في نفس الدور بياخد توقيعه هو", () => {
  const contents = buildGeminiContents([
    {
      role: "assistant",
      text: "تمام",
      toolCalls: [
        { id: "a", name: "add_shopping_item", input: { item: "لبن" }, thoughtSignature: "SIG_1" },
        { id: "b", name: "add_shopping_item", input: { item: "عيش" }, thoughtSignature: "SIG_2" },
      ],
    },
  ]);
  const parts = contents[0].parts;

  assertEquals(parts[0].text, "تمام");
  assertEquals(parts[1].thoughtSignature, "SIG_1");
  assertEquals(parts[2].thoughtSignature, "SIG_2");
});

Deno.test("رد الأداة بيرجع بدور user وبالاسم مش بالـ id", () => {
  const contents = buildGeminiContents([
    { role: "tool", results: [{ id: "gem_x_0", name: "add_shopping_item", content: "اتضاف" }] },
  ]);

  assertEquals(contents[0].role, "user");
  assertEquals(contents[0].parts[0].functionResponse.name, "add_shopping_item");
  assertEquals(contents[0].parts[0].functionResponse.response, { result: "اتضاف" });
});

/**
 * الأجسام دي منقولة حرفيًا من `zad_brain_runs.error` الحيّة (2026-08-10). الغرض من
 * `summarizeQuota429` إنها تطلّع الحقيقتين اللي بيفرّقوا بين تفسيرين مختلفين تمامًا:
 * كوتة يوم خلصت، ولا رشقة ضربت حد الدقيقة.
 */
Deno.test("summarizeQuota429 يطلّع quotaId والحد وتأخير المحاولة من رد جوجل الحقيقي", () => {
  const body = JSON.stringify({
    error: {
      code: 429,
      message: "You exceeded your current quota…",
      status: "RESOURCE_EXHAUSTED",
      details: [
        { "@type": "type.googleapis.com/google.rpc.Help", links: [] },
        {
          "@type": "type.googleapis.com/google.rpc.QuotaFailure",
          violations: [{
            quotaMetric: "generativelanguage.googleapis.com/generate_content_free_tier_requests",
            quotaId: "GenerateRequestsPerDayPerProjectPerModel-FreeTier",
            quotaDimensions: { location: "global", model: "gemini-3.5-flash" },
            quotaValue: "20",
          }],
        },
        { "@type": "type.googleapis.com/google.rpc.RetryInfo", retryDelay: "51s" },
      ],
    },
  });

  assertEquals(summarizeQuota429(body), {
    quotaId: "GenerateRequestsPerDayPerProjectPerModel-FreeTier",
    quotaValue: "20",
    retryDelay: "51s",
  });
});

Deno.test("summarizeQuota429 مابيرميش لو الرد مش JSON أو ناقص", () => {
  assertEquals(summarizeQuota429("<html>502 Bad Gateway</html>").quotaId, "unparseable");
  assertEquals(summarizeQuota429(JSON.stringify({ error: { code: 429 } })), {
    quotaId: "unknown",
    quotaValue: "?",
    retryDelay: "?",
  });
});
