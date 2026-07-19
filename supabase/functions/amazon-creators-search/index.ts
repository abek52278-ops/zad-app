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
              model: "llama-3.3-70b-versatile",
              messages: [
                { role: "system", content: systemPrompt },
                { role: "user", content: userPrompt }
              ],
              response_format: { type: "json_object" },
              temperature: 0.1,
              max_tokens: 100,
            }),
          });

          const groqData = await groqResp.json();
          const content = JSON.parse(groqData.choices?.[0]?.message?.content || "{}");
          return jsonResponse({ match: content.matched_id || null });
        }

        // Fallback: simple keyword matching
        const query = product_name.toLowerCase();
        for (const item of catalog) {
          if (item.name.toLowerCase().includes(query)) return jsonResponse({ match: item.id });
          for (const kw of item.keywords) {
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
    console.error(`[AmazonCreators] Error: ${e.message}`);
    return jsonResponse({ error: e.message }, 500);
  }
});
