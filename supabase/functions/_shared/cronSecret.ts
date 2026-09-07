// حارس أسرار الكرون — نسخة واحدة لكل الدوال اللي بتشتغل بـ`verify_jwt = false`.
//
// الدوال دي الهيدر بتاعها هو **الحاجة الوحيدة بينها وبين الإنترنت**، فالرفض لازم
// يبقى مقفول (سر مش متظبط = رفض، مش فتح).
//
// وليه التشخيص جوّه: قبل كده الرفض كان بيرجّع 401 وخلاص. ضبط سر واحد
// (`ZAD_FX_CRON_SECRET`) استهلك **ست محاولات** يوم 2026-09-07، وأول تلاتة منهم
// ماطلّعوش ولا معلومة واحدة — كان مستحيل تفرّق بين «مسافة اتنسخت مع القيمة»
// و«قيمة من مصدر تاني» من غير تجريب أعمى. أول ما الطول والبصمة اتسجّلوا، السبب
// اتحدد من أول محاولة.

/** بصمة قصيرة للمقارنة في اللوج — ٨ حروف من SHA-256، مش قابلة للعكس لسر عشوائي. */
export async function fingerprint(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest).slice(0, 4))
    .map((b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * بيقارن هيدر جاي بالسر المتظبط على المشروع.
 *
 * الرد على النداء بيفضل 401 في كل الحالات — اللي بينده من بره **مايتعلّمش حاجة**
 * من الفرق. التفصيل بيروح للوج بس، وحتى هناك القيمة نفسها مابتتكتبش:
 *
 *   الأطوال مختلفة + trimmed match=true → مسافة أو سطر جديد في النسخ
 *   الأطوال متساوية والبصمات مختلفة      → قيمة تانية خالص
 *   البصمات متساوية                       → المشكلة مش في القيمة
 */
export async function secretMatches(received: string | null, envName: string): Promise<boolean> {
  const expected = Deno.env.get(envName);
  if (!expected) {
    console.error(`[auth] ${envName} is not set on this project — rejecting.`);
    return false;
  }
  if (!received) {
    console.error(`[auth] ${envName}: no secret header on the request — rejecting.`);
    return false;
  }
  if (received === expected) return true;

  console.error(
    `[auth] ${envName} mismatch — received len=${received.length} fp=${await fingerprint(received)}, ` +
    `expected len=${expected.length} fp=${await fingerprint(expected)}. ` +
    `trimmed match=${received.trim() === expected.trim()}`,
  );
  return false;
}
