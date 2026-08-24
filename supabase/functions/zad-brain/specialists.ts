// specialists.ts — توجيه الوكلاء المتخصصين (المرحلة ٣).
//
// الفكرة: كل رسالة عميل بتتصنّف لوكيل متخصص (مال / مخزون وشيف / صيدلية / عائلة ومهام)،
// والبرومبت بياخد هوية الوكيل + تعليماته. التوجيه **حقيقي مش تجميلي**:
// - التصنيف كلمات-مفتاحية deterministic سريعة (مش نداء موديل إضافي، مش تكلفة).
// - أي رسالة مش متطابقة = وكيل عام (general) بنفس البرومبت الموجود — مفيش تغيير سلوك.
// - كل لفة agent_turn بتسجّل specialist في zad_brain_runs.specialist — ده الـ trace
//   اللي بيخلي "مين عالج الرسالة دي" مثبت في الداتابيز مش ادعاء.
// - الأدوات نفسها بتتحقق في validators.ts زي ما هي — التوجيه ما بيغيرش صلاحيات.

import { SupabaseClient } from "jsr:@supabase/supabase-js@2";

export type SpecialistId = "finance" | "pantry" | "pharmacy" | "family" | "home" | "general";

export interface SpecialistDef {
  id: SpecialistId;
  /** اسم بشري يظهر للعميل في كارت التنفيذ الحي. */
  nameAr: string;
  /** جملة حالة بتظهر وهو شغال ("بيفتش في الدفتر..."). */
  activeLineAr: string;
  keywords: string[];
}

// الكلمات المفتاحية مصرية/عربية فعلية من طريقة كلام العملاء. المطابقة على مستوى
// substring بعد تطبيع بسيط (إزالة تشكيل وتوحيد أ/إ/آ) — مش regex معقد بيتكسر.
export const SPECIALISTS: Record<SpecialistId, SpecialistDef> = {
  finance: {
    id: "finance",
    nameAr: "وكيل المال",
    activeLineAr: "وكيل المال بيفتح الدفتر...",
    keywords: [
      "مصروف", "مصاريف", "صرفت", "دفعت", "فلوس", "جنيه", "ريال", "درهم", "رصيد", "الميزانية",
      "ميزانيتي", "الكارت", "راتب", "قبضت", "قسط", "دين", "ديون", "ادخر", "مدخرات", "باقي",
      "معايا", "فاضل", "اشتراك", "فاتورة", "كهربا", "مياه", "غاز", "انترنت", "بنك", "فيزا",
      "كاش", "سحبت", "حوّلت", "حولت", "تحويل", "إيداع", "ايداع", "budget", "spend", "spent",
      "paid", "salary", "money", "balance",
    ],
  },
  pantry: {
    id: "pantry",
    nameAr: "وكيل المخزون والمطبخ",
    activeLineAr: "وكيل المطبخ بيصور الخزنة...",
    keywords: [
      "مخزون", "خزنة", "لازم نشتري", "ضيف", "زود", "البقالة", "خضار", "فواكه", "لحمة", "لحوم",
      "ألبان", "اللبن", "جبنة", "أرز", "مكرونة", "زيت", "سكر", "شاي", "قهوة", "تاريخ الصلاحية",
      "صلاحية", "هينزل", "وجبة", "أطبخ", "اطبخ", "طبخة", "وصفة", "العشا", "الفطار", "الغدا",
      "غدا", "قائمة الشراء", "التسوق", "سوبر ماركت", "ماركت", "inventory", "grocery", "recipe",
      "cook", "meal", "shopping",
    ],
  },
  pharmacy: {
    id: "pharmacy",
    nameAr: "وكيل الصيدلية",
    activeLineAr: "وكيل الصيدلية بيراجع الجرعات...",
    keywords: [
      "دوا", "الدوا", "دواء", "أدوية", "ادوية", "حبة", "جرعة", "جرعات", "قرص", "شراب", "بخاخ",
      "مرهم", "تحليل", "معمل", "دكتور", "عيادة", "مرض", "سخن", "حرارة", "صداع", "ضغط", "سكري",
      "انسولين", "فيتامين", "مكمل", "مسكن", "antibiotic", "medicine", "dose", "pill",
    ],
  },
  family: {
    id: "family",
    nameAr: "وكيل العائلة والمهام",
    activeLineAr: "وكيل العائلة بينظم المواعيد...",
    keywords: [
      "مهمة", "مهام", "موعد", "مواعيد", "مذكر", "فكرني", "ذكرني", "جدول", "مناسبة", "عيد ميلاد",
      "عزومة", "زيارة", "مدرسة", "أطفال", "الاولاد", "اولادي", "زوجتي", "جوازي", "واجب", "امتحان",
      "مذاكرة", "تسبيحة", "tasbih", "task", "reminder", "appointment",
    ],
  },
  home: {
    id: "home",
    nameAr: "وكيل المنزل والدفع",
    activeLineAr: "وكيل المنزل بيراجع الفواتير والصيانة...",
    keywords: [
      "فاتورة", "فواتير", "كهربا", "مياه", "غاز", "انترنت", "سددت", "سداد", "دفعت الفاتورة",
      "الضمان", "ضمان", "صيانة", "التكييف", "تكييف", "غسالة", "ثلاجة", "بوتاجاز", "سخان",
      "بيت", "شقة", "إيجار البيت", "نظافة", "ترتيب", "عطل", "بايظ", "مكسور", "تصليح",
      "bill", "warranty", "maintenance", "home", "repair",
    ],
  },
  general: {
    id: "general",
    nameAr: "زاد",
    activeLineAr: "",
    keywords: [],
  },
};

