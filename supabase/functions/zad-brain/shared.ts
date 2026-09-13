// Small pure decision functions extracted out of the Deno.serve handler purely so
// Task 16.4's tests #11/#12 don't need a live server or a live database to verify.

/** Task 16.3 partial-run idempotency: skip a new daily run if one in the window already mutated data. */
export function hasRecentMutatingRun(runs: Array<{ mutations: unknown }>): boolean {
  return runs.some((r) => Array.isArray(r.mutations) && r.mutations.length > 0);
}

/**
 * بيطبّع `trigger` الجاي من الطلب لقيمة يقبلها `zad_brain_runs_trigger_check`
 * (`daily`/`event`/`chat` بس).
 *
 * ليه: `GeofenceBroadcastReceiver` بيبعت `"geofence_enter"`، والنوع `Trigger` في
 * index.ts كان cast من غير تحقق وقت التشغيل. الإدراج في `zad_brain_runs` كان بيقع على
 * الـCHECK، والخطأ مكانش بيتقرا: `runId` بيبقى undefined، الموديل بيشتغل عادي، وكل
 * تحديث بعد كده (`success`/`queued`) بيطابق صفر صفوف. يعني تشغيلات الجيوفينس كانت
 * **بتختفي من المراقبة كليًا** — لا نجاح ولا فشل. أي قيمة مش معروفة = حدث، وده صح
 * دلاليًا (دخول نطاق محل حدث فعلاً)، ومطابق لما `scope.source` كان بيعمله أصلاً.
 */
export function normalizeBrainTrigger(raw: unknown): "daily" | "event" | "chat" {
  return raw === "daily" || raw === "chat" ? raw : "event";
}

/**
 * عنوان إشعار نتيجة مهمة `agent_tasks`، وهل هي مبادرة من زاد ولا طلب من العميل.
 *
 * كل النتايج كانت بتطلع بعنوان «زاد خلّص مهمة كنت طلبتها» — حتى المهام اللي الماسح
 * الاستباقي كتبها والعميل عمره ماطلبها (ملخص البيت، توقّع الصرف، متابعة الدوا…). ده
 * كذب صغير بيبوّظ ثقة، وبيخبّي إن زاد هو اللي بادر.
 *
 * `reminder` (وهو الافتراضي على العمود) = طلب العميل. أي نوع تاني = مبادرة، ودي اللي
 * بتتبعت لتليجرام كمان (3 من 4 مستخدمين حقيقيين مربوطين، وFCM صفر توكن — يعني قبل كده
 * النتيجة الاستباقية كانت بتقف في قايمة إشعارات جوه التطبيق محدش بيفتحها).
 */
export function agentTaskNotice(kind: string | null | undefined): { title: string; proactive: boolean } {
  const k = (kind ?? "").trim();
  if (k === "" || k === "reminder") {
    return { title: "زاد خلّص مهمة كنت طلبتها ✅", proactive: false };
  }
  const titles: Record<string, string> = {
    home_weekly_digest: "📋 ملخص البيت من زاد",
    spend_forecast: "📈 زاد بيتوقّع مصروف الأسبوع",
    spending_ahead: "⚠️ زاد لاحظ إن الصرف أسرع من الميزانية",
    med_followup: "💊 زاد بيتابع معاك الدوا",
    bill_reminder: "🧾 زاد بيفكّرك بفاتورة قربت",
    warranty_reminder: "🛡️ زاد بيفكّرك بضمان قرب ينتهي",
    listener_gap_alert: "🔔 زاد لاحظ إن إشعارات البنك وقفت",
    goal_review: "🎯 زاد بيتابع هدفك",
    store_arrival: "🛒 زاد لاحظ إنك جنب محل",
  };
  return { title: titles[k] ?? "💡 زاد لاحظ حاجة تهمّك", proactive: true };
}

/**
 * لو مبادرة مستحقة نوعها مكتوم (رفض العميل من تليجرام، 20260913190000)، بترجّع إمتى تتأجل —
 * آخر `suppress_until` لسه في المستقبل. `null` = نفّذ عادي.
 *
 * ليه تأجيل مش إلغاء: الكتم كان بيتقرا في الماسح بس، ومنفّذ المهام مابيشوفوش. فمتابعة هدف
 * أسبوعية (`goal_review`، مهمة متكررة مش من الماسح) كانت هتتبعت تاني رغم الرفض. والإلغاء كان
 * هيقتل السلسلة المتكررة كلها بعد كتم ٣ أيام بس («عرفت خلاص») — التأجيل لنهاية الكتم بيحترم
 * الرفض ويسيب الهدف عايش. طلبات العميل (`reminder`) عمرها ما بتتأجل بكتم.
 */
