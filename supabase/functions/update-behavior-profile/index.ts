// deno-lint-ignore-file
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.45.6";

const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const supabase = createClient(supabaseUrl, supabaseKey);

Deno.serve(async (req: Request) => {
  try {
    // Get all active users (those with transactions)
    const { data: users, error: userError } = await supabase
      .from("zad_transactions")
      .select("user_id")
      .gte("created_at", new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString())
      .limit(1000);

    if (userError) throw userError;
    const userIds = [...new Set((users || []).map(u => u.user_id).filter(Boolean))];

    console.log(`[BehaviorProfile] Processing ${userIds.length} users`);

    let successCount = 0;
    const errors: Array<{ user_id: string; message: string }> = [];

    for (const userId of userIds) {
      // Get last 90 days of transactions
      const { data: txns } = await supabase
        .from("zad_transactions")
        .select("*")
        .eq("user_id", userId)
        .gte("created_at", new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString())
        .limit(500);

      if (!txns?.length) continue;

      // Calculate avg_weekly_spending
      const totalSpent = txns.filter(t => t.is_expense).reduce((sum, t) => sum + (t.amount || 0), 0);
      const weeks = Math.max(1, 90 / 7);
      const avgWeeklySpending = totalSpent / weeks;

      // Calculate top_spending_categories
      const categoryTotals: Record<string, number> = {};
      txns.filter(t => t.is_expense).forEach(t => {
        const cat = t.category || "أخرى";
        categoryTotals[cat] = (categoryTotals[cat] || 0) + (t.amount || 0);
      });
      const topCategories = Object.entries(categoryTotals)
        .sort(([, a], [, b]) => b - a)
        .slice(0, 5)
        .map(([category, total]) => ({ category, total }));

      // Calculate spending_pattern_by_weekday
      const dowTotals: Record<number, { count: number; total: number }> = {};
      txns.filter(t => t.is_expense && t.created_at).forEach(t => {
        try {
          const day = new Date(t.created_at!).getDay();
          if (!dowTotals[day]) dowTotals[day] = { count: 0, total: 0 };
          dowTotals[day].count++;
          dowTotals[day].total += t.amount || 0;
        } catch { /* ignore parse errors */ }
      });
      const dayNames = ["الأحد", "الإثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت"];
      const weekdayPattern: Record<string, number> = {};
      Object.entries(dowTotals).forEach(([day, data]) => {
        weekdayPattern[dayNames[parseInt(day)]] = Math.round(data.total / Math.max(1, data.count));
      });

      // Get subscription load
      const { data: subs } = await supabase
        .from("zad_subscriptions")
        .select("amount")
        .eq("user_id", userId);
      const subMonthly = (subs || []).reduce((sum, s) => sum + (s.amount || 0), 0);

      // Upsert profile
      const { error: upsertError } = await supabase.from("user_behavior_profile").upsert({
        user_id: userId,
        avg_weekly_spending: Math.round(avgWeeklySpending * 100) / 100,
        top_spending_categories: topCategories,
        spending_pattern_by_weekday: weekdayPattern,
        subscription_load_monthly: subMonthly,
        last_updated_at: new Date().toISOString(),
      });

      if (upsertError) {
        console.error(`[BehaviorProfile] Upsert failed for ${userId}: ${upsertError.message}`);
        errors.push({ user_id: userId, message: upsertError.message });
        continue;
      }
      successCount++;
    }

    return new Response(JSON.stringify({ users_found: userIds.length, processed: successCount, errors }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    // See the note in amazon-creators-search: `unknown` catch binding, narrowed
    // with the same helper shape the CI-gated functions use.
    const msg = String((e as { message?: string })?.message ?? e);
    console.error(`[BehaviorProfile] Error: ${msg}`);
    return new Response(JSON.stringify({ error: msg }), { status: 500, headers: { "Content-Type": "application/json" } });
  }
});
