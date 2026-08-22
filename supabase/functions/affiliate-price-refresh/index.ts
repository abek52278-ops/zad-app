// affiliate-price-refresh — تحديث أسعار أمازون للكتالوج.
//
// الفجوة: `affiliate_products.average_price_sar` سعر مكتوب مرة واحدة، والكارت في
// الرئيسية بيعرضه كأنه السعر الحالي. ده ادعاء بيانات.
//
// الحل: endpoint يرجّع الأسعار المخزنة **مع تاريخ آخر تحقق** لكل صف، عشان الكلاينت
// يعرض "منذ X يوم" ويطلب refresh لما تتقديم. تحديث السعر نفسه من scraping مباشر
// مخالف لشروط أمازون — فالمسار الصحيح هو: الأدمن يدخل سعر محدث (admin API هنا)،
// والكلاينت يعرض عمر السعر بشفافية بدل ما يمثله كأنه حي.
//
// المصادقة: service role فقط (الأدمن) أو authenticated للقراءة.

import { createClient, SupabaseClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

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

async function requireUser(sb: SupabaseClient, req: Request): Promise<string | null> {
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer /i, "");
  if (!token) return null;
  const { data } = await sb.auth.getUser(token);
  return data.user?.id ?? null;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders() });
  if (!SUPABASE_URL || !SERVICE_ROLE_KEY) return jsonResponse({ error: "not_configured" }, 503);
  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

  try {
    // قراءة: أي مستخدم مسجل — الأسعار + عمرها
    if (req.method === "GET") {
      const user = await requireUser(sb, req);
      if (!user) return jsonResponse({ error: "unauthorized" }, 401);
      const { data, error } = await sb.from("affiliate_products")
        .select("id, product_name_ar, average_price_sar, price_checked_at, asin_verified")
        .eq("is_active", true);
      if (error) return jsonResponse({ error: error.message }, 500);
      const now = Date.now();
      const items = (data ?? []).map((r: Record<string, unknown>) => {
        const checkedAt = r.price_checked_at ? new Date(String(r.price_checked_at)).getTime() : null;
        return { ...r, price_age_days: checkedAt ? Math.floor((now - checkedAt) / 86_400_000) : null };
      });
      return jsonResponse({ items });
    }

    // تحديث سعر: service role / أدمن فقط
    if (req.method === "POST") {
      const body = await req.json();
      const rows = Array.isArray(body?.updates) ? body.updates : [];
      if (rows.length === 0 || rows.length > 50) {
        return jsonResponse({ error: "updates must be a non-empty array (max 50)" }, 400);
      }
      const nowIso = new Date().toISOString();
      for (const u of rows) {
        if (!u?.id || typeof u.average_price_sar !== "number" || u.average_price_sar < 0) continue;
        await sb.from("affiliate_products")
          .update({ average_price_sar: u.average_price_sar, price_checked_at: nowIso })
          .eq("id", String(u.id));
      }
      return jsonResponse({ ok: true, updated: nowIso });
    }

    return jsonResponse({ error: "Method not allowed" }, 405);
  } catch (e) {
    console.error("affiliate-price-refresh failed:", e);
    return jsonResponse({ error: "internal" }, 500);
  }
});
