// tools.ts — بند 33.2: أدوات zad-brain المتاحة داخل جلسة صوتية حية.
//
// نسخة مقصودة (نفس نمط persona.ts/entitlement.ts) من قايمتين موجودتين فعلاً وموثوقتين
// في zad-brain/index.ts:
// - DIRECT_INGRESS_TOOLS (التسعة أدوات دون CONFIRM): "Deterministic ingress for
//   trusted channel parsers" — زاد صوت هو بالظبط نوع القناة دي، فمفيش توسعة للقايمة
//   نفسها، بس إضافة قناة جديدة تستخدمها.
// - CONFIRM_REQUIRED_TOOLS (الأربعة أدوات المالية من validators.ts): بتتحوّل هنا
//   لتأكيد كلامي فوري بدل زرار (قرار العميل 2026-09-01) — أول toolCall لأداة مالية
//   بتوقيعة معينة بيرجّع "awaiting_confirmation" من غير تنفيذ، وnاني toolCall بنفس
//   التوقيعة (يعني الموديل ناداها تاني بعد ما العميل قال "أيوه") هو اللي بينفّذ فعلاً.
//
// السكيمات هنا نسخة حرفية من CHAT_TOOLS في zad-brain/index.ts لنفس الأسماء —
// input_schema بتوصف نفس البيانات اللي runTool() بيتوقعها بالظبط، فأي تعديل هناك
// (حقل جديد، required جديد) لازم ينعكس هنا يدوياً.

export interface VoiceToolDef {
  name: string;
  description: string;
  parameters: Record<string, unknown>;
}

/** تُنفَّذ فوراً عبر zad-brain's agent_execute — مفيش فلوس، مفيش تأكيد لازم. */
export const DIRECT_VOICE_TOOLS: VoiceToolDef[] = [
  {
    name: "add_inventory_item",
    description: "ضيف صنف جديد للمخزون. لو الصنف موجود بالفعل استخدم update_inventory_qty بدلها.",
    parameters: {
      type: "object",
      properties: {
        item_name: { type: "string" },
        quantity: { type: "number" },
        unit: { type: "string", description: "حبة، كيلو، لتر، علبة، كيس..." },
        category: {
          type: "string",
          enum: ["البقالة", "الخضار", "الفواكه", "اللحوم", "الألبان", "المشروبات", "العناية", "أخرى"],
        },
        expiry_date: { type: "string", description: "YYYY-MM-DD لو العميل ذكرها" },
      },
      required: ["item_name", "quantity", "category"],
    },
  },
  {
    name: "update_inventory_qty",
    description: "عدّل كمية صنف موجود بالفعل في المخزون (بما فيها التصفير لما يخلص).",
    parameters: {
      type: "object",
      properties: {
        item_name: { type: "string" },
        new_qty: { type: "number" },
        reason: { type: "string", description: "سبب واضح للتعديل، ١٠ حروف على الأقل" },
      },
      required: ["item_name", "new_qty", "reason"],
    },
  },
  {
    name: "delete_inventory_item",
    description: "احذف صنفاً من المخزون نهائياً فقط لو العميل مش عايز يتابعه بعد كده.",
    parameters: { type: "object", properties: { item_name: { type: "string" } }, required: ["item_name"] },
  },
  {
    name: "add_pharmacy_item",
    description: "ضيف دواء لجدول الصيدلية. متحسبش مواعيد من الوقت الحالي — لو العميل قال عدد مرات بس اتركها فاضية.",
    parameters: {
      type: "object",
      properties: {
        name: { type: "string" },
        dosage: { type: "string" },
        daily_dose_count: { type: "number" },
        dose_times: { type: "string", description: "HH:MM مفصولة بفاصلة — بس لو العميل نطق الساعات" },
        times_explicit: { type: "boolean" },
        unit: { type: "string", enum: ["قرص", "أقراص", "حبة", "حبات", "حبوب", "كبسولة", "كبسولات", "مل", "كريم", "بخاخ", "نقطة", "قطرة", "كيس", "أكياس", "أمبول", "أمبولات", "علبة"] },
        quantity: { type: "number" },
        category: { type: "string", enum: ["عام", "مسكن", "مضاد حيوي", "فيتامين", "مزمن"] },
      },
      required: ["name"],
    },
  },
  {
    name: "update_pharmacy_item",
    description: "عدّل كمية دواء موجود، وصف الجرعة، أو مواعيد تذكيره.",
    parameters: {
      type: "object",
      properties: {
        name: { type: "string" }, dosage: { type: "string" }, remaining_quantity: { type: "number" },
        daily_dose_count: { type: "number" },
        dose_times: { type: "string" }, times_explicit: { type: "boolean" },
      },
      required: ["name"],
    },
  },
  {
    name: "delete_pharmacy_item",
    description: "احذف دواء من قايمة الصيدلية خالص (مش نفاد كمية).",
    parameters: { type: "object", properties: { name: { type: "string" } }, required: ["name"] },
  },
  {
    name: "add_shopping_item",
    description: "ضيف صنف لقائمة التسوق.",
    parameters: {
      type: "object",
      properties: { item_name: { type: "string" }, quantity: { type: "number" } },
      required: ["item_name", "quantity"],
    },
  },
  {
    name: "complete_shopping_item",
    description: "علّم صنفاً في قائمة التسوق أنه اتشرى.",
    parameters: { type: "object", properties: { item_name: { type: "string" } }, required: ["item_name"] },
  },
  {
    name: "delete_shopping_item",
    description: "احذف صنفاً من قائمة التسوق لما العميل يلغي احتياجه له.",
    parameters: { type: "object", properties: { item_name: { type: "string" } }, required: ["item_name"] },
  },
];

