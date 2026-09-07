// admob-ssv — نقطة الـcallback اللي AdMob بتنادّيها بعد ما العميل يكمّل إعلان مكافأة.
//
// ليه دي موجودة: `zad_ad_reward_grant` بتتنادى من الكلاينت، وهي محكومة بالهوية
// وبفاصل ١٠ ثواني وسقف ٣٠/يوم وجدول تدقيق — بس **مفيش فيها إثبات إن الإعلان اتشاف
// أصلاً**. مستخدم مسجّل دخول يقدر ينادي الـRPC مباشرة وياخد رصيد. SSV هو الإثبات:
// جوجل هي اللي بتنادي، والتوقيع بمفتاحها هو اللي بيميّز نداءها عن أي نداء تاني.
//
// ⚠️ **مش مفعّل لحد ما رابط الـcallback يتسجّل في كونسول AdMob** — وده محتاج حساب
// حقيقي، وهو مؤجّل بقرار المالك. الكود هنا مبني ومختبَر ومستني التسجيل بس.
// لحد ساعتها مسار الكلاينت شغال بحراساته، والـfallback المحلي اتشال (شوف الكوميت).
//
// التسجيل لما الحساب يتفتح:
//   AdMob → Ad unit → Server-side verification → Callback URL:
//   https://<project>.supabase.co/functions/v1/admob-ssv
//   ولازم verify_jwt = false في config.toml — جوجل مش بتبعت Authorization.

import { createClient } from "jsr:@supabase/supabase-js@2";
import {
  b64urlToBytes,
  derToRawSignature,
  isFreshTimestamp,
  pickKey,
  signedContent,
  type VerifierKey,
} from "./verify.ts";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const KEYS_URL = "https://gstatic.com/admob/reward/verifier-keys.json";

// جوجل بتدوّر المفاتيح، فالكاش بـTTL بدل تثبيت في الكود. ساعة كفاية: الدوران
// نادر، وإعادة الجلب أرخص من رفض منحة صحيحة بمفتاح جديد.
let keyCache: { at: number; keys: VerifierKey[] } | null = null;

async function verifierKeys(): Promise<VerifierKey[]> {
  if (keyCache && Date.now() - keyCache.at < 60 * 60 * 1000) return keyCache.keys;
  const res = await fetch(KEYS_URL);
  if (!res.ok) throw new Error(`verifier keys HTTP ${res.status}`);
  const json = await res.json();
  const keys: VerifierKey[] = (json?.keys ?? []).map((k: Record<string, unknown>) => ({
    keyId: Number(k.keyId),
    pem: k.pem as string | undefined,
    base64: k.base64 as string,
  }));
  keyCache = { at: Date.now(), keys };
  return keys;
}

async function signatureValid(content: string, sigB64Url: string, key: VerifierKey): Promise<boolean> {
  const raw = derToRawSignature(b64urlToBytes(sigB64Url));
  if (!raw) return false;
  const spki = b64urlToBytes(key.base64);
  const pub = await crypto.subtle.importKey(
    "spki",
    spki,
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["verify"],
  );
  return await crypto.subtle.verify(
    { name: "ECDSA", hash: "SHA-256" },
    pub,
    raw,
    new TextEncoder().encode(content),
  );
}

Deno.serve(async (req: Request) => {
  // AdMob بتنادي بـGET. أي حاجة تانية مش منها.
  if (req.method !== "GET") return new Response("method not allowed", { status: 405 });

  const url = new URL(req.url);
  const q = url.search.startsWith("?") ? url.search.slice(1) : url.search;
  const p = url.searchParams;

  const content = signedContent(q);
  const sig = p.get("signature");
  const keyId = p.get("key_id");
  // custom_data هو اللي بنحط فيه user_id وقت عرض الإعلان — من غيره مفيش حساب نشحنه.
  const userId = p.get("custom_data");
  const txId = p.get("transaction_id");

  if (!content || !sig || !keyId || !userId || !txId) {
    return new Response("bad request", { status: 400 });
  }
  if (!isFreshTimestamp(p.get("timestamp"), Date.now())) {
    // إعادة تشغيل: نفس الرابط بيفضل صالح للأبد من غير نافذة زمنية.
    return new Response("stale", { status: 400 });
  }

  try {
    const key = pickKey(await verifierKeys(), keyId);
    if (!key) return new Response("unknown key", { status: 400 });
    if (!await signatureValid(content, sig, key)) {
      return new Response("bad signature", { status: 403 });
    }
  } catch (e) {
    console.error("admob-ssv verification error:", e);
    // فشل جلب المفاتيح مش سبب نمنح: الرفض هنا أأمن من المنح على الشك.
    return new Response("verification unavailable", { status: 503 });
  }

  const sb = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);

  // transaction_id فريد من AdMob لكل مكافأة. الإدراج المشروط هو حارس التكرار:
  // نفس الـcallback بيتبعت أكتر من مرة عن قصد لو ردّنا اتأخر.
  const { error: dupErr } = await sb.from("zad_ad_ssv_receipts").insert({
    transaction_id: txId, user_id: userId, ad_unit: p.get("ad_unit"),
    reward_amount: Number(p.get("reward_amount") ?? 0) || null,
  });
  if (dupErr) {
    // 23505 = unique_violation ⇒ اتمنحت قبل كده. رد 200 عشان AdMob ما تعيدش.
    if ((dupErr as { code?: string }).code === "23505") {
      return new Response("duplicate", { status: 200 });
    }
    console.error("ssv receipt insert failed:", dupErr);
    return new Response("receipt failed", { status: 500 });
  }

  const { data, error } = await sb.rpc("zad_ad_reward_grant", { p_user: userId });
  if (error) {
    console.error("zad_ad_reward_grant failed:", error);
    return new Response("grant failed", { status: 500 });
  }
  console.log("admob-ssv granted:", txId, JSON.stringify(data));
  return new Response("ok", { status: 200 });
});