export function postponeForSuppression(
  kind: string | null | undefined,
  mutes: Array<{ suppress_until: string | null }>,
  nowMs: number,
): string | null {
  if (!agentTaskNotice(kind).proactive) return null;
  let latest = -Infinity;
  for (const m of mutes) {
    const t = m.suppress_until ? Date.parse(m.suppress_until) : NaN;
    if (Number.isFinite(t) && t > nowMs && t > latest) latest = t;
  }
  return Number.isFinite(latest) ? new Date(latest).toISOString() : null;
}

// ── وصول لمحل (مرحلة ٢ بند ٣) ─────────────────────────────────────────────
// دخول نطاق سوبرماركت/مول/صيدلية كان بيبعت `geofence_enter` لمسار التحليل: نداء موديل
// ورده بيرجع في الـHTTP response والتطبيق بيتجاهله — لا تليجرام ولا رؤية. القايمة هنا
// بتتبني من البيانات مباشرة (قايمة الشراء + المخزون الناقص + الأدوية القربت)، من غير موديل.

export type StoreCategory = "supermarket" | "mall" | "pharmacy";

/** ساعات بين رسالتين لنفس المحل، وأقصى عدد رسايل في ٢٤ ساعة — عشان التنقل في مول مايبقاش سبام. */
export const STORE_ARRIVAL_SAME_STORE_HOURS = 6;
export const STORE_ARRIVAL_DAILY_CAP = 3;
const STORE_ARRIVAL_MAX_LISTED = 10;

export function normalizeStoreCategory(raw: unknown): StoreCategory | null {
  const v = String(raw ?? "").trim().toLowerCase();
  return v === "supermarket" || v === "mall" || v === "pharmacy" ? v : null;
}

/** اسم محل أو صنف جاي من الجهاز: نص بس، من غير control chars ولا «»، بطول معقول. */
function cleanText(raw: unknown, max: number): string {
  if (typeof raw !== "string") return "";
  return raw.replace(/[\u0000-\u001f\u007f\u00ab\u00bb]/g, " ").replace(/\s+/g, " ").trim().slice(0, max);
}

export function sanitizeStoreName(raw: unknown): string {
  return cleanText(raw, 60);
}

/** قايمة الجهاز تلميح بس (ممكن تكون أحدث من السيرفر لو كان أوفلاين) — مقصوصة ومنضّفة. */
export function sanitizeItemHints(raw: unknown): string[] {
  if (!Array.isArray(raw)) return [];
  return raw.slice(0, 30).map((x) => cleanText(x, 60)).filter((x) => x.length > 0);
}

/**
 * مفتاح مقارنة للأصناف: «لبن» و«لَبن» و«لبن » نفس الصنف. بيشيل التشكيل والتطويل وبيوحّد
 * الألف والياء والتاء المربوطة — للمقارنة بس، الاسم المعروض بيفضل زي ما العميل كتبه.
 */
