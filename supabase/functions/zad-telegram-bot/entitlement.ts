// قفل الصوت والصور على تليجرام.
//
// النص بيتحاسب في zad-brain (نفس البوابة اللي شات التطبيق بيعدي منها)، فالملف ده
// مسؤول عن الحاجتين اللي البوت بينفذهم بنفسه من غير ما يعدي على العقل: الرسالة
// الصوتية وصورة الفاتورة. الاتنين بيروحوا لـ zad-core-intelligence مباشرة، يعني من
// غير الفحص ده مفيش أي حاجة بتمنع مستخدم مجاني من تفريغ صوت ومسح فواتير بلا حدود
// من برّه التطبيق.
//
// ودول بالظبط أغلى وأهم ميزتين للعميل وهو برّه البيت — تسجيل صوتي وهو ماشي، أو تصوير
// فاتورة في المطعم — فهما اللي بيبيعوا الاشتراك.

// deno-lint-ignore no-explicit-any
type Sb = any;

export type MediaKind = "voice" | "scan";

export interface MediaGate {
  allowed: boolean;
  reply?: string;
}

const LOCKED_VOICE =
  "🔒 الرسايل الصوتية على تليجرام متاحة لمشتركي باقات زاد المميزة.\n\n" +
  "الكتابة العادية شغالة زي ما هي ومجاناً — ابعتلي \"صرفت ٥٠ قهوة\" وهسجلها فوراً.\n" +
  "افتح تطبيق زاد واشترك عشان تفعّل الصوت.";

const LOCKED_PHOTO =
  "🔒 مسح الفواتير بالصور على تليجرام متاح لمشتركي باقات زاد المميزة.\n\n" +
  "تقدر تصوّر الفاتورة من كاميرا التطبيق نفسه، أو تكتبهالي هنا عادي ومجاناً.\n" +
  "افتح تطبيق زاد واشترك عشان تفعّل الصور هنا.";

const EXHAUSTED_VOICE = "🔒 خلص رصيد الرسايل الصوتية في باقتك الشهرية. تقدر ترقّي باقتك من التطبيق.";
const EXHAUSTED_PHOTO = "🔒 خلص رصيد مسح الفواتير في باقتك الشهرية. تقدر ترقّي باقتك من التطبيق.";

/**
 * بيتنادى **قبل** تنزيل الملف من تليجرام، مش بعده: التنزيل نفسه بيستهلك وقت وباندويدث
 * على مستخدم مقفول أصلاً، والأهم إنه بيوصل بايتات لـ zad-core-intelligence لو حد نسي
 * يفحص الرد.
 *
 * بيفشل **مقفول** هنا، بعكس بوابة zad-brain اللي بتفشل مفتوحة. السبب إن ده مسار مدفوع
 * بحت: لو الفحص وقع، إتاحته للكل معناها إن الميزة اللي بنبيعها بقت مجانية لأي حد
 * بيحاول في اللحظة دي. النص لسه شغال مجاناً في الحالة دي، فالعميل مش مقطوع.
 */
export async function mediaGate(sb: Sb, userId: string, kind: MediaKind): Promise<MediaGate> {
  const locked = kind === "voice" ? LOCKED_VOICE : LOCKED_PHOTO;
  let status: Record<string, unknown> | null = null;
  try {
    const { data, error } = await sb.rpc("zad_entitlement_status", { p_user: userId });
    if (error) {
      console.error("zad_entitlement_status failed, failing closed:", error.message);
      return { allowed: false, reply: locked };
    }
    status = data as Record<string, unknown>;
  } catch (e) {
    console.error("zad_entitlement_status threw, failing closed:", e);
    return { allowed: false, reply: locked };
  }

  // الباقة المجانية مالهاش وسائط على تليجرام أصلاً — رسالة ترقية، مش رسالة "خلص رصيدك".
  if (status?.telegram_media !== true) return { allowed: false, reply: locked };

  // مشترك: الرصيد الشهري هو اللي بيتخصم منه. الخصم بيحصل هنا عشان نفس السبب — قبل
  // ما نصرف على تنزيل الملف والنداء على الموديل.
  try {
    const { data, error } = await sb.rpc("zad_entitlement_consume", { p_user: userId, p_kind: kind });
    if (error) {
      console.error("zad_entitlement_consume failed, failing closed:", error.message);
      return { allowed: false, reply: locked };
    }
    const d = data as { allowed?: boolean; reason?: string };
    if (d?.allowed === true) return { allowed: true };
    if (d?.reason === "quota_exhausted") {
      return { allowed: false, reply: kind === "voice" ? EXHAUSTED_VOICE : EXHAUSTED_PHOTO };
    }
    return { allowed: false, reply: locked };
  } catch (e) {
    console.error("zad_entitlement_consume threw, failing closed:", e);
    return { allowed: false, reply: locked };
  }
}
