// Crowdsource Tools — Phase 3
// Helper functions for managing crowdsourced price data
// API endpoint: POST /submit_price (called from Android app or web UI)

import { createClient } from "jsr:@supabase/supabase-js@2";

interface CrowdsourcePrice {
  item_name: string;
  item_category: string;
  price: number;
  location: string;
  store_name?: string;
}

interface LeaderboardEntry {
  user_id: string;
  user_name: string;
  contribution_count: number;
  last_contribution: string;
  score: number;
}

export async function submitCrowdsourcePrice(
  sb: any,
  userId: string,
  priceData: CrowdsourcePrice
): Promise<{ success: boolean; message: string }> {
  try {
    const { error } = await sb.from("price_index").insert({
      item_name: priceData.item_name,
      item_category: priceData.item_category,
      price: priceData.price,
      currency: "EGP",
      user_id: userId,
      location: priceData.location,
      source: "crowdsource",
      timestamp: new Date().toISOString(),
      ...(priceData.store_name && { merchant_name: priceData.store_name }),
    });

    if (error) {
      return { success: false, message: `Error: ${error.message}` };
    }

    return { success: true, message: "Price submitted successfully" };
  } catch (err) {
    return {
      success: false,
      message: err instanceof Error ? err.message : "Unknown error",
    };
  }
}

export async function getLeaderboard(
  sb: any,
  limit: number = 10
): Promise<LeaderboardEntry[]> {
  try {
    // Get top contributors
    const { data: contributions, error } = await sb
      .from("price_index")
      .select("user_id, count(*) as contribution_count, max(timestamp) as last_contribution")
      .eq("source", "crowdsource")
      .groupBy("user_id")
      .order("contribution_count", { ascending: false })
      .limit(limit);

    if (error || !contributions) {
      return [];
    }

    // Fetch user names
    const leaderboard = await Promise.all(
      (contributions as any[]).map(async (entry, index) => {
        const { data: userData } = await sb
          .auth.admin.getUserById(entry.user_id);

        return {
          user_id: entry.user_id,
          user_name: userData?.user?.user_metadata?.name || `Contributor ${index + 1}`,
          contribution_count: entry.contribution_count || 0,
          last_contribution: entry.last_contribution,
          score: (limit - index) * 10, // 100, 90, 80, ...
        };
      })
    );

    return leaderboard;
  } catch (err) {
    console.error("Error fetching leaderboard:", err);
    return [];
  }
}

export async function getUserContributions(
  sb: any,
  userId: string
): Promise<number> {
  try {
    const { data, error } = await sb
      .from("price_index")
      .select("id", { count: "exact" })
      .eq("user_id", userId)
      .eq("source", "crowdsource");

    return data?.length || 0;
  } catch (err) {
    console.error("Error fetching user contributions:", err);
    return 0;
  }
}

export async function getRecentContributions(
  sb: any,
  limit: number = 20
): Promise<CrowdsourcePrice[]> {
  try {
    const { data, error } = await sb
      .from("price_index")
      .select(
        "item_name, item_category, price, location, merchant_name, timestamp"
      )
      .eq("source", "crowdsource")
      .order("timestamp", { ascending: false })
      .limit(limit);

    if (error || !data) return [];

    return (data as any[]).map((row) => ({
      item_name: row.item_name,
      item_category: row.item_category,
      price: row.price,
      location: row.location,
      store_name: row.merchant_name,
    }));
  } catch (err) {
    console.error("Error fetching recent contributions:", err);
    return [];
  }
}