export function itemKey(name: string): string {
  return name
    .replace(/[\u064B-\u0652\u0640]/g, "")
    .replace(/[أإآ]/g, "ا")
    .replace(/ى/g, "ي")
    .replace(/ة/g, "ه")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

export function storeArrivalDescription(storeName: string, category: StoreCategory): string {
  return `وصول لـ«${storeName}» (${category})`;
}

/**
 * هل الرسالة دي لازم تتمنع؟ `recent` = مهام store_arrival آخر ٢٤ ساعة للعميل ده.
 * اسم المحل بيتقرا من الوصف اللي `storeArrivalDescription` كتبته.
 */
export function storeArrivalBlock(
  recent: Array<{ created_at: string; task_description: string }>,
  storeName: string,
  nowMs: number,
): "same_store_recently" | "daily_cap" | null {
  const dayAgo = nowMs - 24 * 3_600_000;
  const inDay = recent.filter((r) => Date.parse(r.created_at) >= dayAgo);
  const wanted = itemKey(storeName);
  const windowStart = nowMs - STORE_ARRIVAL_SAME_STORE_HOURS * 3_600_000;
  const sameStore = inDay.some((r) => {
    const m = /«([^»]*)»/.exec(r.task_description ?? "");
    return m !== null && itemKey(m[1]) === wanted && Date.parse(r.created_at) >= windowStart;
  });
  if (sameStore) return "same_store_recently";
  if (inDay.length >= STORE_ARRIVAL_DAILY_CAP) return "daily_cap";
  return null;
}

/**
 * نص الرسالة. الترتيب مقصود: قايمة الشراء الأول (العميل كتبها بنفسه)، بعدين المخزون الناقص،
 * بعدين تلميح الجهاز. `null` = مفيش حاجة ناقصة — ساعتها مابنبعتش خالص بدل رسالة فاضية.
 */
export function buildStoreArrivalMessage(input: {
  storeName: string;
  category: StoreCategory;
  shopping: string[];
  lowStock: string[];
  clientHints: string[];
}): { title: string; body: string; itemCount: number } | null {
  const seen = new Set<string>();
  const items: string[] = [];
  for (const raw of [...input.shopping, ...input.lowStock, ...input.clientHints]) {
    const name = cleanText(raw, 60);
    const key = itemKey(name);
    if (!key || seen.has(key)) continue;
    seen.add(key);
    items.push(name);
  }
  if (items.length === 0) return null;

  const store = sanitizeStoreName(input.storeName) || "محل قريب";
  const title = input.category === "pharmacy"
    ? `💊 أنت جنب «${store}»`
    : input.category === "mall"
      ? `🛍️ أنت في «${store}»`
      : `🛒 أنت جنب «${store}»`;
  const intro = input.category === "pharmacy" ? "أدوية قربت تخلص عندك:" : "ناقص في البيت، لو هتشتري:";
  const listed = items.slice(0, STORE_ARRIVAL_MAX_LISTED).map((i) => `• ${i}`);
  const more = items.length - listed.length;
  const body = [intro, ...listed, ...(more > 0 ? [`… و${more} كمان`] : [])].join("\n");
  return { title, body, itemCount: items.length };
}

/**
 * بيحوّل رد `agent_proactive_scan()` لرد الإندبوينت. `ok` = مفيش ولا فشل.
 *
 * `null`/شكل مش متوقع (الدالة لسه `void` قبل ما ميجريشن 20260913161000 توصل) بيتعامل
 * كصفر فشل — نفس سلوك الإندبوينت القديم بالظبط، عشان ترتيب النشر (فانكشن قبل ميجريشن
 * أو العكس) مايكسرش حاجة. أي رقم فشل موجب لازم يطلع `ok: false`.
 */
export function summarizeProactiveScan(raw: unknown): {
  ok: boolean;
  scanned: number;
  failed: number;
  failed_users: number;
  skipped_orphans: number;
  suppressed: number;
  errors: unknown[];
} {
  const r = (raw && typeof raw === "object" ? raw : {}) as Record<string, unknown>;
  const num = (v: unknown) => (typeof v === "number" && Number.isFinite(v) ? v : 0);
  const failed = num(r.failed);
  return {
    ok: failed <= 0,
    scanned: num(r.scanned),
    failed,
    failed_users: num(r.failed_users),
    skipped_orphans: num(r.skipped_orphans),
    // مراحل الماسح اتخطّت لأن العميل رفض النوع ده (20260913190000) — مش فشل، بس لازم يبان.
    suppressed: num(r.suppressed),
    errors: Array.isArray(r.errors) ? r.errors : [],
  };
}

/**
 * Task 16.3: on exhausted retries, what to do. `chat` never queues silently — the user
 * is waiting right now, so it gets an honest message instead of a generic {queued:true}
 * with no reply. Every other trigger (daily/event) queues for the drain cron.
 */
export function decideOnBrainFailure(trigger: string): { shouldQueue: boolean; status: number; body: Record<string, unknown> } {
  if (trigger === "chat") {
    return { shouldQueue: false, status: 200, body: { message: "زاد مش قادر يفكر دلوقتي، جرب بعد شوية" } };
  }
  return { shouldQueue: true, status: 200, body: { queued: true } };
}

/**
 * مواعيد الجرعات — التطبيع الحتمي.
 *
 * الأداة كانت بتقول للموديل حرفياً "احسب dose_times من **الوقت الحالي** والفاصل اللي
 * قاله العميل"، والبرومبت مش بيدّي الموديل الوقت الحالي ولا المنطقة الزمنية أصلاً. يعني
 * كان بيخترع نقطة بداية. النتيجة في بيانات الإنتاج (2026-08-16): "سبروفار" مواعيده
 * `02:00,14:00` و"اجمانتين" مواعيده `01:30,13:30` — منبه دوا بيرن الساعة ١:٣٠ بالليل كل
 * يوم. دي مش غلطة تجميلية: المريض بيتصحّى، وغالباً بيقفل المنبه ويرجع ينام، فالجرعة
 * بتتفوّت والالتزام بالعلاج بيقل.
 *
 * التصليح مش برومبت بس — البرومبت اتظبط كمان، بس الحارس هنا حتمي:
 *
 * - أي `HH:MM` مش صالح بيتشال بدل ما يوصل للجدول ويفضل مكسور.
 * - لو مفيش أي ميعاد صالح، بنشتق المواعيد من `daily_dose_count` بمراسي قياسية.
 * - لو أي ميعاد وقع في نافذة النوم (00:00–05:59) **والعميل مانطقش ساعة بعينها**
 *   (`times_explicit` مش true)، بنستبدل الجدول كله بالمرساة القياسية لنفس عدد الجرعات.
 *   الشرط ده مقصود: مريض قال "الساعة ٢ بالليل" فعلاً له الحق ياخد ٢ بالليل.
 */
const DOSE_TIME_ANCHORS: Record<number, string[]> = {
  1: ["09:00"],
  2: ["09:00", "21:00"],
  3: ["08:00", "14:00", "20:00"],
  4: ["08:00", "13:00", "18:00", "23:00"],
  5: ["07:00", "11:00", "15:00", "19:00", "23:00"],
  6: ["06:00", "10:00", "14:00", "18:00", "22:00", "23:59"],
};
const NIGHT_WINDOW_END_MINUTES = 6 * 60; // 06:00

export function normalizeDoseTimes(
  raw: unknown,
  dailyDoseCount?: unknown,
  timesExplicit?: unknown,
): string | null {
  const parsed: string[] = [];
  if (typeof raw === "string") {
    for (const chunk of raw.split(",")) {
      const m = /^\s*(\d{1,2}):(\d{2})\s*$/.exec(chunk);
      if (!m) continue;
      const h = Number(m[1]);
      const min = Number(m[2]);
      // 24:00 is not a clock time; the schema already bans it, but a model that emits it
      // anyway must not land a value LocalTime.parse() will reject on the phone.
      if (!Number.isInteger(h) || !Number.isInteger(min) || h > 23 || min > 59) continue;
      const norm = `${String(h).padStart(2, "0")}:${String(min).padStart(2, "0")}`;
      if (!parsed.includes(norm)) parsed.push(norm);
    }
  }

  const count = Number(dailyDoseCount);
  const wanted = Number.isFinite(count) && count >= 1 && count <= 6
    ? Math.trunc(count)
    : (parsed.length >= 1 ? parsed.length : 0);

  if (parsed.length === 0) {
    if (wanted === 0) return null;
    return (DOSE_TIME_ANCHORS[wanted] ?? DOSE_TIME_ANCHORS[1]).join(",");
  }

  if (timesExplicit === true) return parsed.sort().join(",");

  const hitsNightWindow = parsed.some((t) => {
    const [h, m] = t.split(":").map(Number);
    return h * 60 + m < NIGHT_WINDOW_END_MINUTES;
  });
  if (!hitsNightWindow) return parsed.sort().join(",");

  const anchor = DOSE_TIME_ANCHORS[parsed.length] ?? DOSE_TIME_ANCHORS[wanted] ?? DOSE_TIME_ANCHORS[1];
  return anchor.join(",");
}

/** نافذة "نفس الدفعة" بين إشعارين — لازم تفضل مطابقة لـ interval '15 minutes' في
 *  private.zad_resolve_transaction_proposal_impl (20260913213000). */
export const DUPLICATE_PROPOSAL_WINDOW_MS = 15 * 60 * 1000;

/**
 * من اقتراحات نفس العميل بنفس المبلغ خلال النافذة، أنهي واحد الإشعار الجديد مكرر منه؟
 *
 * البنك بيقول "تم خصم 500 من بطاقتك" وInstaPay بيقول "تم دفع فاتورة الإنترنت 500" —
 * مافيش بينهم ولا كلمة مشتركة، فالمبلغ والوقت هما الإشارة الوحيدة المتاحة. ده سؤال
 * للعميل مش حكم: الاقتراح الجديد بيتعمل عادي وبيتعلّم بس، والتطبيق وتيليجرام بيسألوا
 * "هل دي نفس المعاملة؟".
 *
 * - اتجاه معروف ومختلف (خصم 500 وإيداع 500) = مش نفس الدفعة.
 * - اتجاه مش معروف في أي طرف = ممكن، يتسأل.
 * - الأقدم هو الأصل، مش الأحدث: لو تلات إشعارات لنفس الدفعة، التاني والتالت يشاوروا
 *   على الأول، مش سلسلة (تالت ← تاني ← أول) تتقطع لو التاني اترفض.
 */
export function pickDuplicateProposalSibling(
  siblings: Array<{ id: string; status: string; txn_kind: string | null; transaction_id: string | null; created_at: string }>,
  txnKind: string | null,
): { id: string } | null {
  const candidates = siblings
    .filter((s) =>
      ["awaiting_confirmation", "needs_classification"].includes(s.status) ||
      // متقيد والعميل مسح معاملته بعدين = مابقاش بيعدّ في الرصيد، مش أصل تكرار.
      (s.status === "posted" && s.transaction_id != null)
    )
    .filter((s) => s.txn_kind == null || txnKind == null || s.txn_kind === txnKind)
    .sort((a, b) => Date.parse(a.created_at) - Date.parse(b.created_at));
  return candidates.length > 0 ? { id: candidates[0].id } : null;
}
