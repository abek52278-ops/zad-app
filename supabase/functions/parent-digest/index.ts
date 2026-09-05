// parent-digest — تقرير أسبوعي للوالدين عن الأبناء (الشبكة العصبية الأبوية)
//
// بيشتغل cron أو يدوياً. لكل عائلة:
// 1. يجيب الأبناء (role=child) والوالدين (role=admin)
// 2. يحسب صرف كل ابن آخر 7 أيام مقابل الأسبوع اللي فات
// 3. يبني تقرير مجمع (مش تفصيل معاملات) + يخزنه في zad_parent_digests
// 4. يبعت إشعار تليجرام/insight للأب: «فلان صرف ٣٠٪ أكتر» أو «التزم تماماً»
//
// الخصوصية بالتصميم: مجمعات فقط، مفيش وصف معاملات فردية.

import { createClient } from "jsr:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

// تصحيح 2026-09-01 (بند 34.3) — كانت بتتحقق من auth.includes(SERVICE_ROLE)، يعني محتاجة
// JWT حقيقي في الهيدر. الكرون بتاعها (pg_cron's net.http_post) مبيحملش JWT سوبابيز أصلاً،
// وكانت بتحاول current_setting('app.settings.jwt_secret') — GUC مش متظبط على المشروع ده،
// فكل تشغيلة كانت بترجع 401 عند بوابة verify_jwt قبل ما توصل هنا خالص (zad_parent_digests
// فاضي من يوم ما الجدول اتعمل). نفس نمط CHECKIN_CRON_SECRET في zad-telegram-bot بالظبط:
// سيكريت مخصص plain literal (مش project secret — مفيش أداة هنا تضيف سيكريت مشروع عن بعد)،
// blast radius صغير (بيشغّل التقرير بس، مفيش وصول بيانات خاص بيه)، وconfig.toml اتظبط
// verify_jwt=false ليها عشان الهيدر ده يبقى كفاية.
// Project secret since 2026-09-05 (بند BE-03). The cron job that calls this reads the
// same value out of Supabase Vault — see migration 20260905150000 — so the literal that
// used to live here is inert and is gone.
//
// An unset secret rejects and logs rather than failing open. This endpoint runs with
// verify_jwt = false, so this check is the only thing standing between it and the
// internet.
function digestSecretMatches(received: string | null): boolean {
  if (!received) return false;
  const expected = Deno.env.get("ZAD_PARENT_DIGEST_CRON_SECRET");
  if (!expected) {
    console.error("[auth] ZAD_PARENT_DIGEST_CRON_SECRET is not set on this project — rejecting.");
    return false;
  }
  return received === expected;
}

const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, content-type, x-parent-digest-cron-secret",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: CORS });

  if (!digestSecretMatches(req.headers.get("X-Parent-Digest-Cron-Secret"))) {
    return new Response(JSON.stringify({ error: "unauthorized" }), {
      status: 401, headers: { ...CORS, "content-type": "application/json" },
    });
  }

  const sb = createClient(SUPABASE_URL, SERVICE_ROLE);
  const weekEnd = new Date();
  const weekStart = new Date(weekEnd.getTime() - 7 * 86400000);
  const prevStart = new Date(weekStart.getTime() - 7 * 86400000);
  const ws = weekStart.toISOString().slice(0, 10);

  try {
    // كل الأسر
    const { data: families } = await sb.from("family_members")
      .select("family_id").not("family_id", "is", null);

    let digests = 0;
    for (const { family_id } of families ?? []) {
      const { data: members } = await sb.from("family_members")
        .select("user_id, role, alias, daily_limit").eq("family_id", family_id);
      if (!members) continue;

      const children = members.filter((m: any) => m.role === "child");
      const parents = members.filter((m: any) => m.role !== "child");
      if (children.length === 0 || parents.length === 0) continue;

      for (const child of children) {
        // صرف الأسبوعين
        const { data: tx } = await sb.from("zad_transactions")
          .select("amount, category, created_at")
          .eq("user_id", child.user_id)
          .eq("is_expense", true)
          .gte("created_at", prevStart.toISOString());

        const inWeek = (tx ?? []).filter((t: any) => t.created_at >= weekStart.toISOString());
        const total = inWeek.reduce((s: number, t: any) => s + Number(t.amount), 0);

        // تجميع بالفئة
        const byCat: Record<string, number> = {};
        for (const t of inWeek) {
          const c = t.category ?? "أخرى";
          byCat[c] = (byCat[c] ?? 0) + Number(t.amount);
        }
        const topCat = Object.entries(byCat).sort((a, b) => b[1] - a[1])[0]?.[0] ?? null;

        // مقارنة بالأسبوع الفايت
        const prevTotal = (tx ?? [])
          .filter((t: any) => t.created_at < weekStart.toISOString())
          .reduce((s: number, t: any) => s + Number(t.amount), 0);
        const deltaPct = prevTotal > 0 ? Math.round(((total - prevTotal) / prevTotal) * 100) : null;

        // تجاوز الحد اليومي (لو محدد)
        let exceeded = 0;
        if (child.daily_limit && child.daily_limit > 0) {
          const byDay: Record<string, number> = {};
          for (const t of inWeek) byDay[t.created_at.slice(0, 10)] = (byDay[t.created_at.slice(0, 10)] ?? 0) + Number(t.amount);
          exceeded = Object.values(byDay).filter((v) => v > child.daily_limit).length;
        }

        // ملخص العقل — جملة واحدة حسب الاتجاه
        let summary: string;
        if (prevTotal > 0 && deltaPct !== null && deltaPct > 25) {
          summary = `${child.alias} صرف ${deltaPct}٪ أكتر من الأسبوع اللي فات — أعلى فئة: ${topCat}. يستاهل كلمة`;
        } else if (deltaPct !== null && deltaPct < -15) {
          summary = `${child.alias} وفّر ${Math.abs(deltaPct)}٪ عن الأسبوع اللي فات — تشجّعه تستاهل`;
        } else if (exceeded > 0) {
          summary = `${child.alias} تعدى حد اليومي ${exceeded} مرات — راجعوا الحد سوا`;
        } else {
          summary = `${child.alias} على نفس وتيرته المعتادة — كل حاجة تحت السيطرة`;
        }

        // خزّن لكل والد (UNIQUE parent+child+week يمنع التكرار)
        for (const parent of parents) {
          const { error } = await sb.from("zad_parent_digests").upsert({
            family_id,
            parent_user_id: parent.user_id,
            child_user_id: child.user_id,
            child_alias: child.alias,
            week_start: ws,
            total_spent: total,
            tx_count: inWeek.length,
            top_category: topCat,
            delta_vs_prev_pct: deltaPct,
            daily_limit_exceeded: exceeded,
            summary_text: summary,
          }, { onConflict: "parent_user_id,child_user_id,week_start" });
          if (!error) digests++;

          // إشعار insight للأب (بيظهر في جرس الإشعارات وفي شات العقل)
          await sb.from("zad_insights").insert({
            user_id: parent.user_id,
            title: `📊 تقرير ${child.alias} الأسبوعي`,
            body: summary,
            surface: "bell",
          });
        }
      }
    }

    return new Response(JSON.stringify({ ok: true, digests }), {
      headers: { ...CORS, "content-type": "application/json" },
    });
  } catch (e) {
    return new Response(JSON.stringify({ ok: false, error: String(e) }), {
      status: 500, headers: { ...CORS, "content-type": "application/json" },
    });
  }
});
