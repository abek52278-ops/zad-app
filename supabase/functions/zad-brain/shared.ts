// Small pure decision functions extracted out of the Deno.serve handler purely so
// Task 16.4's tests #11/#12 don't need a live server or a live database to verify.

/** Task 16.3 partial-run idempotency: skip a new daily run if one in the window already mutated data. */
export function hasRecentMutatingRun(runs: Array<{ mutations: unknown }>): boolean {
  return runs.some((r) => Array.isArray(r.mutations) && r.mutations.length > 0);
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
