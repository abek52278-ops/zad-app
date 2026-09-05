// deno-lint-ignore-file

const GROQ_API_KEY = Deno.env.get("GROQ_API_KEY");
const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const ASSOCIATE_TAG = Deno.env.get("AMAZON_ASSOCIATE_TAG") || "zad0b-21";

interface MatchRequest {
  product_name: string;
  catalog: Array<{ id: string; name: string; keywords: string[] }>;
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

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders() });
  if (req.method !== "POST") return jsonResponse({ error: "Method not allowed" }, 405);

  try {
    const { action, payload } = await req.json();
    console.log(`[AmazonCreators] action=${action}`);

    switch (action) {
      case "match_product": {
        const { product_name, catalog } = payload as MatchRequest;
        if (!product_name || !catalog?.length) {
          return jsonResponse({ match: null, error: "Missing product_name or catalog" });
        }

        // Use Groq for smart matching (not exact text match)
        if (GROQ_API_KEY) {
          const systemPrompt = `أنت خبير في مطابقة منتجات البقالة باللغة العربية. 
مهمتك: لك اسم منتج من مستخدم (مثلاً "زيت" أو "حليب" أو "رز")، وعندك كتالوج منتجات 
كل منتج له: id و name و keywords.

أرجع id المنتج الأكثر تطابقاً من الكتالوج. 
قواعد المطابقة:
- "زيت" يطابق "زيت زيتون عافية 1.5 لتر" ← صحيح
- "حليب" يطابق "حليب المراعي طويل الأجل 1 لتر" ← صحيح
- إذا ما في تطابق واضح، أرجع null
- أجب بصيغة JSON فقط: {"matched_id": "..."} أو {"matched_id": null}`;

          const userPrompt = `ابحث عن تطابق لـ: "${product_name}" في هذا الكتالوج:\n${JSON.stringify(catalog)}`;

          const groqResp = await fetch(GROQ_URL, {
            method: "POST",
            headers: { "Authorization": `Bearer ${GROQ_API_KEY}`, "Content-Type": "application/json" },
            body: JSON.stringify({
              // 2026-08-31: llama-3.3-70b-versatile 404s (Groq dropped all Llama chat models).
              model: "openai/gpt-oss-120b",
              messages: [
                { role: "system", content: systemPrompt },
                { role: "user", content: userPrompt }
              ],
              response_format: { type: "json_object" },
              temperature: 0.1,
              max_tokens: 100,
            }),
          });

          // Every failure below falls through to the keyword matcher instead of
          // returning. Before this, the branch returned unconditionally, so the
          // keyword fallback under it was unreachable code — and because a failed
          // Groq call still parsed to `{}`, the customer saw `{match: null}`,
          // which is indistinguishable from "no such product". That is exactly how
          // the llama-3.3-70b-versatile 404 (Groq dropped every Llama chat model on
          // 2026-08-31) stayed invisible: the deployed copy of this function was
          // outside CI, so the model fix could not ship, and the symptom was silence.
          if (!groqResp.ok) {
            console.error(`[AmazonCreators] match_product: Groq HTTP ${groqResp.status} — falling back to keywords`);
          } else {
            try {
              const groqData = await groqResp.json();
              const raw = groqData.choices?.[0]?.message?.content;
              if (raw) {
                const matchedId = JSON.parse(raw).matched_id;
                // A deliberate null from the model means "no match" and is a real
                // answer, so return it. Only a missing/!ok/unparseable reply falls through.
                if (matchedId !== undefined) return jsonResponse({ match: matchedId || null });
              }
              console.error("[AmazonCreators] match_product: Groq reply had no usable content — falling back to keywords");
            } catch (e) {
              const msg = String((e as { message?: string })?.message ?? e);
              console.error(`[AmazonCreators] match_product: could not parse Groq reply (${msg}) — falling back to keywords`);
            }
          }
        }

        // Keyword matching — the fallback when there is no key, or the model call
        // failed. Reachable now.
        const query = product_name.toLowerCase();
        for (const item of catalog) {
          if (item.name.toLowerCase().includes(query)) return jsonResponse({ match: item.id });
          for (const kw of item.keywords ?? []) {
            if (query.includes(kw.toLowerCase()) || kw.toLowerCase().includes(query)) {
              return jsonResponse({ match: item.id });
            }
          }
        }
        return jsonResponse({ match: null });
      }

      case "build_affiliate_link": {
        const { asin } = payload;
        if (!asin) return jsonResponse({ error: "Missing asin" });
        return jsonResponse({ link: `https://www.amazon.sa/dp/${asin}?tag=${ASSOCIATE_TAG}` });
      }

      case "record_click": {
        const { product_id, user_id, source_screen } = payload;
        if (!product_id) return jsonResponse({ error: "Missing product_id" });

        const { createClient } = await import("https://esm.sh/@supabase/supabase-js@2");
        const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
        const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const supabase = createClient(supabaseUrl, supabaseKey);

        await supabase.from("affiliate_clicks").insert({
          product_id,
          user_id,
          source_screen: source_screen || "shopping",
        });
        return jsonResponse({ success: true });
      }

      case "record_catalog_request": {
        const { searched_term, user_id } = payload;
        if (!searched_term) return jsonResponse({ error: "Missing searched_term" });

        const { createClient } = await import("https://esm.sh/@supabase/supabase-js@2");
        const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
        const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
        const supabase = createClient(supabaseUrl, supabaseKey);

        await supabase.from("affiliate_catalog_requests").insert({
          searched_term,
          user_id,
        });
        return jsonResponse({ success: true });
      }

      default:
        return jsonResponse({ error: `Unknown action: ${action}` }, 400);
    }
  } catch (e) {
    // `catch` binds `unknown` under Deno's strict config, so `.message` does not
    // type-check. Same narrowing the CI-gated functions already use
    // (zad-core-intelligence/index.ts:901) — kept identical on purpose so this
    // file can finally join the same `deno check` gate.
    const msg = String((e as { message?: string })?.message ?? e);
    console.error(`[AmazonCreators] Error: ${msg}`);
    return jsonResponse({ error: msg }, 500);
  }
});
