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
  };
  return { title: titles[k] ?? "💡 زاد لاحظ حاجة تهمّك", proactive: true };
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