const ORDER: SpecialistId[] = ["finance", "pantry", "pharmacy", "family", "home"];

/** تطبيع خفيف: تشكيل، همزات، ألف مقصورة، تاء مربوطة. */
function normalize(text: string): string {
  return text
    .replace(/[\u064B-\u065F\u0670]/g, "")
    .replace(/[أإآ]/g, "ا")
    .replace(/ى/g, "ي")
    .replace(/ة/g, "ه")
    .toLowerCase();
}

/**
 * تصنيف deterministic. أول وكيل بيجمع أكتر نقاط كلمات مفتاحية بيكسب — العدّ مهم
 * لأن رسالة واحدة ممكن تمس مجالات (لو كلمة واحدة فقط اتطابقت بنقطة واحدة والباقي
 * صفر، برضه بيكسب). مفيش تطابق خالص = general.
 */
export function routeSpecialist(message: string): SpecialistId {
  const norm = normalize(message);
  let best: SpecialistId = "general";
  let bestScore = 0;
  for (const id of ORDER) {
    const score = SPECIALISTS[id].keywords.reduce(
      (acc, kw) => acc + (norm.includes(normalize(kw)) ? 1 : 0),
      0,
    );
    if (score > bestScore) {
      bestScore = score;
      best = id;
    }
  }
  return best;
}

/** سطر هوية الوكيل اللي بيتحقن في برومبت المحادثة. general = null (البرومبت زي ما هو). */
export function specialistPromptBlock(id: SpecialistId): string | null {
  if (id === "general") return null;
  const s = SPECIALISTS[id];
  const scope: Record<Exclude<SpecialistId, "general">, string> = {
    finance:
      "- نطاقك: المعاملات، الرصيد، الالتزامات، الاشتراكات، الديون، دورة الراتب. أدوات الفلوس بتعرض تأكيد قبل الكتابة.",
    pantry:
      "- نطاقك: المخزون، قائمة الشراء، الصلاحيات، اقتراح وجبات من الموجود فعلاً في المخزون بس — ماتقترحش صنف مش موجود.",
    pharmacy:
      "- نطاقك: أدوات الصيدلية والجرعات. أوقات الجرعات لازم تكون ضمن ٢٤ ساعة وبصيغة HH:mm — دي قاعدة تحقق صارمة، لو الوقت مش مفهوم اسأل بدل ما تخمّن.",
    family:
      "- نطاقك: المهام والمواعيد والتذكيرات وأخبار العائلة. المهمة محتاجة عنوان واضح، ولو التاريخ/الوقت مش محدد اسأل.",
    home:
      "- نطاقك: فواتير البيت ومتابعة سدادها (تنبيه واستفسار بس — مفيش تسجيل فلوس من هنا)، الأجهزة والضمانات والصيانة الدورية، وأعطال المنزل. تقدر تفتح شاشة الصيانة للعميل بـ app_command.",
  };
  return `=== الوكيل المتخصص ===
انت دلوقتي ${s.nameAr} داخل نظام زاد — الجزء المتخصص اللي العقل العام حوّل له الرسالة دي.
${scope[id as Exclude<SpecialistId, "general">]}
برا نطاقك: جاوب باقتضاب واعرض إنك تحوّل الموضوع للوكيل المناسب بمجرد ما العميل يكمله — متتعمقش فيه.
=== نهاية الوكيل ===`;
}

