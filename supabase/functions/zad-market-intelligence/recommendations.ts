// Smart Recommendations Engine — Phase 4
// استخدم Gemini + market data لتقديم توصيات شراء ذكية

import { createClient } from "jsr:@supabase/supabase-js@2";

interface ShoppingContext {
  userId: string;
  familyBudget: number;
  currentSpending: number;
  averageMonthlyExpense: number;
  preferences: string[]; // dietary preferences, allergies, etc
  locationLat: number;
  locationLng: number;
}

interface MarketContext {
  currentPrices: Array<{ item: string; price: number; trend: string }>;
  weatherForecast: string;
  inflationOutlook: number;
  nearbyStores: Array<{ name: string; distance: number }>;
}

interface ShoppingRecommendation {
  userId: string;
  itemName: string;
  recommendation: string; // "buy_now", "wait", "bulk_buy", "avoid", "substitute"
  reasoning: string; // Arabic explanation
  estimatedSavings: number;
  urgency: "low" | "medium" | "high";
  bestStore?: string;
  bestPrice?: number;
}

export async function generateSmartRecommendations(
  sb: any,
  userId: string,
  shoppingContext: ShoppingContext,
  marketContext: MarketContext
): Promise<ShoppingRecommendation[]> {
  try {
    const GEMINI_KEY = Deno.env.get("ZAD_API_KEY_1") || Deno.env.get("GEMINI_API_KEY");
    if (!GEMINI_KEY) {
      console.warn("[Recommendations] No Gemini key available");
      return [];
    }

    const prompt = `You are a smart shopping advisor for an Egyptian family. Analyze this shopping context and provide 3-5 specific, actionable recommendations.

Family Budget: ${shoppingContext.familyBudget} EGP/month
Current Spending: ${shoppingContext.currentSpending} EGP
Average Monthly: ${shoppingContext.averageMonthlyExpense} EGP
Preferences: ${shoppingContext.preferences.join(", ") || "none"}
Location: ${shoppingContext.locationLat}, ${shoppingContext.locationLng}

Current Market Data:
- Prices: ${marketContext.currentPrices.map((p) => `${p.item}: ${p.price} EGP (${p.trend})`).join(", ")}
- Weather: ${marketContext.weatherForecast}
- Inflation Outlook: ${marketContext.inflationOutlook}%
- Nearby Stores: ${marketContext.nearbyStores.map((s) => `${s.name} (${s.distance}km)`).join(", ")}

Respond with ONLY valid JSON (no markdown, no code blocks):
[
  {
    "itemName": "item name",
    "recommendation": "<buy_now|wait|bulk_buy|avoid|substitute>",
    "reasoning": "<brief Arabic explanation>",
    "estimatedSavings": <number>,
    "urgency": "<low|medium|high>",
    "bestStore": "store name (optional)",
    "bestPrice": <number (optional)>
  }
]`;

    const response = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" +
        GEMINI_KEY,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          contents: [
            {
              parts: [{ text: prompt }],
            },
          ],
          generationConfig: {
            maxOutputTokens: 512,
            temperature: 0.3,
          },
        }),
      }
    );

    if (!response.ok) {
      console.warn(`[Recommendations] Gemini API error: ${response.status}`);
      return [];
    }

    const result = await response.json();
    const textContent = result.candidates?.[0]?.content?.parts?.[0]?.text;

    if (!textContent) {
      console.warn("[Recommendations] No text in Gemini response");
      return [];
    }

    // Parse JSON response (handle potential markdown wrapping)
    const jsonMatch = textContent.match(/\[[\s\S]*\]/);
    const jsonStr = jsonMatch ? jsonMatch[0] : textContent;
    const recommendations = JSON.parse(jsonStr);

    // Convert to ShoppingRecommendation objects
    return (recommendations as any[]).map((rec) => ({
      userId,
      itemName: rec.itemName || "",
      recommendation: rec.recommendation || "wait",
      reasoning: rec.reasoning || "No reason provided",
      estimatedSavings: rec.estimatedSavings || 0,
      urgency: rec.urgency || "medium",
      bestStore: rec.bestStore,
      bestPrice: rec.bestPrice,
    }));
  } catch (err) {
    console.error("[Recommendations] Generation failed:", err);
    return [];
  }
}

export async function storeRecommendations(
  sb: any,
  recommendations: ShoppingRecommendation[]
): Promise<boolean> {
  try {
    if (recommendations.length === 0) return true;

    const insertData = recommendations.map((rec) => ({
      user_id: rec.userId,
      item_name: rec.itemName,
      recommendation_type: rec.recommendation,
      reasoning: rec.reasoning,
      estimated_savings: rec.estimatedSavings,
      urgency: rec.urgency,
      best_store: rec.bestStore,
      best_price: rec.bestPrice,
      created_at: new Date().toISOString(),
    }));

    const { error } = await sb
      .from("shopping_recommendations")
      .insert(insertData);

    if (error) {
      console.error("[Recommendations] DB error:", error.message);
      return false;
    }

    console.log(
      `[Recommendations] Stored ${recommendations.length} recommendations`
    );
    return true;
  } catch (err) {
    console.error("[Recommendations] Storage failed:", err);
    return false;
  }
}

export async function getUserRecommendations(
  sb: any,
  userId: string,
  limit: number = 10
): Promise<ShoppingRecommendation[]> {
  try {
    const { data, error } = await sb
      .from("shopping_recommendations")
      .select("*")
      .eq("user_id", userId)
      .order("created_at", { ascending: false })
      .limit(limit);

    if (error || !data) return [];

    return (data as any[]).map((row) => ({
      userId: row.user_id,
      itemName: row.item_name,
      recommendation: row.recommendation_type,
      reasoning: row.reasoning,
      estimatedSavings: row.estimated_savings,
      urgency: row.urgency,
      bestStore: row.best_store,
      bestPrice: row.best_price,
    }));
  } catch (err) {
    console.error("[Recommendations] Fetch failed:", err);
    return [];
  }
}

export async function getRecommendationStats(
  sb: any,
  userId: string
): Promise<{
  totalRecommendations: number;
  totalSavingsPotential: number;
  actionedCount: number;
}> {
  try {
    const { data, error } = await sb
      .from("shopping_recommendations")
      .select("estimated_savings, acted_on_at")
      .eq("user_id", userId)
      .order("created_at", { ascending: false })
      .limit(100);

    if (error || !data) {
      return {
        totalRecommendations: 0,
        totalSavingsPotential: 0,
        actionedCount: 0,
      };
    }

    const totalSavings = (data as any[]).reduce(
      (sum, rec) => sum + (rec.estimated_savings || 0),
      0
    );
    const acted = (data as any[]).filter((rec) => rec.acted_on_at).length;

    return {
      totalRecommendations: data.length,
      totalSavingsPotential: totalSavings,
      actionedCount: acted,
    };
  } catch (err) {
    console.error("[Recommendations] Stats fetch failed:", err);
    return {
      totalRecommendations: 0,
      totalSavingsPotential: 0,
      actionedCount: 0,
    };
  }
}
