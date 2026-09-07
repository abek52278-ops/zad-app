// التحقق من توقيع AdMob Server-Side Verification.
//
// AdMob بتنادي رابط الـcallback بتاعنا بـquery string منتهية بـ&signature=...&key_id=...
// المحتوى الموقَّع هو **كل اللي قبل `&signature=`** حرفيًا — مش الـquery كلها ومش
// معاد ترتيبها. أي إعادة بناء للسلسلة بتكسر التحقق، عشان كده بنقص النص الخام.
//
// المفاتيح العامة من https://gstatic.com/admob/reward/verifier-keys.json، وكل مفتاح
// له key_id بيوصل في الطلب نفسه. جوجل بتدوّر المفاتيح، فبنجيبها ونكاشها بـTTL
// بدل ما نثبّتها في الكود.

export interface VerifierKey {
  keyId: number;
  pem?: string;
  base64: string;
}

/** المحتوى الموقَّع: كل شيء قبل `&signature=`. null لو الپارامتر مش موجود. */
export function signedContent(rawQuery: string): string | null {
  const i = rawQuery.indexOf("&signature=");
  if (i < 0) return null;
  return rawQuery.slice(0, i);
}

/** base64url → bytes. توقيع AdMob بيوصل base64url مش base64 عادي. */
export function b64urlToBytes(s: string): Uint8Array<ArrayBuffer> {
  const b64 = s.replace(/-/g, "+").replace(/_/g, "/");
  const pad = b64.length % 4 === 0 ? "" : "=".repeat(4 - (b64.length % 4));
  const bin = atob(b64 + pad);
  const out = new Uint8Array(new ArrayBuffer(bin.length));
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

/**
 * توقيع ECDSA بيوصل بترميز DER؛ WebCrypto عايزه raw (r||s) بطول ٦٤ بايت لـP-256.
 * التحويل ده هو أكتر خطوة بيتنسى فيها الحشو: r و s ممكن يبقوا أقصر من ٣٢ بايت
 * وساعتها لازم يتحشوا من الشمال، ولو أطول (بايت صفر في الأول) لازم يتقص.
 */
export function derToRawSignature(der: Uint8Array): Uint8Array<ArrayBuffer> | null {
  if (der.length < 8 || der[0] !== 0x30) return null;
  let i = 2;
  if (der[1] & 0x80) i = 2 + (der[1] & 0x7f); // طول طويل
  const readInt = (): Uint8Array | null => {
    if (der[i] !== 0x02) return null;
    const len = der[i + 1];
    const start = i + 2;
    i = start + len;
    let v = der.slice(start, start + len);
    while (v.length > 32 && v[0] === 0x00) v = v.slice(1);
    if (v.length > 32) return null;
    const padded = new Uint8Array(32);
    padded.set(v, 32 - v.length);
    return padded;
  };
  const r = readInt();
  if (!r) return null;
  const s = readInt();
  if (!s) return null;
  const raw = new Uint8Array(new ArrayBuffer(64));
  raw.set(r, 0);
  raw.set(s, 32);
  return raw;
}

/** المفتاح المطابق لـkey_id، أو null. */
export function pickKey(keys: VerifierKey[], keyId: string | null): VerifierKey | null {
  if (!keyId) return null;
  const id = Number(keyId);
  if (!Number.isFinite(id)) return null;
  return keys.find((k) => k.keyId === id) ?? null;
}

/**
 * الطابع الزمني من AdMob بالميلي ثانية. الطلبات القديمة بتترفض عشان إعادة
 * التشغيل: حد اتسجل مرة يقدر يعيد نفس الرابط للأبد لو مفيش نافذة.
 */
export function isFreshTimestamp(tsMs: string | null, nowMs: number, maxAgeMs = 60 * 60 * 1000): boolean {
  if (!tsMs) return false;
  const t = Number(tsMs);
  if (!Number.isFinite(t)) return false;
  // مستقبل بعيد مرفوض كمان — ساعة سماح للفروق بين السيرفرات.
  return t <= nowMs + 5 * 60 * 1000 && nowMs - t <= maxAgeMs;
}