/**
 * Trace دائم: specialist بيتكتب في صف zad_brain_runs بتاع اللفة. الفشل هنا مابيرميش —
 * التتبع تحسين، مش مسار حرج.
 */
export async function recordSpecialistTrace(
  sb: SupabaseClient,
  runId: string | null | undefined,
  specialist: SpecialistId,
): Promise<void> {
  if (!runId || specialist === "general") return;
  try {
    await sb.from("zad_brain_runs").update({ specialist }).eq("id", runId);
  } catch (e) {
    console.error("specialist trace failed:", e);
  }
}

/**
 * تقليل الأدوات المعروضة حسب الوكيل — سر سرعة الردود:
 * 39 أداة في كل طلب بتخلي الموديل يقرا أوصاف ضخمة ويتردد بين بدائل كتير قبل
 * ما يختار. لما نعرض بس أدوات نطاق الوكيل + الأدوات العامة، القرار بيبقى أسرع
 * وأدق. لو الرسالة عامة (general)، كل الأدوات متاحة زي ما هي — مفيش تغيير سلوك.
 */
export function scopeToolsForSpecialist<T extends { name: string }>(
  tools: T[],
  specialist: SpecialistId,
): T[] {
  if (specialist === "general") return tools;
  const scope: Record<Exclude<SpecialistId, "general">, string[]> = {
    finance: [
      "log_transaction", "allocate_income", "update_transaction", "delete_transaction",
      "set_monthly_limit", "add_debt", "update_debt", "delete_debt",
      "add_obligation", "update_obligation", "delete_obligation",
      "add_subscription", "update_subscription", "delete_subscription",
      "forward_ledger", "check_price_online", "query_family", "weekly_savings_plan",
      "home_health_score", "propose_next_month_budget",
    ],
    pantry: [
      "add_inventory_item", "update_inventory_qty",
      "add_shopping_item", "complete_shopping_item", "delete_shopping_item",
      "suggest_product", "check_price_online", "find_nearby_stores", "web_search",
    ],
    pharmacy: [
      "add_pharmacy_item", "update_pharmacy_item", "delete_pharmacy_item",
      "log_pharmacy_dose", "find_nearby_stores", "web_search",
    ],
    family: ["schedule_task", "query_family", "family_digest"],
    home: [
      "app_command",
      "add_maintenance_item", "update_maintenance_item", "delete_maintenance_item",
      "add_obligation", "update_obligation", "delete_obligation",
      "forward_ledger", "web_search", "home_health_score",
    ],
  };
  const allowed = new Set([
    ...(scope[specialist as Exclude<SpecialistId, "general">] ?? []),
    // الأدوات العابرة للنطاقات — متاحة دايمًا
    "remember", "link_memory", "web_search", "set_market", "set_transaction_category",
    "update_emergency_fund_balance", "add_maintenance_item", "update_maintenance_item",
    "delete_maintenance_item", "app_command", "learn_skill", "home_health_score",
  ]);
  return tools.filter((t) => allowed.has(t.name));
}