/** أدوات فلوس — أول نداء بتوقيعة معينة بيسأل يتأكد بس، تاني نداء بنفس التوقيعة ينفّذ. */
export const CONFIRM_VOICE_TOOLS: VoiceToolDef[] = [
  {
    name: "log_transaction",
    description: "سجّل مصروف أو دخل حصل فعلاً. هتسأل العميل يتأكد بصوته قبل ما يتسجل فعلياً.",
    parameters: {
      type: "object",
      properties: {
        amount: { type: "number" },
        txn_kind: { type: "string", enum: ["expense", "income"] },
        title: { type: "string" },
        category: { type: "string" },
        wallet: { type: "string", enum: ["card", "cash"] },
      },
      required: ["amount", "txn_kind", "title"],
    },
  },
  {
    name: "update_transaction",
    description: "عدّل معاملة موجودة. هتسأل العميل يتأكد بصوته قبل التعديل.",
    parameters: {
      type: "object",
      properties: {
        transaction_id: { type: "string" }, amount: { type: "number" }, title: { type: "string" },
        category: { type: "string" }, txn_kind: { type: "string", enum: ["expense", "income"] },
      },
      required: ["transaction_id"],
    },
  },
  {
    name: "delete_transaction",
    description: "احذف معاملة نهائياً. هتسأل العميل يتأكد بصوته — الحذف مش راجع.",
    parameters: { type: "object", properties: { transaction_id: { type: "string" } }, required: ["transaction_id"] },
  },
  {
    name: "set_monthly_limit",
    description: "اظبط رصيد العميل (اللي الكارت الأخضر بيعرضه). هتسأل العميل يتأكد بصوته.",
    parameters: { type: "object", properties: { monthly_limit: { type: "number" } }, required: ["monthly_limit"] },
  },
];

/** بيتضاف لـsystemInstruction — الموديل محتاج يعرف معنى status اللي بيرجعله من
 *  الأداة، وده عقد اخترعناه إحنا (33.2) مش حاجة Gemini عارفها بنفسه. */
export const VOICE_TOOL_USAGE_INSTRUCTION =
  "لو نداء أداة رجّعلك نتيجتها status=\"awaiting_confirmation\"، معناها ده أداة فلوس ولسه محتاجة تأكيد العميل بصوته — قوله الملخص اللي في summary بصيغة سؤال واضح (\"تقصد كذا؟ أأكد؟\") واستنى رده. لو قال أيوه/تمام/أكد بأي صيغة، نادِ نفس الأداة تاني بنفس البيانات بالظبط. لو قال لأ، سيبها ومتناديهاش تاني. لو status=\"done\" أو \"failed\"، اتصرف عادي: قول اللي حصل من غير ما تسأل تأكيد تاني.";

export const VOICE_TOOLS: VoiceToolDef[] = [...DIRECT_VOICE_TOOLS, ...CONFIRM_VOICE_TOOLS];
const CONFIRM_NAMES = new Set(CONFIRM_VOICE_TOOLS.map((t) => t.name));
export function isConfirmRequired(toolName: string): boolean {
  return CONFIRM_NAMES.has(toolName);
}

/** وصف عربي مختصر — الموديل بيسمعه في نتيجة الأداة وبيستخدمه يسأل العميل يتأكد. نفس
 *  أسلوب zad-brain/index.ts's describeProposal لكن نسخة أصغر لأربع أدوات بس، ومع حالة
 *  delete_transaction اللي كانت ناقصة هناك (بترجع اسم الأداة الخام بدل وصف). */
export function describeVoiceProposal(tool: string, input: Record<string, unknown>): string {
  const amt = (n: unknown) => typeof n === "number" ? String(n) : String(n ?? "");
  switch (tool) {
    case "log_transaction":
      return `${input.txn_kind === "income" ? "دخل" : "مصروف"} ${amt(input.amount)} — ${input.title ?? ""}`;
    case "update_transaction": {
      const parts: string[] = [];
      if (input.amount !== undefined) parts.push(`المبلغ ${amt(input.amount)}`);
      if (input.title !== undefined) parts.push(`الوصف "${input.title}"`);
      if (input.category !== undefined) parts.push(`الفئة ${input.category}`);
      return `تعديل معاملة: ${parts.join("، ")}`;
    }
    case "delete_transaction":
      return "حذف معاملة نهائياً";
    case "set_monthly_limit":
      return `رصيدك يبقى ${amt(input.monthly_limit)}`;
    default:
      return tool;
  }
}
