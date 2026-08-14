// أسماء حقول معروف إنها بتحمل بيانات خام كبيرة. الاسم لوحده مش كفاية — نقط النداء
// بتبعت الوسائط كـ array (`{ args: [...] }`) فالمفتاح بيبقى "0"/"1"/"2" — عشان كده الطول
// هو الحارس الأساسي والاسم حارس إضافي.
const LOG_REDACT_KEYS = /^(image|photo|audio|file)?_?base64$|^(image|photo|audio)_data$/i;

// أطول من كده مالوش أي قيمة تشخيصية في لوج. برومبت طبيعي أقصر من كده بكتير؛ اللي بيعدّيه
// عمليًا هو الـbase64.
const LOG_MAX_STRING = 512;
const LOG_MAX_DEPTH = 6;

/**
 * تنضيف أي payload قبل ما يتكتب في `agent_logs`.
 *
 * السبب: `logged()` كانت بتمرّر الـinput كامل، وأكشنين الرؤية بيبعتوا `image_base64` جوّه
 * الوسائط — يعني صورة المستخدم بالكامل كانت هتتكتب في جدول لوجات. ده تخزين صور شخصية في
 * مكان مش متصمّم ليها، وتضخّم في القاعدة (صورة واحدة ممكن تعدّي ميجابايت كـbase64).
 *
 * التنضيف مركزي هنا مش عند نقط النداء عن قصد: أي أكشن رؤية جديد يتضاف بعد كده بيتغطّى
 * تلقائي من غير ما حد يفتكر يحجب حاجة.
 *
 * الملف منفصل عن `index.ts` عشان يتختبر: استيراد `index.ts` بيشغّل `Deno.serve` عند
 * التحميل، فأي اختبار بيستورده كان هيقوّم سيرفر.
 */
export function redactForLog(value: unknown, depth = 0): unknown {
  if (depth > LOG_MAX_DEPTH) return "[redacted: too deep]";
  if (typeof value === "string") {
    return value.length > LOG_MAX_STRING ? `[redacted ${value.length} chars]` : value;
  }
  if (Array.isArray(value)) return value.map((v) => redactForLog(v, depth + 1));
  if (value && typeof value === "object") {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      out[k] = LOG_REDACT_KEYS.test(k) ? "[redacted]" : redactForLog(v, depth + 1);
    }
    return out;
  }
  return value;
}
